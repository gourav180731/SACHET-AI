package ai.sachet;

import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ai.sachet.security.SachetPrincipal;

/**
 * TEST-ONLY endpoints (src/test). Exist so the security chain (401/403 paths)
 * can be exercised before business endpoints land in later phases.
 */
@RestController
@RequestMapping("/api/v1")
public class ProbeController {

  @GetMapping("/me")
  public Map<String, Object> me(@AuthenticationPrincipal SachetPrincipal p) {
    return Map.of("userId", p.getUserId(), "roles", List.copyOf(p.getRoles()));
  }

  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping("/admin/ping")
  public Map<String, String> adminPing() {
    return Map.of("status", "pong");
  }
}
