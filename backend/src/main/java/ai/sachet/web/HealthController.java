package ai.sachet.web;

import ai.sachet.config.SachetProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Health probes. Liveness carries no sensitive data; readiness verifies a DB
 * connection and answers with a generic reason when degraded.
 */
@RestController
@RequestMapping("${sachet.api-prefix:/api/v1}")
public class HealthController {

  private static final Logger log = LoggerFactory.getLogger(HealthController.class);

  private final SachetProperties props;
  private final DataSource dataSource;

  public HealthController(SachetProperties props, DataSource dataSource) {
    this.props = props;
    this.dataSource = dataSource;
  }

  @GetMapping("/health")
  public Map<String, String> health() {
    return Map.of("status", "ok", "version", props.getAppVersion());
  }

  @GetMapping("/ready")
  public ResponseEntity<Map<String, String>> ready(HttpServletRequest req) {
    try (var conn = dataSource.getConnection()) {
      if (conn.isValid(2)) {
        return ResponseEntity.ok(Map.of("status", "ready"));
      }
    } catch (Exception e) {
      log.warn("readiness check failed: {}", e.getClass().getSimpleName());
    }
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(Map.of("status", "not-ready", "reason", "dependency unavailable"));
  }
}
