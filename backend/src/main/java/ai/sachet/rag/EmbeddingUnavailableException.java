package ai.sachet.rag;

/** Embedding subsystem unavailable (model missing/corrupt, engine failure). Maps to 503. */
public class EmbeddingUnavailableException extends RuntimeException {
  public EmbeddingUnavailableException(String message) {
    super(message);
  }

  public EmbeddingUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
