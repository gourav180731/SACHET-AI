package ai.sachet.rag;

/**
 * Text embedding. The ONLY implementation wired by default runs fully on this
 * machine (ONNX, no network at inference) so document text never leaves the
 * SACHET boundary for embedding. External embedding providers are NOT offered:
 * adding one would need a ModelPolicy gate + explicit operator opt-in first.
 */
public interface EmbeddingService {

  /** L2-normalized embedding of {@code text}. Never null entries. */
  float[] embed(String text);

  int dimensions();

  String modelId();
}
