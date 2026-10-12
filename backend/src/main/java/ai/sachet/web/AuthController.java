package ai.sachet.web;

import ai.sachet.domain.User;
import ai.sachet.repo.UserRepository;
import ai.sachet.security.SachetPrincipal;
import ai.sachet.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authentication endpoint. Failures are generic (no user enumeration). */
@RestController
@RequestMapping("${sachet.api-prefix:/api/v1}/auth")
public class AuthController {

  private final AuthService auth;
  private final UserRepository users;

  public AuthController(AuthService auth, UserRepository users) {
    this.auth = auth;
    this.users = users;
  }

  public record LoginRequest(
      @Email @NotBlank String email,
      @NotBlank String password) {
  }

  @PostMapping("/login")
  public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest req) {
    AuthService.LoginResult r = auth.login(req.email().trim().toLowerCase(), req.password());
    return ResponseEntity.ok(Map.of(
        "tokenType", "Bearer",
        "accessToken", r.accessToken(),
        "roles", List.copyOf(r.roles())));
  }

  @org.springframework.web.bind.annotation.GetMapping("/me")
  public ResponseEntity<Map<String, Object>> me(
      @org.springframework.security.core.annotation.AuthenticationPrincipal SachetPrincipal p) {
    User u = users.findById(p.getUserId()).orElse(null);
    if (u == null || !u.isActive()) {
      throw new ai.sachet.security.AuthException("Unauthorized.");
    }
    return ResponseEntity.ok(Map.of(
        "id", u.getId(),
        "email", u.getEmail(),
        "clearance", p.getClearance(),
        "department", u.getDepartment() == null ? "" : u.getDepartment(),
        "roles", p.getRoles()));
  }
}
