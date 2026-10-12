package ai.sachet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.sachet.security.PasswordService;
import org.junit.jupiter.api.Test;

class PasswordServiceTest {

  private final PasswordService passwords = new PasswordService();

  @Test
  void roundtrip() {
    String hashed = passwords.hash("correct-horse-123");
    assertFalse(hashed.contains("correct-horse-123"));
    assertTrue(passwords.matches("correct-horse-123", hashed));
    assertFalse(passwords.matches("wrong-password", hashed));
  }

  @Test
  void overBcryptLimitRejected() {
    assertThrows(IllegalArgumentException.class, () -> passwords.hash("x".repeat(73)));
  }

  @Test
  void malformedHashNeverAuthenticates() {
    assertFalse(passwords.matches("anything", "not-a-valid-hash"));
    assertFalse(passwords.matches(null, "x"));
    assertFalse(passwords.matches("x", null));
  }

  @Test
  void saltsDiffer() {
    assertFalse(passwords.hash("same").equals(passwords.hash("same")));
    assertEquals(60, passwords.hash("same").length()); // BCrypt modular crypt format
  }
}
