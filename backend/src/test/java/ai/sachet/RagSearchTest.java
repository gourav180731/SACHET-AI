package ai.sachet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Phase 4 retrieval: real embeddings end-to-end (upload → READY → search).
 * Mandatory: cross-user chunks NEVER appear; clearance gates retrieval;
 * citations + evidence status are exact.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RagSearchTest {

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
  String carolToken;
  String aliceDocId;
  String bobDocId;
  String secretDocId;

  static final String ALICE_TEXT =
      "The emergency communication process requires district approval before broadcast. "
          + "CAP alerts follow SOP-7 for dissemination across all telecom circles without delay.";
  static final String BOB_TEXT =
      "Quarterly budget review schedule for telecom circles. Finance meets every third Friday "
          + "to reconcile circle expenditure against the annual operating plan.";
  static final String SECRET_TEXT =
      "Classified cipher rotation timetable for secure backbone links. "
          + "Keys rotate every Sunday at midnight under dual custody protocol.";

  @BeforeEach
  void seed() throws Exception {
    chunks.deleteAll();
    jobs.deleteAll();
    grants.deleteAll();
    documents.deleteAll();
    users.deleteAll();
    roles.deleteAll();
    Role analyst = roles.save(new Role("ANALYST"));
    User alice = new User("alice@example.gov", passwords.hash("alice-pw-12"));
    alice.getRoles().add(analyst);
    alice.setDepartment("C-DOT");
    alice.setClearance(Classification.SECRET); // may author SECRET docs; bob stays INTERNAL
    alice = users.save(alice);
    User bob = new User("bob@example.gov", passwords.hash("bob-pw-1234"));
    bob = users.save(bob);
    User carol = new User("carol@example.gov", passwords.hash("carol-pw-12"));
    carol = users.save(carol);
    aliceToken = login("alice@example.gov", "alice-pw-12");
    bobToken = login("bob@example.gov", "bob-pw-1234");
    carolToken = login("carol@example.gov", "carol-pw-12");

    aliceDocId = uploadReady(aliceToken, "sop.txt", ALICE_TEXT, "INTERNAL");
    bobDocId = uploadReady(bobToken, "budget.txt", BOB_TEXT, "INTERNAL");
    secretDocId = uploadReady(aliceToken, "cipher.txt", SECRET_TEXT, "SECRET");
    // Grant that must NOT escalate: bob is INTERNAL, doc is SECRET.
    grants.save(new DocumentPermission(secretDocId, PrincipalType.USER, bob.getId()));
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
      Thread.sleep(300);
    }
  }

  private JsonNode search(String token, String query, String docIdsJson) throws Exception {
    String body = docIdsJson == null
        ? "{\"query\":\"" + query + "\"}"
        : "{\"query\":\"" + query + "\",\"docIds\":" + docIdsJson + "}";
    MvcResult r = mvc.perform(post("/api/v1/search")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk()).andReturn();
    return mapper.readTree(r.getResponse().getContentAsString());
  }

  @Test
  void semanticSearchFindsParaphraseWithCitations() throws Exception {
    JsonNode res = search(aliceToken, "How are emergency alerts approved?", null);
    assertTrue(res.get("results").size() > 0, "expected hits");
    boolean found = false;
    for (JsonNode hit : res.get("results")) {
      if (hit.get("documentId").asText().equals(aliceDocId)) {
        found = true;
        assertEquals(1, hit.get("page").asInt());
        assertTrue(hit.has("documentTitle") || hit.has("documentId"));
        assertTrue(hit.get("vectorScore").asDouble() > 0.3, "similarity sanity");
      }
    }
    assertTrue(found, "alice's SOP must be retrieved: " + res);
    assertFalse(res.get("evidenceStatus").asText().equals("INSUFFICIENT"));
  }

  @Test
  void crossUserChunksNeverAppear() throws Exception {
    // Bob searches alice's topic: zero alice chunks, zero secret chunks.
    JsonNode res = search(bobToken, "emergency communication approval broadcast", null);
    for (JsonNode hit : res.get("results")) {
      String id = hit.get("documentId").asText();
      assertFalse(id.equals(aliceDocId), "cross-user leak!");
      assertFalse(id.equals(secretDocId), "classified leak!");
    }
  }

  @Test
  void ownerFindsOwnAndBobFindsOwn() throws Exception {
    JsonNode b = search(bobToken, "budget review schedule telecom circles", null);
    boolean found = false;
    for (JsonNode hit : b.get("results")) {
      if (hit.get("documentId").asText().equals(bobDocId)) {
        found = true;
      }
      assertFalse(hit.get("documentId").asText().equals(aliceDocId));
    }
    assertTrue(found, "bob must find his own doc: " + b);
  }

  @Test
  void grantCannotEscalateClearanceInRetrieval() throws Exception {
    // Bob holds a grant on the SECRET doc but his clearance is INTERNAL.
    JsonNode res = search(bobToken, "cipher rotation timetable backbone links", null);
    for (JsonNode hit : res.get("results")) {
      assertFalse(hit.get("documentId").asText().equals(secretDocId),
          "clearance bypass via grant!");
    }
  }

  @Test
  void docIdsFilterIntersectsReadable() throws Exception {
    // Alice restricts to bob's doc (unreadable to her) → nothing, honestly reported.
    JsonNode res = search(aliceToken, "budget review", "[\"" + bobDocId + "\"]");
    assertEquals(0, res.get("results").size());
    assertEquals("INSUFFICIENT", res.get("evidenceStatus").asText());
    // ...and to her own doc → hits.
    JsonNode res2 = search(aliceToken, "emergency communication", "[\"" + aliceDocId + "\"]");
    assertTrue(res2.get("results").size() > 0);
  }

  @Test
  void emptyCorpusIsInsufficientWithExactPhrase() throws Exception {
    JsonNode res = search(carolToken, "anything at all here", null);
    assertEquals(0, res.get("results").size());
    assertEquals("INSUFFICIENT", res.get("evidenceStatus").asText());
    assertEquals(EvidenceEvaluator.INSUFFICIENT_MESSAGE, res.get("message").asText());
  }

  @Test
  void unauthenticatedSearchIs401() throws Exception {
    mvc.perform(post("/api/v1/search")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"query\":\"hello\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void blankQueryIs400() throws Exception {
    mvc.perform(post("/api/v1/search")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"query\":\"\"}")
            .header("Authorization", "Bearer " + aliceToken))
        .andExpect(status().isBadRequest());
  }

  @Test
  void chunksAreEmbeddedAfterReady() {
    for (var c : chunks.findByDocumentIdOrderByChunkIndexAsc(aliceDocId)) {
      assertTrue(c.getEmbedding() != null && c.getEmbedding().length == 384,
          "chunk missing 384-dim embedding");
    }
  }

  @Test
  void hindiRetrievalWorks() throws Exception {
    String hindi = "आपातकालीन संचार प्रक्रिया के लिए जिला अनुमोदन आवश्यक है। "
        + "सभी दूरसंचार सर्किलों में बिना विलंब के प्रसारण किया जाता है।";
    String hindiDoc = uploadReady(aliceToken, "hindi.txt", hindi, "INTERNAL");
    JsonNode res = search(aliceToken, "आपातकालीन संचार प्रक्रिया", null);
    boolean found = false;
    for (JsonNode hit : res.get("results")) {
      if (hit.get("documentId").asText().equals(hindiDoc)) {
        found = true;
      }
    }
    assertTrue(found, "Hindi doc must be retrieved: " + res);
  }
}
