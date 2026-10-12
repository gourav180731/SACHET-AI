package ai.sachet.ingest;

import ai.sachet.domain.IngestionJob;

/** Synchronous upload rejection. The code maps to a safe client error. */
public class ValidationException extends RuntimeException {

  private final String code;

  public ValidationException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }

  static ValidationException failed(String message) {
    return new ValidationException(IngestionJob.Errors.VALIDATION_FAILED, message);
  }

  static ValidationException tooBig(String message) {
    return new ValidationException(IngestionJob.Errors.LIMIT_EXCEEDED, message);
  }
}
