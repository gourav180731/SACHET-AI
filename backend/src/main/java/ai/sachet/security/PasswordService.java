package ai.sachet.security;

import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** Password hashing (BCrypt-12). Only hashes cross this boundary — never plaintext. */
@Service
public class PasswordService {

  private static final int MAX_BYTES = 72; // BCrypt limit: fail loudly, never truncate.
  private final PasswordEncoder encoder = new BCryptPasswordEncoder(12);

  public String hash(String raw) {
    if (raw == null || raw.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
      throw new IllegalArgumentException("Password exceeds 72 bytes");
    }
    return encoder.encode(raw);
  }

  public boolean matches(String raw, String hashed) {
    if (raw == null || hashed == null) {
      return false;
    }
    try {
      return encoder.matches(raw, hashed);
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
