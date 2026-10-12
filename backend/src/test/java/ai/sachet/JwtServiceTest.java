package ai.sachet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.sachet.config.SachetProperties;
import ai.sachet.security.AuthException;
import ai.sachet.security.JwtService;
import ai.sachet.security.SachetPrincipal;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;

/** JWT issue/verify: round-trip, tamper, expiry, wrong-secret rejection. */
class JwtServiceTest {

  static final String SECRET = "test-only-secret-32-chars-minimum";

  private JwtService service(String secret) {
    SachetProperties p = new SachetProperties();
    p.setJwtSecret(secret);
    p.setJwtExpireMin(30);
    return new JwtService(p);
  }

  @Test
  void roundtripCarriesSecurityContext() {
    SachetPrincipal p = service(SECRET)
        .verify(service(SECRET).issue("user-123", List.of("ANALYST"), "RESTRICTED", "C-DOT", List.of("proj")));
    assertEquals("user-123", p.getUserId());
    assertEquals(List.of("ANALYST"), p.getRoles());
    assertEquals("RESTRICTED", p.getClearance());
    assertEquals("C-DOT", p.getDepartment());
    assertTrue(p.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ANALYST")));
  }

  @Test
  void tamperedTokenRejected() {
    String token = service(SECRET).issue("u", List.of("USER"), "INTERNAL", null, List.of());
    String tampered = token.substring(0, token.length() - 2) + (token.endsWith("ab") ? "cd" : "ab");
    assertThrows(AuthException.class, () -> service(SECRET).verify(tampered));
  }

  @Test
  void garbageRejected() {
    assertThrows(AuthException.class, () -> service(SECRET).verify("not-a-token"));
  }

  @Test
  void expiredTokenRejected() {
    var key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    String expired = Jwts.builder().subject("u").claim("type", "access")
        .issuedAt(Date.from(Instant.now().minusSeconds(3600)))
        .expiration(Date.from(Instant.now().minusSeconds(60)))
        .signWith(key).compact();
    assertThrows(AuthException.class, () -> service(SECRET).verify(expired));
  }

  @Test
  void wrongSecretRejected() {
    String token = service(SECRET).issue("u", List.of("USER"), "INTERNAL", null, List.of());
    assertThrows(AuthException.class, () -> service(SECRET + "-different").verify(token));
  }

  @Test
  void wrongTokenTypeRejected() {
    var key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    String other = Jwts.builder().subject("u").claim("type", "refresh")
        .expiration(Date.from(Instant.now().plusSeconds(600)))
        .signWith(key).compact();
    assertThrows(AuthException.class, () -> service(SECRET).verify(other));
  }
}
