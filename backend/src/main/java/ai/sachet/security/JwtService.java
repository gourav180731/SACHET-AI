package ai.sachet.security;

import ai.sachet.config.SachetProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * JWT issue/verify. Algorithm is fixed to HS256 (not configurable) to rule out
 * algorithm-confusion attacks. Tokens carry the {@code security_context} claims
 * every later-phase query must present.
 */
@Service
public class JwtService {

  /** Fixed algorithm name — intentionally a constant, never a setting. */
  public static final String ALGORITHM = "HS256";
  public static final String TOKEN_TYPE = "access";

  private static final Logger log = LoggerFactory.getLogger(JwtService.class);

  private final SachetProperties props;

  public JwtService(SachetProperties props) {
    this.props = props;
  }

  private SecretKey key() {
    // Length floor is enforced by SachetPropertiesValidator (>= 32 chars = 256 bits).
    return Keys.hmacShaKeyFor(props.getJwtSecret().getBytes(StandardCharsets.UTF_8));
  }

  public String issue(String userId, List<String> roles, String clearance,
      String department, List<String> scopes) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(userId)
        .claim("roles", roles == null ? List.of("USER") : roles)
        .claim("clearance", clearance == null ? "INTERNAL" : clearance)
        .claim("department", department)
        .claim("scopes", scopes == null ? List.of() : scopes)
        .claim("type", TOKEN_TYPE)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds((long) props.getJwtExpireMin() * 60)))
        .signWith(key())
        .compact();
  }

  /** Parses + verifies; any problem becomes {@link AuthException} (generic 401). */
  public SachetPrincipal verify(String token) {
    Claims claims;
    try {
      claims = Jwts.parser().verifyWith(key()).build()
          .parseSignedClaims(token).getPayload();
    } catch (JwtException | IllegalArgumentException e) {
      log.info("token rejected: {}", e.getClass().getSimpleName());
      throw new AuthException("Invalid or expired credentials.");
    }
    if (!TOKEN_TYPE.equals(claims.get("type", String.class)) || claims.getSubject() == null) {
      log.info("token rejected: claims");
      throw new AuthException("Invalid or expired credentials.");
    }
    @SuppressWarnings("unchecked")
    List<String> roles = claims.get("roles", List.class);
    @SuppressWarnings("unchecked")
    List<String> scopes = claims.get("scopes", List.class);
    return new SachetPrincipal(
        claims.getSubject(),
        roles == null ? List.of("USER") : roles,
        claims.get("clearance", String.class),
        claims.get("department", String.class),
        scopes == null ? List.of() : scopes);
  }
}
