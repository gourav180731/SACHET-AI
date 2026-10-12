package ai.sachet.web;

/** Uniform 404 (missing OR no access — existence hiding for protected resources). */
public class NotFoundException extends RuntimeException {
  public NotFoundException() {
    super("Not found.");
  }
}
