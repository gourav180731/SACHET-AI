package ai.sachet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.sachet.domain.Classification;
import ai.sachet.domain.Document;
import ai.sachet.domain.DocumentPermission;
import ai.sachet.domain.DocumentPermission.PrincipalType;
import ai.sachet.domain.Role;
import ai.sachet.domain.User;
import ai.sachet.rag.EvidenceEvaluator;
import ai.sachet.repo.DocumentChunkRepository;
import ai.sachet.repo.DocumentPermissionRepository;
import ai.sachet.repo.DocumentRepository;
import ai.sachet.repo.IngestionJobRepository;
import ai.sachet.repo.RoleRepository;
import ai.sachet.repo.UserRepository;
import ai.sachet.security.PasswordService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Chat Assistant tests (Phase 5/7).
 * Verifies that:
 * 1. Answers are grounded strictly in authorized documents.
 * 2. Unrelated queries return insufficient-evidence message.
 * 3. RBAC & clearance restrictions prevent unauthorized document inclusion.
 * 4. Response modes and capabilities are reported correctly.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RagChatTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired UserRepository users;
  @Autowired RoleRepository roles;
  @Autowired DocumentRepository documents;
  @Autowired DocumentPermissionRepository grants;
  @Autowired DocumentChunkRepository chunks;
  @Autowired IngestionJobRepository jobs;
  @Autowired PasswordService passwords;

  String aliceToken;
  String bobToken;
  String aliceDocId;
  String secretDocId;

  static final String SOP_TEXT =
      "Emergency broadcast authorization procedure for telecom networks. "
          + "All public alert broadcasts require District Collector authorization. "
          + "Priority 1 notifications must dispatch across SMS gateways within two minutes.";

  static final String CIPHER_TEXT =
      "TOP SECRET: Cryptographic master key schedule for critical defense backbones. "
          + "Physical access requires biometric dual-custody verification at Station Echo.";

  @BeforeEach
  void seed() throws Exception {
    chunks.deleteAll();
    jobs.deleteAll();
    grants.deleteAll();
    documents.deleteAll();
    users.deleteAll();
    roles.deleteAll();

    Role analyst = roles.save(new Role("ANALYST"));
    User alice = new User("alice@example.gov", passwords.hash("alice-pw-1234"));
    alice.getRoles().add(analyst);
    alice.setDepartment("DISASTER_MGMT");
    alice.setClearance(Classification.SECRET);
    users.save(alice);

    User bob = new User("bob@example.gov", passwords.hash("bob-pw-123456"));
    bob.setDepartment("FINANCE");
    bob.setClearance(Classification.INTERNAL);
    users.save(bob);

    aliceToken = login("alice@example.gov", "alice-pw-1234");
    bobToken = login("bob@example.gov", "bob-pw-123456");

    aliceDocId = uploadReady(aliceToken, "sop.txt", SOP_TEXT, "INTERNAL");
    secretDocId = uploadReady(aliceToken, "cipher.txt", CIPHER_TEXT, "SECRET");
  }

  private String login(String email, String password) throws Exception {
    MvcResult r = mvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("email", email, "password", password))))
        .andExpect(status().isOk()).andReturn();
    return mapper.readTree(r.getResponse().getContentAsString()).get("accessToken").asText();
  }

  private String uploadReady(String token, String name, String text, String classification)
      throws Exception {
    MvcResult r = mvc.perform(multipart("/api/v1/documents")
            .file(new MockMultipartFile("file", name, "text/plain",
                text.getBytes(StandardCharsets.UTF_8)))
            .param("classification", classification)
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isAccepted()).andReturn();
    JsonNode body = mapper.readTree(r.getResponse().getContentAsString());
    String jobId = body.get("jobId").asText();
    long deadline = System.currentTimeMillis() + 40000;
    while (true) {
      MvcResult j = mvc.perform(get("/api/v1/ingestion/jobs/" + jobId)
              .header("Authorization", "Bearer " + token))
          .andExpect(status().isOk()).andReturn();
      JsonNode job = mapper.readTree(j.getResponse().getContentAsString());
      String st = job.get("status").asText();
      if (st.equals("READY")) {
        return body.get("documentId").asText();
      }
      if (st.equals("FAILED")) {
        throw new AssertionError("ingestion failed: " + job);
      }
      if (System.currentTimeMillis() > deadline) {
        throw new AssertionError("ingestion stuck: " + job);
      }
      Thread.sleep(250);
    }
  }

  @Test
  @DisplayName("chat: relevant query returns grounded answer with exact citation sources")
  void groundedChatReturnsAnswerAndSources() throws Exception {
    MvcResult r = mvc.perform(post("/api/v1/chat")
            .header("Authorization", "Bearer " + aliceToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of(
                "query", "What authorization is required for emergency broadcast?",
                "topK", 5))))
        .andExpect(status().isOk())
        .andReturn();

    JsonNode res = mapper.readTree(r.getResponse().getContentAsString());
    String answer = res.get("answer").asText();
    assertNotNull(answer);
    assertFalse(answer.isBlank());
    assertTrue(answer.toLowerCase().contains("emergency") || answer.toLowerCase().contains("broadcast"));

    JsonNode sources = res.get("sources");
    assertNotNull(sources);
    assertTrue(sources.size() > 0);
    assertEquals(aliceDocId, sources.get(0).get("documentId").asText());
  }

  @Test
  @DisplayName("chat: unrelated query reports insufficient evidence without hallucinating")
  void unrelatedQueryReportsInsufficientEvidence() throws Exception {
    MvcResult r = mvc.perform(post("/api/v1/chat")
            .header("Authorization", "Bearer " + aliceToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of(
                "query", "How do I cultivate organic dragonfruit in greenhouse soil?",
                "topK", 5))))
        .andExpect(status().isOk())
        .andReturn();

    JsonNode res = mapper.readTree(r.getResponse().getContentAsString());
    assertEquals(EvidenceEvaluator.INSUFFICIENT_MESSAGE, res.get("answer").asText());
    assertEquals("INSUFFICIENT", res.get("evidenceStatus").asText());
  }

  @Test
  @DisplayName("chat: Bob cannot see or retrieve answers from Alice's SECRET document")
  void rbacClearanceProtectsSecretDocument() throws Exception {
    // Bob asks about the secret cryptographic key schedule
    MvcResult r = mvc.perform(post("/api/v1/chat")
            .header("Authorization", "Bearer " + bobToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of(
                "query", "What is the cryptographic master key schedule for Station Echo?",
                "topK", 5))))
        .andExpect(status().isOk())
        .andReturn();

    JsonNode res = mapper.readTree(r.getResponse().getContentAsString());
    // Bob has clearance INTERNAL; secret document is SECRET -> must be filtered out
    assertEquals(EvidenceEvaluator.INSUFFICIENT_MESSAGE, res.get("answer").asText());
    assertEquals("INSUFFICIENT", res.get("evidenceStatus").asText());
    assertEquals(0, res.get("sources").size());
  }

  @Test
  @DisplayName("chat: modes endpoint lists response modes and Indian languages")
  void modesReported() throws Exception {
    mvc.perform(get("/api/v1/chat/modes")
            .header("Authorization", "Bearer " + aliceToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.defaultMode").value("detailed"))
        .andExpect(jsonPath("$.supportedLanguages").isArray())
        .andExpect(jsonPath("$.responseModes").isArray());
  }
}