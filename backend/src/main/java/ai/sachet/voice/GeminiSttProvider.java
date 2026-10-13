package ai.sachet.voice;

import ai.sachet.config.SachetProperties;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Gemini Speech-to-Text provider.
 *
 * <p>Uses Gemini's multimodal audio comprehension (e.g. {@code gemini-1.5-flash} or
 * {@code gemini-2.0-flash}) to transcribe spoken voice recordings into native script.
 *
 * <p><b>Language coverage.</b> Gemini natively supports regional Indian languages
 * including Maithili (मैथिली), Punjabi (ਪੰਜਾਬੀ), Hindi (हिन्दी), Bengali, Tamil, Telugu,
 * Marathi, Gujarati, and English.
 *
 * <p><b>Privacy boundary.</b> User voice audio is sent to Gemini, so every call
 * is authenticated and gated by {@link ai.sachet.service.ModelPolicy}.
 */
@Component
public class GeminiSttProvider implements SttProvider {

  private static final Logger log = LoggerFactory.getLogger(GeminiSttProvider.class);

  /** Maximum audio file size (25 MB) to bound payload. */
  private static final int MAX_AUDIO_BYTES = 25 * 1024 * 1024;

  private static final List<String> SUPPORTED = List.of(
      "mai", "hi", "pa", "bn", "te", "mr", "ta", "gu", "kn", "ml",
      "ur", "or", "as", "ks", "kok", "en", "ne", "sd");

  private final SachetProperties props;
  private final Client client;

  public GeminiSttProvider(SachetProperties props) {
    this.props = props;
    String key = props.getGeminiApiKey();
    boolean selected = "gemini".equalsIgnoreCase(props.getSttProvider());
    if (selected && key != null && !key.isBlank()) {
      this.client = Client.builder().apiKey(key).build();
    } else {
      this.client = null;
    }
  }

  @Override
  public Status status() {
    if (client == null) {
      if (!"gemini".equalsIgnoreCase(props.getSttProvider())) {
        return Status.NOT_CONFIGURED;
      }
      return Status.MISSING_CREDENTIAL;
    }
    return Status.AVAILABLE;
  }

  @Override
  public String unavailableReason() {
    return switch (status()) {
      case AVAILABLE -> null;
      case NOT_CONFIGURED -> "No STT provider selected. Set SACHET_STT_PROVIDER=gemini.";
      case MISSING_CREDENTIAL -> "STT provider selected but SACHET_GEMINI_API_KEY is not set.";
      case ERROR -> "STT provider failed.";
    };
  }

  @Override
  public String modelId() {
    String configured = props.getSttModel();
    if (configured == null || configured.isBlank()) {
      configured = props.getGeminiModel();
    }
    return (configured == null || configured.isBlank() || "gemini-1.5-flash".equals(configured) || "gemini-2.0-flash".equals(configured))
        ? "gemini-3.8-flash"
        : configured;
  }

  @Override
  public List<String> supportedLanguages() {
    return SUPPORTED;
  }

  @Override
  public Transcription transcribe(byte[] audioBytes, String mimeType, String languageHint) {
    if (client == null) {
      throw new IllegalStateException(unavailableReason());
    }
    if (audioBytes == null || audioBytes.length == 0) {
      throw new IllegalArgumentException("Cannot transcribe empty audio payload.");
    }
    if (audioBytes.length > MAX_AUDIO_BYTES) {
      throw new IllegalArgumentException("Audio file exceeds maximum size limit (25 MB).");
    }

    String cleanMime = sanitizeMimeType(mimeType);
    String languageDesc = describeLanguage(languageHint);

    String prompt = "Transcribe the spoken speech in this audio recording.\n"
        + "The audio is spoken in " + languageDesc + ".\n\n"
        + "Rules:\n"
        + "1. Transcribe the spoken words accurately into their authentic native script (e.g. Devanagari for Maithili and Hindi, Gurmukhi for Punjabi, Bengali script for Bangla, Latin script for English).\n"
        + "2. Identify the language spoken and return a JSON object with 'text' and 'language' (BCP-47 code, e.g., mai-IN, pa-IN, hi-IN, en-IN, bn-IN).\n"
        + "3. Output ONLY valid JSON: {\"text\": \"...\", \"language\": \"...\"}";

    List<String> candidates = new java.util.ArrayList<>();
    String primary = modelId();
    if (primary != null && !primary.isBlank() && !"gemini-3.8-flash".equals(primary) && !"gemini-1.5-flash".equals(primary) && !"gemini-2.0-flash".equals(primary)) {
      candidates.add(primary);
    } else {
      candidates.add("gemini-3.5-flash");
    }
    if (!candidates.contains("gemini-3.5-flash")) candidates.add("gemini-3.5-flash");
    if (!candidates.contains("gemini-flash-lite-latest")) candidates.add("gemini-flash-lite-latest");
    if (!candidates.contains("gemini-3-flash-preview")) candidates.add("gemini-3-flash-preview");
    if (!candidates.contains("gemini-3.1-flash-lite")) candidates.add("gemini-3.1-flash-lite");

    RuntimeException lastException = null;

    for (String targetModel : candidates) {
      try {
        Part audioPart = Part.fromBytes(audioBytes, cleanMime);
        Part textPart = Part.fromText(prompt);

        GenerateContentConfig cfg = GenerateContentConfig.builder()
            .temperature(0.1f)
            .maxOutputTokens(300)
            .build();

        GenerateContentResponse res = client.models.generateContent(
            targetModel,
            com.google.genai.types.Content.fromParts(audioPart, textPart),
            cfg);

        String raw = res.text();
        if (raw == null) raw = "";
        raw = raw.trim();

        // Extract JSON if wrapped in markdown code fence
        if (raw.startsWith("```json")) raw = raw.substring(7);
        if (raw.startsWith("```")) raw = raw.substring(3);
        if (raw.endsWith("```")) raw = raw.substring(0, raw.length() - 3);
        raw = raw.trim();

        String parsedText = raw;
        String detectedLang = languageHint != null && !languageHint.equalsIgnoreCase("auto") && !languageHint.equalsIgnoreCase("Auto detect")
            ? languageHint : "en-IN";

        if (raw.startsWith("{") && raw.contains("\"text\"")) {
          try {
            com.fasterxml.jackson.databind.JsonNode node =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(raw);
            if (node.has("text") && !node.get("text").asText().isBlank()) {
              parsedText = node.get("text").asText().trim();
            }
            if (node.has("language") && !node.get("language").asText().isBlank()) {
              detectedLang = node.get("language").asText().trim();
            }
          } catch (Exception ignore) {
            // Keep raw as text
          }
        } else {
          // If plain text was returned, strip surrounding quotes
          if (parsedText.startsWith("\"") && parsedText.endsWith("\"") && parsedText.length() >= 2) {
            parsedText = parsedText.substring(1, parsedText.length() - 1).trim();
          }
          // Script-based heuristic language detection for regional Indian languages
          detectedLang = detectLanguageFromScript(parsedText, languageHint);
        }

        return new Transcription(parsedText, detectedLang);
      } catch (IllegalStateException | IllegalArgumentException e) {
        throw e;
      } catch (RuntimeException e) {
        log.warn("Gemini STT with model {} failed ({}). Attempting fallback if available.",
            targetModel, e.getMessage());
        lastException = e;
      }
    }

    log.error("All Gemini STT candidate models exhausted. Last error: {}",
        lastException != null ? lastException.getMessage() : "unknown");
    throw new IllegalStateException("Voice speech-to-text transcription failed: "
        + (lastException != null ? lastException.getMessage() : "All models failed"));
  }

