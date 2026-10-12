package ai.sachet.security;

/** Generic authentication failure. Handlers map this to 401 without detail. */
public class AuthException extends RuntimeException {
  public AuthException(String message) {
    super(message);
  }
}
