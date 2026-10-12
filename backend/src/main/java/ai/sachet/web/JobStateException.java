package ai.sachet.web;

/** Illegal job lifecycle transition (e.g. retrying a non-FAILED job). Safe message only. */
public class JobStateException extends RuntimeException {
  public JobStateException(String message) {
    super(message);
  }
}