  static String detectLanguageFromScript(String text, String hint) {
    if (hint != null && !hint.isBlank() && !hint.equalsIgnoreCase("auto") && !hint.equalsIgnoreCase("Auto detect")) {
      return hint;
    }
    if (text == null || text.isBlank()) return "en-IN";
    // Check unicode script blocks
    for (char c : text.toCharArray()) {
      Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
      if (block == Character.UnicodeBlock.GURMUKHI) return "pa-IN"; // Punjabi
      if (block == Character.UnicodeBlock.BENGALI) return "bn-IN"; // Bengali/Assamese
      if (block == Character.UnicodeBlock.TAMIL) return "ta-IN";
      if (block == Character.UnicodeBlock.TELUGU) return "te-IN";
      if (block == Character.UnicodeBlock.GUJARATI) return "gu-IN";
      if (block == Character.UnicodeBlock.KANNADA) return "kn-IN";
      if (block == Character.UnicodeBlock.MALAYALAM) return "ml-IN";
      if (block == Character.UnicodeBlock.DEVANAGARI) {
        // Devanagari is used for Maithili, Hindi, Marathi, etc.
        return "mai-IN"; // Default regional focus
      }
    }
    return "en-IN";
  }

  static String sanitizeMimeType(String mimeType) {
    if (mimeType == null || mimeType.isBlank()) {
      return "audio/webm";
    }
    // Strip codec parameters like "audio/webm;codecs=opus" -> "audio/webm"
    String base = mimeType.split(";")[0].trim().toLowerCase(Locale.ROOT);
    if (base.equals("audio/webm") || base.equals("audio/wav") || base.equals("audio/mp3")
        || base.equals("audio/ogg") || base.equals("audio/mp4") || base.equals("audio/x-m4a")) {
      return base;
    }
    return "audio/webm";
  }

  static String describeLanguage(String tag) {
    if (tag == null || tag.isBlank() || tag.equalsIgnoreCase("Auto detect")) {
      return "an Indian regional language (such as Maithili, Punjabi, Hindi, Bengali) or English";
    }
    String base = tag.split("-")[0].toLowerCase(Locale.ROOT);
    return switch (base) {
      case "mai" -> "Maithili (मैथिली)";
      case "pa" -> "Punjabi (ਪੰਜਾਬੀ)";
      case "hi" -> "Hindi (हिन्दी)";
      case "bn" -> "Bengali (বাংলা)";
      case "te" -> "Telugu (తెలుగు)";
      case "mr" -> "Marathi (मराठी)";
      case "ta" -> "Tamil (தமிழ்)";
      case "gu" -> "Gujarati (ગુજરાતી)";
      case "kn" -> "Kannada (ಕನ್ನಡ)";
      case "ml" -> "Malayalam (മലയാളം)";
      case "ur" -> "Urdu (اردو)";
      case "or" -> "Odia (ଓଡ଼ିଆ)";
      case "as" -> "Assamese (অসমীয়া)";
      case "ks" -> "Kashmiri (कॉशुर)";
      case "kok" -> "Konkani (कोंकणी)";
      case "en" -> "English";
      default -> tag;
    };
  }
}
