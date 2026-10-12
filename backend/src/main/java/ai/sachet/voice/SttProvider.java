package ai.sachet.voice;

import java.util.List;

/**
 * Speech-to-text (STT) provider interface.
 *
 * <p>Transcribes spoken audio into text using configured model providers (e.g. Gemini).
 * Obeying SACHET AI privacy & security guidelines: audio transcription using external
 * models is an external data transfer and must be gated by authentication and {@link ai.sachet.service.ModelPolicy}.
 */
public interface SttProvider {

  enum Status {
    /** Provider configured and available. */
    AVAILABLE,
    /** Provider not selected. */
    NOT_CONFIGURED,
    /** Provider selected but API key missing. */
    MISSING_CREDENTIAL,
    /** Provider failed or errored. */
    ERROR
  }

  /**
   * Result of audio transcription.
   *
   * @param text The transcribed text in authentic native script.
   * @param detectedLanguage The language tag (or requested hint).
   */
  record Transcription(String text, String detectedLanguage) {}

  Status status();

  String unavailableReason();

  String modelId();

  List<String> supportedLanguages();

  Transcription transcribe(byte[] audioBytes, String mimeType, String languageHint);
}
