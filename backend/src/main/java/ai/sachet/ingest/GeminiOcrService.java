package ai.sachet.ingest;

import ai.sachet.config.SachetProperties;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * High-accuracy Multimodal OCR using Google Gemini.
 * <p>
 * Invoked when local Tesseract is absent or fails. Operates on rendered page images
 * and preserves verbatim layout, tables, regional scripts (Maithili, Hindi, Punjabi, Bengali, etc.),
 * and numerical data.
 * <p>
 * Respects SACHET security constitution: only active when external models are permitted
 * and SACHET_GEMINI_API_KEY is configured.
 */
@Service
public class GeminiOcrService implements OcrService {

  private static final Logger log = LoggerFactory.getLogger(GeminiOcrService.class);

  private static final String OCR_PROMPT =
      "Perform high-precision OCR on this document page image.\n"
          + "Extract ALL text, headings, numbers, tables, bullet points, charts, footnotes, and metadata verbatim.\n"
          + "Preserve structure, tabular relationships, and original languages/scripts (including Indian regional scripts such as Devanagari/Maithili/Hindi, Gurmukhi, Bengali, etc., as well as English).\n"
          + "Do NOT summarize, edit, translate, or omit any text. Return ONLY the verbatim extracted textual content.";

  private final SachetProperties props;
  private final Client geminiClient;

  public GeminiOcrService(SachetProperties props) {
    this.props = props;
    String apiKey = props.getGeminiApiKey();
    if (apiKey != null && !apiKey.isBlank()) {
      this.geminiClient = Client.builder().apiKey(apiKey).build();
    } else {
      this.geminiClient = null;
    }
  }

  @Override
  public boolean available() {
    return geminiClient != null
        && props.getGeminiApiKey() != null
        && !props.getGeminiApiKey().isBlank()
        && (!props.isSensitiveMode() || props.isAllowExternalModels());
  }

  @Override
  public List<String> ocrImages(List<Path> images, String language) throws IOException {
    if (!available()) {
      throw new IOException("Gemini OCR is unavailable: API key not set or external transfer forbidden by policy.");
    }
    if (images == null || images.isEmpty()) {
      return List.of();
    }

    log.info("Starting Gemini Multimodal OCR for {} page image(s)", images.size());

    // Use bounded 2-thread pool to respect free tier RPM limits while ensuring low total latency
    int poolSize = Math.min(Math.max(1, images.size()), 2);
    ExecutorService executor = Executors.newFixedThreadPool(poolSize);

    List<CompletableFuture<String>> futures = new ArrayList<>(images.size());
    for (int i = 0; i < images.size(); i++) {
      final int pageIdx = i;
      final Path imagePath = images.get(i);
      futures.add(CompletableFuture.supplyAsync(() -> {
        // Stagger requests slightly to prevent bursting into rate limit
        if (pageIdx > 0) {
          try { Thread.sleep(400); } catch (InterruptedException ignored) {}
        }
        return ocrSingleImage(imagePath, pageIdx + 1);
      }, executor));
    }

    try {
      CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
      List<String> results = new ArrayList<>(images.size());
      for (CompletableFuture<String> future : futures) {
        results.add(future.get());
      }
      log.info("Gemini Multimodal OCR successfully processed {} page(s)", results.size());
      return results;
    } catch (Exception e) {
      log.error("Gemini Multimodal OCR batch processing error", e);
      throw new IOException("Gemini Multimodal OCR failed: " + e.getMessage(), e);
    } finally {
      executor.shutdown();
      try {
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
          executor.shutdownNow();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        executor.shutdownNow();
      }
    }
  }

  private String ocrSingleImage(Path imagePath, int pageNum) {
    try {
      byte[] imageBytes = Files.readAllBytes(imagePath);
      Part imagePart = Part.fromBytes(imageBytes, "image/png");
      Part promptPart = Part.fromText(OCR_PROMPT);

      GenerateContentConfig config = GenerateContentConfig.builder()
          .temperature(0.0f)
          .maxOutputTokens(8192)
          .build();

      List<String> candidates = getCandidateModels();
      Exception lastException = null;

      for (String targetModel : candidates) {
        try {
          GenerateContentResponse res = geminiClient.models.generateContent(
              targetModel,
              Content.fromParts(imagePart, promptPart),
              config);

          String extracted = res.text();
          if (extracted != null && !extracted.isBlank()) {
            log.info("Gemini OCR completed for page {} using model {} (chars={})",
                pageNum, targetModel, extracted.length());
            return extracted.strip();
          }
        } catch (Exception e) {
          log.warn("Gemini OCR for page {} with model {} failed ({}). Trying next candidate.",
              pageNum, targetModel, e.getMessage());
          lastException = e;
          // Short backoff before next candidate if rate limited
          if (e.getMessage() != null && e.getMessage().contains("429")) {
            try { Thread.sleep(1200); } catch (InterruptedException ignored) {}
          }
        }
      }

      log.warn("Gemini OCR exhausted all models for page {}: {}",
          pageNum, lastException != null ? lastException.getMessage() : "empty response");
      return "";
    } catch (Exception e) {
      log.error("Failed to read image or execute OCR for page {}", pageNum, e);
      return "";
    }
  }

  private List<String> getCandidateModels() {
    List<String> list = new ArrayList<>();
    // Verified available & responsive models on Google Generative AI API
    list.add("gemini-flash-lite-latest");
    list.add("gemini-3.1-flash-lite");
    list.add("gemini-3.5-flash-lite");
    String configured = props.getGeminiModel();
    if (configured != null && !configured.isBlank() && !list.contains(configured)) {
      list.add(configured);
    }
    if (!list.contains("gemini-3.5-flash")) list.add("gemini-3.5-flash");
    return Collections.unmodifiableList(list);
  }
}
