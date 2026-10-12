package ai.sachet;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.sachet.domain.Role;
import ai.sachet.domain.User;
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
import java.util.List;
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

/** Phase 6: grounded artifact generation, authorization, download, delete, auth/me, search filters. */
@SpringBootTest
@AutoConfigureMockMvc
class ArtifactGenerationTest {

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
    users.save(alice);
    users.save(new User("bob@example.gov", passwords.hash("bob-pw-1234")));
    aliceToken = login("alice@example.gov", "alice-pw-12");
    bobToken = login("bob@example.gov", "bob-pw-1234");
  }

  private String login(String email, String password) throws Exception {
    MvcResult r = mvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("email", email, "password", password))))
        .andExpect(status().isOk()).andReturn();
    return mapper.readTree(r.getResponse().getContentAsString()).get("accessToken").asText();
  }

  private JsonNode awaitJob(String token, String jobId) throws Exception {
    long deadline = System.currentTimeMillis() + 25000;
    while (true) {
      MvcResult r = mvc.perform(get("/api/v1/ingestion/jobs/" + jobId)
              .header("Authorization", "Bearer " + token))
          .andExpect(status().isOk()).andReturn();
      JsonNode job = mapper.readTree(r.getResponse().getContentAsString());
      String st = job.get("status").asText();
      if (st.equals("READY") || st.equals("FAILED")) {
        return job;
      }
      if (System.currentTimeMillis() > deadline) {
        throw new AssertionError("job did not finish: " + job);
      }
      Thread.sleep(250);
    }
  }

  private String seedDoc(String token, String name, String body) throws Exception {
    MvcResult r = mvc.perform(multipart("/api/v1/documents")
            .file(new MockMultipartFile("file", name, "text/plain",
                body.getBytes(StandardCharsets.UTF_8)))
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isAccepted()).andReturn();
    JsonNode up = mapper.readTree(r.getResponse().getContentAsString());
    awaitJob(token, up.get("jobId").asText());
    return up.get("documentId").asText();
  }

  @Test
  void docxGenerationLifecycle() throws Exception {
    String docId = seedDoc(aliceToken, "sop.txt",
        "Emergency communication procedures overview. All officers must follow the radio protocol. Communications are logged and reviewed weekly.");
    MvcResult gen = mvc.perform(post("/api/v1/studio/generate/docx")
            .header("Authorization", "Bearer " + aliceToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of(
                "title", "Comm SOP Report", "topic", "communication procedures",
                "docIds", List.of(docId), "topK", 5))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value("DOCX"))
        .andExpect(jsonPath("$.classification").value("INTERNAL"))
        .andReturn();
    JsonNode artifact = mapper.readTree(gen.getResponse().getContentAsString());
    String id = artifact.get("id").asText();

    mvc.perform(get("/api/v1/artifacts").header("Authorization", "Bearer " + aliceToken))
        .andExpect(jsonPath("$.totalElements").value(1));

    MvcResult dl = mvc.perform(get("/api/v1/artifacts/" + id + "/download")
            .header("Authorization", "Bearer " + aliceToken))
        .andExpect(status().isOk()).andReturn();
    org.junit.jupiter.api.Assertions.assertTrue(dl.getResponse().getContentLength() > 0);

    // bob cannot see or download alice's artifact (existence hiding)
    mvc.perform(get("/api/v1/artifacts/" + id).header("Authorization", "Bearer " + bobToken))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/artifacts/" + id + "/download").header("Authorization", "Bearer " + bobToken))
        .andExpect(status().isNotFound());

    mvc.perform(delete("/api/v1/artifacts/" + id).header("Authorization", "Bearer " + aliceToken))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/artifacts").header("Authorization", "Bearer " + aliceToken))
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void pptxGenerationLifecycle() throws Exception {
    String docId = seedDoc(aliceToken, "brief.txt",
        "Quarterly preparedness briefing for all departments. Logistics must pre-position supplies. Communication trees are validated monthly.");
    MvcResult gen = mvc.perform(post("/api/v1/studio/generate/pptx")
            .header("Authorization", "Bearer " + aliceToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of(
                "title", "Preparedness Briefing", "topic", "preparedness briefing",
                "docIds", List.of(docId), "topK", 5, "slides", 5))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value("PPTX"))
        .andReturn();
    JsonNode artifact = mapper.readTree(gen.getResponse().getContentAsString());
    MvcResult dl = mvc.perform(get("/api/v1/artifacts/" + artifact.get("id").asText() + "/download")
            .header("Authorization", "Bearer " + aliceToken))
        .andExpect(status().isOk()).andReturn();
    org.junit.jupiter.api.Assertions.assertTrue(dl.getResponse().getContentLength() > 0);
  }

  @Test
  void documentDownloadAuthorized() throws Exception {
    String docId = seedDoc(aliceToken, "dl.txt", "downloadable content for the download test case");
    MvcResult r = mvc.perform(get("/api/v1/documents/" + docId + "/download")
            .header("Authorization", "Bearer " + aliceToken))
        .andExpect(status().isOk()).andReturn();
    org.junit.jupiter.api.Assertions.assertTrue(
        r.getResponse().getContentAsString().contains("downloadable content"));
    mvc.perform(get("/api/v1/documents/" + docId + "/download")
            .header("Authorization", "Bearer " + bobToken))
        .andExpect(status().isNotFound());
  }

  @Test
  void authMeReturnsProfile() throws Exception {
    mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + aliceToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("alice@example.gov"))
        .andExpect(jsonPath("$.roles[0]").value("ANALYST"));
  }

  @Test
  void searchClassificationFilterWorks() throws Exception {
    seedDoc(aliceToken, "internal.txt", "internal warehouse logistics coordination memo for the regional office");
    String secretDoc = seedDoc(aliceToken, "secret.txt", "secret field exercise schedule with restricted distribution");
    // default classification INTERNAL; mark second as RESTRICTED via details not available → skip
    MvcResult r = mvc.perform(post("/api/v1/search")
            .header("Authorization", "Bearer " + aliceToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of(
                "query", "logistics", "classification", "INTERNAL"))))
        .andExpect(status().isOk()).andReturn();
    JsonNode body = mapper.readTree(r.getResponse().getContentAsString());
    org.junit.jupiter.api.Assertions.assertTrue(body.get("results").size() >= 1);
    MvcResult r2 = mvc.perform(post("/api/v1/search")
            .header("Authorization", "Bearer " + aliceToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of(
                "query", "logistics", "classification", "CONFIDENTIAL"))))
        .andExpect(status().isOk()).andReturn();
    JsonNode body2 = mapper.readTree(r2.getResponse().getContentAsString());
    org.junit.jupiter.api.Assertions.assertEquals(0, body2.get("results").size());
  }
}
