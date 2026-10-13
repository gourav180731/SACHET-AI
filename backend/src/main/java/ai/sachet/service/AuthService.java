package ai.sachet.service;

import ai.sachet.domain.Classification;
import ai.sachet.domain.Role;
import ai.sachet.domain.User;
import ai.sachet.repo.RoleRepository;
import ai.sachet.repo.UserRepository;
import ai.sachet.security.AuthException;
import ai.sachet.security.JwtService;
import ai.sachet.security.PasswordService;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login: verifies credentials against stored BCrypt hashes, issues a token,
 * and audits both outcomes (actor email only — never passwords or tokens).
 * Failure messages are generic to prevent user enumeration.
 */
@Service
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);

  private final UserRepository users;
  private final RoleRepository rolesRepo;
  private final PasswordService passwords;
  private final JwtService jwt;
  private final AuditService audit;

  public AuthService(UserRepository users, RoleRepository rolesRepo,
      PasswordService passwords, JwtService jwt, AuditService audit) {
    this.users = users;
    this.rolesRepo = rolesRepo;
    this.passwords = passwords;
    this.jwt = jwt;
    this.audit = audit;
  }

  @Transactional
  public LoginResult login(String email, String password) {
    User user = users.findByEmail(email).filter(User::isActive).orElse(null);
    if (user == null || !passwords.matches(password, user.getPasswordHash())) {
      log.info("login failed for {}", email);
      audit.append(email, "auth.login.failed", List.of(), null, "", "");
      throw new AuthException("Invalid credentials.");
    }
    List<String> roles = user.getRoles().stream().map(Role::getName).sorted().toList();
    if (roles.isEmpty()) {
      roles = List.of("USER");
    }
    String token = jwt.issue(user.getId(), roles, user.getClearance().name(),
        user.getDepartment(), List.of());
    audit.append(user.getId(), "auth.login", List.of(), null, "", "");
    log.info("login ok user={}", user.getId());
    return new LoginResult(token, roles);
  }

  @Transactional
  public LoginResult loginWithGoogle(String email, String name, String picture) {
    if (email == null || email.isBlank()) {
      throw new AuthException("Email is required for Google SSO.");
    }
    String normalizedEmail = email.trim().toLowerCase();
    User user = users.findByEmail(normalizedEmail).orElse(null);

    if (user == null) {
      String randomSecret = UUID.randomUUID().toString();
      user = new User(normalizedEmail, passwords.hash(randomSecret));
      user.setClearance(Classification.INTERNAL);
      user.setDepartment("Operations");
      user.setActive(true);

      Role userRole = rolesRepo.findByName("USER").orElseGet(() -> rolesRepo.save(new Role("USER")));
      user.getRoles().add(userRole);
      user = users.save(user);
      log.info("Provisioned new Google SSO user: {}", normalizedEmail);
    } else if (!user.isActive()) {
      log.warn("Login attempt for deactivated Google user: {}", normalizedEmail);
      audit.append(normalizedEmail, "auth.login.google.disabled", List.of(), null, "", "");
      throw new AuthException("Account is deactivated. Contact your security administrator.");
    }

    List<String> roles = user.getRoles().stream().map(Role::getName).sorted().toList();
    if (roles.isEmpty()) {
      roles = List.of("USER");
    }

    String token = jwt.issue(user.getId(), roles, user.getClearance().name(),
        user.getDepartment(), List.of());
    audit.append(user.getId(), "auth.login.google", List.of(), null, "", "");
    log.info("Google login ok user={} email={}", user.getId(), normalizedEmail);
    return new LoginResult(token, roles);
  }

  public record LoginResult(String accessToken, List<String> roles) {
  }
}
