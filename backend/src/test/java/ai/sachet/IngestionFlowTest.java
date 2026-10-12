package ai.sachet;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.sachet.domain.Document;
import ai.sachet.domain.DocumentPermission;
import ai.sachet.domain.DocumentPermission.PrincipalType;
import ai.sachet.domain.Role;
import ai.sachet.domain.User;
import ai.sachet.ingest.OcrService;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Phase 3 flows: upload → async READY, validation, versions, isolation, OCR, retry. */
@SpringBootTest
@AutoConfigureMockMvc
class IngestionFlowTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired UserRepository users;
  @Autowired RoleRepository roles;
  @Autowired DocumentRepository documents;
  @Autowired DocumentPermissionRepository grants;
  @Autowired DocumentChunkRepository chunks;
  @Autowired IngestionJobRepository jobs;
  @Autowired PasswordService passwords;

  @MockBean OcrService ocr; // unavailable by default; stubbed per-test for the OCR path

  String aliceToken;
  String bobToken;
  String aliceId;
  String bobId;

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
    alice = users.save(alice);
    User bob = new User("bob@example.gov", passwords.hash("bob-pw-1234"));
    bob = users.save(bob);
    aliceId = alice.getId();
    bobId = bob.getId();
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

  private JsonNode upload(String token, String filename, byte[] bytes, String classification)
      throws Exception {
    MockMultipartFile file = new MockMultipartFile("file", filename,
        MediaType.TEXT_PLAIN_VALUE, bytes);
    var req = multipart("/api/v1/documents").file(file)
        .header("Authorization", "Bearer " + token);
    if (classification != null) {
      req.param("classification", classification);
    }
    MvcResult r = mvc.perform(req).andReturn();
    return mapper.readTree(r.getResponse().getContentAsString());
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

  @Test
  void txtUploadToReadyWithProvenance() throws Exception {
    String text = "# Intro\n\nEmergency process overview.\n\n# Steps\n\nFollow the steps carefully.\n";
    MvcResult r = mvc.perform(multipart("/api/v1/documents")
            .file(new MockMultipartFile("file", "sop.txt", "text/plain",
                text.getBytes(StandardCharsets.UTF_8)))
            .header("Authorization", "Bearer " + aliceToken))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.duplicate").value(false))
        .andReturn();
    JsonNode body = mapper.readTree(r.getResponse().getContentAsString());
    JsonNode job = awaitJob(aliceToken, body.get("jobId").asText());
    assert job.get("status").asText().equals("READY") : job.toString();

    String docId = body.get("documentId").asText();
    mvc.perform(get("/api/v1/documents/" + docId)
            .header("Authorization", "Bearer " + aliceToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pageCount").value(1))
        .andExpect(jsonPath("$.sha256").exists())
        .andExpect(jsonPath("$.classification").value("INTERNAL"));

    MvcResult chunks = mvc.perform(get("/api/v1/documents/" + docId + "/chunks")
            .header("Authorization", "Bearer " + aliceToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].pageNumber").value(1))
        .andReturn();
    String chunkBody = chunks.getResponse().getContentAsString();
    org.junit.jupiter.api.Assertions.assertTrue(chunkBody.contains("Intro"));
    org.junit.jupiter.api.Assertions.assertTrue(chunkBody.contains("Steps"));
  }

  @Test
  void unsupportedExtensionRejectedSync() throws Exception {
    // .xyz (not .exe): Windows AV locks fresh MZ .exe files and flakes temp cleanup.
    mvc.perform(multipart("/api/v1/documents")
            .file(new MockMultipartFile("file", "evil.xyz", "application/octet-stream", new byte[] {0x4D, 0x5A}))
            .header("Authorization", "Bearer " + aliceToken))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.errorCode").value("VALIDATION_FAILED"));
  }

  @Test
  void magicMismatchRejectedSync() throws Exception {
    mvc.perform(multipart("/api/v1/documents")
            .file(new MockMultipartFile("file", "fake.pdf", "application/pdf",
                "just text pretending".getBytes(StandardCharsets.UTF_8)))
            .header("Authorization", "Bearer " + aliceToken))
        .andExpect(status().isBadRequest());
  }

  @Test
  void classificationAboveClearanceForbidden() throws Exception {
    mvc.perform(multipart("/api/v1/documents")
            .file(new MockMultipartFile("file", "s.txt", "text/plain", "secret stuff".getBytes(StandardCharsets.UTF_8)))
            .param("classification", "SECRET")
            .header("Authorization", "Bearer " + aliceToken))
        .andExpect(status().isForbidden());
    // no document leaked into the readable set
    mvc.perform(get("/api/v1/documents").header("Authorization", "Bearer " + aliceToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void duplicateUploadReturnsExisting() throws Exception {
    byte[] bytes = "same content twice in this file body".getBytes(StandardCharsets.UTF_8);
    JsonNode first = upload(aliceToken, "a.txt", bytes, "INTERNAL");
    awaitJob(aliceToken, first.get("jobId").asText());
    JsonNode second = upload(aliceToken, "a-copy.txt", bytes, "INTERNAL");
    org.junit.jupiter.api.Assertions.assertEquals(true, second.get("duplicate").asBoolean());
    org.junit.jupiter.api.Assertions.assertEquals(
        first.get("documentId").asText(), second.get("documentId").asText());
  }

  @Test
  void versioningSupersedesAndCopiesGrants() throws Exception {
    JsonNode v1 = upload(aliceToken, "v.txt", "version one of this document".getBytes(StandardCharsets.UTF_8), "INTERNAL");
    awaitJob(aliceToken, v1.get("jobId").asText());
    String v1Id = v1.get("documentId").asText();
    grants.save(new DocumentPermission(v1Id, PrincipalType.USER, bobId));

    MvcResult r = mvc.perform(multipart("/api/v1/documents/" + v1Id + "/versions")
            .file(new MockMultipartFile("file", "v2.txt", "text/plain",
                "version two content of this document".getBytes(StandardCharsets.UTF_8)))
            .header("Authorization", "Bearer " + aliceToken))
        .andExpect(status().isAccepted()).andReturn();
    JsonNode v2 = mapper.readTree(r.getResponse().getContentAsString());
    awaitJob(aliceToken, v2.get("jobId").asText());

    mvc.perform(get("/api/v1/documents/" + v1Id)
            .header("Authorization", "Bearer " + aliceToken))
        .andExpect(jsonPath("$.status").value("SUPERSEDED"));
    mvc.perform(get("/api/v1/documents/" + v2.get("documentId").asText())
            .header("Authorization", "Bearer " + aliceToken))
        .andExpect(jsonPath("$.status").value("CURRENT"))
        .andExpect(jsonPath("$.version").value(2));
    // grant followed the family: bob reads v2
    mvc.perform(get("/api/v1/documents/" + v2.get("documentId").asText())
            .header("Authorization", "Bearer " + bobToken))
        .andExpect(status().isOk());
    // family listing shows both
    mvc.perform(get("/api/v1/documents/" + v2.get("documentId").asText() + "/versions")
            .header("Authorization", "Bearer " + aliceToken))
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  void crossUserIsolation() throws Exception {
    JsonNode up = upload(aliceToken, "priv.txt", "alice private notes go here".getBytes(StandardCharsets.UTF_8), "INTERNAL");
    String docId = up.get("documentId").asText();
    String jobId = up.get("jobId").asText();
    awaitJob(aliceToken, jobId);

    mvc.perform(get("/api/v1/documents/" + docId).header("Authorization", "Bearer " + bobToken))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/ingestion/jobs/" + jobId).header("Authorization", "Bearer " + bobToken))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/documents/" + docId + "/chunks")
            .header("Authorization", "Bearer " + bobToken))
        .andExpect(status().isNotFound());
    mvc.perform(post("/api/v1/ingestion/jobs/" + jobId + "/retry")
            .header("Authorization", "Bearer " + bobToken))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/documents").header("Authorization", "Bearer " + bobToken))
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void corruptPdfFailsJobSafely() throws Exception {
    JsonNode up = upload(aliceToken, "bad.pdf",
        "%PDF-1.4 not really a pdf".getBytes(StandardCharsets.UTF_8), "INTERNAL");
    JsonNode job = awaitJob(aliceToken, up.get("jobId").asText());
    org.junit.jupiter.api.Assertions.assertEquals("FAILED", job.get("status").asText());
    org.junit.jupiter.api.Assertions.assertEquals("PARSE_ERROR", job.get("errorCode").asText());
    // safe message: no parser internals
    org.junit.jupiter.api.Assertions.assertFalse(
        job.get("errorMessage").asText().toLowerCase().contains("pdfbox"));
  }

  @Test
  void scannedPdfWithoutOcrFailsThenRetrySucceeds() throws Exception {
    JsonNode up = upload(aliceToken, "scan.pdf", blankPdf(), "INTERNAL");
    JsonNode failed = awaitJob(aliceToken, up.get("jobId").asText());
    org.junit.jupiter.api.Assertions.assertEquals("FAILED", failed.get("status").asText());
    org.junit.jupiter.api.Assertions.assertEquals("OCR_UNAVAILABLE", failed.get("errorCode").asText());

    when(ocr.available()).thenReturn(true);
    when(ocr.ocrImages(anyList(), eq("eng"))).thenReturn(java.util.List.of("scanned hello world"));
    MvcResult r = mvc.perform(post("/api/v1/ingestion/jobs/" + up.get("jobId").asText() + "/retry")
            .header("Authorization", "Bearer " + aliceToken))
        .andExpect(status().isAccepted()).andReturn();
    JsonNode retried = awaitJob(aliceToken,
        mapper.readTree(r.getResponse().getContentAsString()).get("id").asText());
    org.junit.jupiter.api.Assertions.assertEquals("READY", retried.get("status").asText());

    MvcResult chunks = mvc.perform(get("/api/v1/documents/" + up.get("documentId").asText() + "/chunks")
            .header("Authorization", "Bearer " + aliceToken))
        .andExpect(status().isOk()).andReturn();
    org.junit.jupiter.api.Assertions.assertTrue(
        chunks.getResponse().getContentAsString().contains("scanned hello world"));
  }

  private byte[] blankPdf() throws Exception {
    try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
      doc.addPage(new org.apache.pdfbox.pdmodel.PDPage());
      doc.save(out);
      return out.toByteArray();
    }
  }

  @Test
  void retryOnlyFailed() throws Exception {
    JsonNode up = upload(aliceToken, "r.txt", "retry me after this full sentence".getBytes(StandardCharsets.UTF_8), "INTERNAL");
    awaitJob(aliceToken, up.get("jobId").asText());
    mvc.perform(post("/api/v1/ingestion/jobs/" + up.get("jobId").asText() + "/retry")
            .header("Authorization", "Bearer " + aliceToken))
        .andExpect(status().isConflict());
  }

  @Test
  void docxPptxMdIngest() throws Exception {
    // DOCX
    byte[] docx;
    try (org.apache.poi.xwpf.usermodel.XWPFDocument d = new org.apache.poi.xwpf.usermodel.XWPFDocument();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
      d.createParagraph().createRun().setText("Docx body content here in full.");
      d.write(out);
      docx = out.toByteArray();
    }
    JsonNode d1 = upload(aliceToken, "r.docx", docx, "INTERNAL");
    JsonNode j1 = awaitJob(aliceToken, d1.get("jobId").asText());
    org.junit.jupiter.api.Assertions.assertEquals("READY", j1.get("status").asText(), j1::toString);

    // PPTX
    byte[] pptx;
    try (org.apache.poi.xslf.usermodel.XMLSlideShow p = new org.apache.poi.xslf.usermodel.XMLSlideShow();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
      p.createSlide().createTextBox().setText("Pptx slide content here in full.");
      p.write(out);
      pptx = out.toByteArray();
    }
    JsonNode d2 = upload(aliceToken, "d.pptx", pptx, "INTERNAL");
    org.junit.jupiter.api.Assertions.assertEquals("READY", awaitJob(aliceToken, d2.get("jobId").asText()).get("status").asText());

    // Markdown
    JsonNode d3 = upload(aliceToken, "n.md",
        "# Brief\n\nMarkdown body text.\n".getBytes(StandardCharsets.UTF_8), "INTERNAL");
    JsonNode j3 = awaitJob(aliceToken, d3.get("jobId").asText());
    org.junit.jupiter.api.Assertions.assertEquals("READY", j3.get("status").asText());

    MvcResult chunks = mvc.perform(get("/api/v1/documents/" + d3.get("documentId").asText() + "/chunks")
            .header("Authorization", "Bearer " + aliceToken))
        .andExpect(status().isOk()).andReturn();
    org.junit.jupiter.api.Assertions.assertTrue(
        chunks.getResponse().getContentAsString().contains("Brief"));
  }
}
