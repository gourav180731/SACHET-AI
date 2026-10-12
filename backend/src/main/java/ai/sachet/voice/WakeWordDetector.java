package ai.sachet.voice;

import java.util.List;

/**
 * Local, always-on wake-phrase detection.
 *
 * <p>Implementations MUST satisfy two invariants, both of which are privacy
 * requirements rather than preferences:
 * <ol>
 *   <li>{@link #detect} is called with 16 kHz mono PCM frames taken from the
 *       microphone and MUST perform every step of detection on the local host.
 *       Audio frames passed in are never queued, logged or transmitted.</li>
 *   <li>An implementation MUST report {@link Status#AVAILABLE} only when it can
 *       genuinely detect the configured phrase. Returning AVAILABLE without a
 *       model that recognises the phrase would be fabricating capability, which
 *       the project constitution forbids.</li>
 * </ol>
 *
 * <p>Implementations must be safe to call from a single dedicated worker thread.
 * They are not required to be thread-safe across concurrent callers.
 */
public interface WakeWordDetector extends AutoCloseable {

  enum Status {
    /** Model present and the configured phrase is genuinely detectable. */
    AVAILABLE,
    /** No engine/model configured at all. */
    NOT_CONFIGURED,
    /** Engine configured but its model files are missing or unreadable. */
    MODEL_MISSING,
    /** Engine present but could not be initialised (e.g. native lib conflict). */
    INIT_FAILED,
    /** Deliberately switched off by configuration. */
    DISABLED
  }

  /** A detection outcome. */
  final class Detection {
    private final boolean fired;
    private final float score;
    private final String transcriptHint;

    private Detection(boolean fired, float score, String transcriptHint) {
      this.fired = fired;
      this.score = score;
      this.transcriptHint = transcriptHint;
    }

    public static Detection fired(float score, String hint) {
      return new Detection(true, score, hint);
    }

    public static Detection noFire(float score) { return new Detection(false, score, null); }

    public boolean fired() { return fired; }

    /** Detector confidence 0..1. Meaningless when {@link #fired()} is false. */
    public float score() { return score; }

    /**
     * Engine-reported match string when the engine can produce one (for example
     * the recognised tokens). Null when the engine cannot. Never log this
     * verbatim if it may contain ambient speech.
     */
    public String transcriptHint() { return transcriptHint; }
  }

  Status status();

  /** Why the engine is unavailable. Null when AVAILABLE. */
  String unavailableReason();

  /** Load the engine/model. Idempotent; safe to call repeatedly. */
  void initialize();

  /**
   * Feed one audio frame.
   *
   * @param pcm16le   16-bit signed little-endian mono samples
   * @param sampleRate Hz; implementations resample if this differs from native
   * @param frameCount number of samples in {@code pcm16le}
   */
  Detection detect(byte[] pcm16le, int sampleRate, int frameCount);

  /** Phrase this instance is actually listening for. */
  String phrase();

  /** Configured trigger threshold, for honest capability reporting. */
  double threshold();

  /** Languages the engine claims, as ISO codes. */
  List<String> supportedLanguages();

  @Override
  void close();
}