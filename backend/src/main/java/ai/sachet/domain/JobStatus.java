package ai.sachet.domain;

/**
 * Ingestion pipeline states. A document is NEVER silently marked READY:
 * any stage failure lands the job in FAILED with a safe error code.
 * EMBEDDING/INDEXING are consumed by Phase 4; Phase 3 transitions CHUNKING → READY.
 */
public enum JobStatus {
  QUEUED,
  VALIDATING,
  PARSING,
  OCR,
  CHUNKING,
  EMBEDDING,
  INDEXING,
  READY,
  FAILED;

  public boolean isTerminal() {
    return this == READY || this == FAILED;
  }
}
