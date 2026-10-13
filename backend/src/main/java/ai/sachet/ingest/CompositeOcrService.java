package ai.sachet.ingest;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Composite OCR service that prioritizes local Tesseract when installed,
 * and falls back seamlessly to Gemini Multimodal OCR when Tesseract is absent
 * or when regional script processing requires high-accuracy multimodal recognition.
 */
@Service
@Primary
public class CompositeOcrService implements OcrService {

  private static final Logger log = LoggerFactory.getLogger(CompositeOcrService.class);

  private final TesseractOcrService tesseract;
  private final GeminiOcrService gemini;

  public CompositeOcrService(TesseractOcrService tesseract, GeminiOcrService gemini) {
    this.tesseract = tesseract;
    this.gemini = gemini;
  }

  @Override
  public boolean available() {
    return tesseract.available() || gemini.available();
  }

  @Override
  public List<String> ocrImages(List<Path> images, String language) throws IOException {
    if (images == null || images.isEmpty()) {
      return List.of();
    }

    if (tesseract.available()) {
      try {
        log.info("Executing OCR using local Tesseract engine for {} page(s)", images.size());
        return tesseract.ocrImages(images, language);
      } catch (Exception e) {
        log.warn("Tesseract OCR failed ({}); falling back to Gemini OCR", e.getMessage());
        if (gemini.available()) {
          log.info("Executing OCR using Gemini Multimodal OCR fallback for {} page(s)", images.size());
          return gemini.ocrImages(images, language);
        }
        throw e;
      }
    }

    if (gemini.available()) {
      log.info("Tesseract binary not installed; executing OCR using Gemini Multimodal OCR for {} page(s)", images.size());
      return gemini.ocrImages(images, language);
    }

    throw new IOException("No OCR service available: Tesseract binary is not installed and Gemini OCR is not configured.");
  }
}
