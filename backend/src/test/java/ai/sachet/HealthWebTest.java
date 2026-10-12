package ai.sachet;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.sachet.domain.User;
import ai.sachet.repo.UserRepository;
import ai.sachet.security.JwtService;
import ai.sachet.security.PasswordService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** Platform tests: health shape, readiness, safe errors, security headers. */
@SpringBootTest
@AutoConfigureMockMvc
class HealthWebTest {

  @Autowired
  MockMvc mvc;

  @Autowired
  UserRepository users;

  @Autowired
  PasswordService passwords;

  @Autowired
  JwtService jwt;

  @Test
  void livenessShape() throws Exception {
    mvc.perform(get("/api/v1/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ok"))
        .andExpect(jsonPath("$.version").exists());
  }

  @Test
  void livenessCarriesNoSecrets() throws Exception {
    String body = mvc.perform(get("/api/v1/health")).andReturn().getResponse().getContentAsString()
        .toLowerCase();
    for (String needle : new String[] {"secret", "password", "token", "jdbc:", "sachet:sachet@"}) {
      org.junit.jupiter.api.Assertions.assertFalse(body.contains(needle), "leak: " + needle);
    }
  }

  @Test
  void readinessOkWithDatabase() throws Exception {
    mvc.perform(get("/api/v1/ready"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ready"));
  }

  @Test
  void anonymousUnknownRouteRevealsNothing() throws Exception {
    // Security first: anonymous callers cannot probe route existence (no 404 oracle).
    mvc.perform(get("/api/v1/does-not-exist"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value(401));
  }

  @Test
  void authenticatedUnknownRouteIsSafeJson() throws Exception {
    User u = users.save(new User("probe@example.gov", passwords.hash("probe-pw-12")));
    String token = jwt.issue(u.getId(), List.of("USER"), "INTERNAL", null, List.of());
    mvc.perform(get("/api/v1/does-not-exist").header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value(404))
        .andExpect(jsonPath("$.error.message").value("Not found."));
  }

  @Test
  void securityHeadersPresent() throws Exception {
    mvc.perform(get("/api/v1/health"))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("X-Frame-Options", "DENY"))
        .andExpect(header().exists("X-Request-ID"));
  }

  @Test
  void unauthenticatedProtectedEndpointIs401() throws Exception {
    mvc.perform(get("/api/v1/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value(401));
  }
}
