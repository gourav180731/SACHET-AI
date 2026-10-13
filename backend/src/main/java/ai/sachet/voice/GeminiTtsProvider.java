package ai.sachet.voice;

import ai.sachet.config.SachetProperties;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.SpeechConfig;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Gemini text-to-speech.
 *
 * <p><b>Why TTS and not the Live API.</b> Gemini TTS is a plain
 * {@code generateContent} call (text in, audio out). The Live API is a
 * bidirectional WebSocket session intended for audio-in/audio-out dialogue and,
 * per Google's own documentation, does <i>not</i> accept documents. Since this
 * feature only needs to read an answer aloud, TTS is the correct, far simpler
 * product: no always-on microphone stream, no session lifecycle, and it works
 * with the google-genai SDK already in {@code pom.xml}.
 *
 * <p><b>Privacy boundary.</b> The synthesised text is the grounded answer, which
 * is derived from retrieved document excerpts. Sending it to this provider IS an
 * external transfer of document-derived content, so every call is gated by
 * {@code ModelPolicy} in {@code VoiceSpeakController}. Nothing is synthesised
 * speculatively.
 *
 * <p><b>Language coverage.</b> {@code gemini-3.8-flash-tts} documents Maithili
 * ({@code mai}) plus Bhojpuri, Magahi, Awadhi, Santali and 100+ others. The model
 * auto-detects input language, so the hint is advisory only.
 */
@Component
public class GeminiTtsProvider implements TtsProvider {

  private static final Logger log = LoggerFactory.getLogger(GeminiTtsProvider.class);

  /** Maximum characters sent in one request; bounds cost and latency. */
  private static final int MAX_CHARS = 4000;

  /** Mime type of the PCM/WAV payload the SDK returns for TTS models. */
  private static final String AUDIO_MIME = "audio/L16;rate=24000";

  /**
   * Languages this deployment claims. Subset of the model's documented coverage,
   * restricted to the ones the SACHET UI offers.
   */
  private static final List<String> SUPPORTED = List.of(
      "en", "hi", "mai", "bn", "te", "mr", "ta", "gu", "kn", "ml",
      "pa", "ur", "or", "as", "ks", "ne", "sd", "kok");

  private final SachetProperties props;
  private final Client client;

  public GeminiTtsProvider(SachetProperties props) {
    this.props = props;
    String key = props.getGeminiApiKey();
    boolean selected = "gemini".equalsIgnoreCase(props.getTtsProvider());
    if (selected && key != null && !key.isBlank()) {
      this.client = Client.builder().apiKey(key).build();
    } else {
      this.client = null;
    }
  }

  @Override
  public Status status() {
    if (client == null) {
      if (!"gemini".equalsIgnoreCase(props.getTtsProvider())) {
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
      case NOT_CONFIGURED -> "No TTS provider selected. Set SACHET_TTS_PROVIDER=gemini.";
      case MISSING_CREDENTIAL ->
          "TTS provider selected but SACHET_GEMINI_API_KEY is not set.";
      case ERROR -> "TTS provider failed.";
    };
  }

  @Override
  public String modelId() {
    String configured = props.getTtsModel();
    return (configured == null || configured.isBlank()) ? "gemini-3.8-flash-lite-tts" : configured;
  }

  @Override
  public List<String> supportedLanguages() {
    return SUPPORTED;
  }

  @Override
  public Audio synthesize(String text, String languageHint) {
    if (client == null) {
      throw new IllegalStateException(unavailableReason());
    }
    String trimmed = text == null ? "" : text.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException("Cannot synthesize empty text.");
    }
    if (trimmed.length() > MAX_CHARS) {
      trimmed = trimmed.substring(0, MAX_CHARS);
    }

    // The model auto-detects language, but an explicit BCP-47 tag avoids
    // mispronouncing short regional text. Gemini Flash Lite TTS natively supports Maithili, Punjabi, Bengali, etc.
    String languageCode = normalizeLanguage(languageHint);

    List<String> candidates = new java.util.ArrayList<>();
    String primary = modelId();
    if (primary != null && !primary.isBlank() && !candidates.contains(primary)) {
      candidates.add(primary);
    }
    if (!candidates.contains("gemini-3.8-flash-lite-tts")) candidates.add("gemini-3.8-flash-lite-tts");
    if (!candidates.contains("gemini-3.1-flash-tts-preview")) candidates.add("gemini-3.1-flash-tts-preview");
    if (!candidates.contains("gemini-2.5-flash-preview-tts")) candidates.add("gemini-2.5-flash-preview-tts");
    if (!candidates.contains("gemini-3.8-flash-tts")) candidates.add("gemini-3.8-flash-tts");

    RuntimeException lastException = null;

    for (String targetModel : candidates) {
      try {
        GenerateContentConfig.Builder cfg = GenerateContentConfig.builder()
            .responseModalities("AUDIO")
            .speechConfig(SpeechConfig.builder().languageCode(languageCode).build());

        GenerateContentResponse res =
            client.models.generateContent(targetModel, com.google.genai.types.Content.fromParts(
                Part.fromText(trimmed)), cfg.build());

        byte[] data = firstInlineAudio(res);
        if (data != null && data.length > 0) {
          log.info("Gemini TTS succeeded using model {}", targetModel);
          return new Audio(data, AUDIO_MIME);
        }
      } catch (IllegalStateException | IllegalArgumentException e) {
        throw e;
      } catch (RuntimeException e) {
        log.warn("Gemini TTS with model {} failed ({}). Trying next candidate...",
            targetModel, e.getMessage());
        lastException = e;
      }
    }

    log.warn("All Gemini TTS models exhausted", lastException);
    throw new IllegalStateException("Speech synthesis failed: " + (lastException != null ? lastException.getMessage() : "All models failed"));
  }

  /** Maps our UI tags onto BCP-47; falls back to English when unknown. */
  static String normalizeLanguage(String tag) {
    if (tag == null || tag.isBlank()) {
      return "en-IN";
    }
    String base = tag.split("-")[0].toLowerCase(Locale.ROOT);
    return switch (base) {
      case "mai" -> "mai-IN";
      case "hi" -> "hi-IN";
      case "bn" -> "bn-IN";
      case "te" -> "te-IN";
      case "mr" -> "mr-IN";
      case "ta" -> "ta-IN";
      case "gu" -> "gu-IN";
      case "kn" -> "kn-IN";
      case "ml" -> "ml-IN";
      case "pa" -> "pa-IN";
      case "ur" -> "ur-IN";
      case "or" -> "or-IN";
      case "as" -> "as-IN";
      case "ks" -> "ks-IN";
      case "kok" -> "kok-IN";
      default -> "en-IN";
    };
  }

  private byte[] firstInlineAudio(GenerateContentResponse res) {
    try {
      if (res.candidates().isEmpty()) {
        return null;
      }
      for (var candidate : res.candidates().get()) {
        if (candidate.content().isEmpty()) {
          continue;
        }
        for (var part : candidate.content().get().parts().get()) {
          if (part.inlineData().isPresent()) {
            byte[] data = part.inlineData().get().data().orElse(null);
            if (data != null && data.length > 0) {
              return data;
            }
          }
        }
      }
      return null;
    } catch (RuntimeException e) {
      return null;
    }
  }
}