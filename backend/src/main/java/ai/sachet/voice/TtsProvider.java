package ai.sachet.voice;

import java.util.List;

/**
 * Text-to-speech.
 *
 * <p>Implementations MUST obey the same authorization model as text: a TTS call
 * may never bypass authentication, document permissions or the external-transfer
 * policy. {@link #synthesize} is only reached after {@code ModelPolicy} has
 * approved the transfer.
 *
 * <p>Implementations are stateless and must be safe for concurrent calls.
 */
public interface TtsProvider {

  enum Status {
    /** Configured and usable. */
    AVAILABLE,
    /** No provider selected (secure default). */
    NOT_CONFIGURED,
    /** Selected but the API key is missing. */
    MISSING_CREDENTIAL,
    /** Selected but the call failed at runtime. */
    ERROR
  }

  /** Rendered audio for a single utterance. */
  final class Audio {
    private final byte[] bytes;
    private final String mimeType;

    public Audio(byte[] bytes, String mimeType) {
      this.bytes = bytes;
      this.mimeType = mimeType;
    }

    public byte[] bytes() { return bytes; }
    public String mimeType() { return mimeType; }
  }

  Status status();

  /** Why unavailable. Null when AVAILABLE. Never contains secrets. */
  String unavailableReason();

  /** Identifier of the underlying model, for honest capability reporting. */
  String modelId();

  /**
   * Languages this provider can actually render.
   *
   * <p>Callers must not offer a language absent from this list; Gemini TTS
   * auto-detects input language, so claiming support we cannot deliver would be
   * a false capability.
   */
  List<String> supportedLanguages();

  /**
   * Render {@code text} as speech.
   *
   * @param languageHint optional BCP-47 hint; may be null (auto-detect)
   * @return audio bytes, never null
   */
  Audio synthesize(String text, String languageHint);
}