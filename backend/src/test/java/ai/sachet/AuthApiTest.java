package ai.sachet;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.sachet.domain.Role;
import ai.sachet.domain.User;
import ai.sachet.repo.RoleRepository;
import ai.sachet.repo.UserRepository;
import ai.sachet.security.PasswordService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Login + authenticated/authorized request paths. Messages stay generic. */
@SpringBootTest
@AutoConfigureMockMvc
class AuthApiTest {

  @Autowired MockMvc mvc;
  @Autowired UserRepository users;
  @Autowired RoleRepository roles;
  @Autowired PasswordService passwords;
  @Autowired ObjectMapper mapper;

  @BeforeEach
  void seed() {
    users.deleteAll();
    roles.deleteAll();
    Role analyst = roles.save(new Role("ANALYST"));
    Role admin = roles.save(new Role("ADMIN"));
    User officer = new User("officer@example.gov", passwords.hash("s3cret-pw"));
    officer.getRoles().add(analyst);
    users.save(officer);
    User boss = new User("admin@example.gov", passwords.hash("admin-pw-1"));
    boss.getRoles().add(admin);
    users.save(boss);
  }

  private String login(String email, String password) throws Exception {
    String body = mvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("email", email, "password", password))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.accessToken").exists())
        .andReturn().getResponse().getContentAsString();
    return mapper.readTree(body).get("accessToken").asText();
  }

  @Test
  void loginOkAndTokenAuthenticates() throws Exception {
    String token = login("officer@example.gov", "s3cret-pw");
    mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roles[0]").value("ANALYST"));
  }

  @Test
  void wrongPasswordIsGeneric401() throws Exception {
    mvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(
                Map.of("email", "officer@example.gov", "password", "nope"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.message").value("Invalid credentials."));
  }

  @Test
  void unknownUserIsIdentical401() throws Exception {
    // Same message as wrong password: no user enumeration.
    mvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(
                Map.of("email", "ghost@example.gov", "password", "whatever"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.message").value("Invalid credentials."));
  }

  @Test
  void invalidEmailShapeIs400() throws Exception {
    mvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("email", "not-an-email", "password", "x"))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void nonAdminDeniedAtAdminEndpoint() throws Exception {
    String token = login("officer@example.gov", "s3cret-pw");
    mvc.perform(get("/api/v1/admin/ping").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value(403));
  }

  @Test
  void adminAllowedAtAdminEndpoint() throws Exception {
    String token = login("admin@example.gov", "admin-pw-1");
    mvc.perform(get("/api/v1/admin/ping").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("pong"));
  }
}
