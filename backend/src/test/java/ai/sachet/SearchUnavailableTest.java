package ai.sachet;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.sachet.domain.User;
import ai.sachet.repo.DocumentChunkRepository;
import ai.sachet.repo.DocumentPermissionRepository;
import ai.sachet.repo.DocumentRepository;
import ai.sachet.repo.IngestionJobRepository;
import ai.sachet.repo.RoleRepository;
import ai.sachet.repo.UserRepository;
import ai.sachet.rag.EmbeddingService;
import ai.sachet.rag.EmbeddingUnavailableException;
import ai.sachet.security.PasswordService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Engine-down path: explicit 503, never a silent degraded answer. */
@SpringBootTest
@AutoConfigureMockMvc
class SearchUnavailableTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired UserRepository users;
  @Autowired RoleRepository roles;
  @Autowired DocumentRepository documents;
  @Autowired DocumentPermissionRepository grants;
  @Autowired DocumentChunkRepository chunks;
  @Autowired IngestionJobRepository jobs;
  @Autowired PasswordService passwords;

  @MockBean EmbeddingService embeddings;

  @BeforeEach
  void seed() {
    chunks.deleteAll();
    jobs.deleteAll();
    grants.deleteAll();
    documents.deleteAll();
    users.deleteAll();
    roles.deleteAll();
    users.save(new User("dave@example.gov", passwords.hash("dave-pw-1234")));
  }

  @Test
  void embeddingOutageIs503() throws Exception {
    when(embeddings.embed(anyString()))
        .thenThrow(new EmbeddingUnavailableException("engine down"));
    MvcResult login = mvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(
                Map.of("email", "dave@example.gov", "password", "dave-pw-1234"))))
        .andExpect(status().isOk()).andReturn();
    String token = mapper.readTree(login.getResponse().getContentAsString())
        .get("accessToken").asText();
    mvc.perform(post("/api/v1/search")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"query\":\"hello\"}")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error.message").value("Search unavailable."));
  }
}
