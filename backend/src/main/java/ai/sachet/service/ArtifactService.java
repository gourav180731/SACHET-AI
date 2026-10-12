package ai.sachet.service;

import ai.sachet.config.SachetProperties;
import ai.sachet.domain.Classification;
import ai.sachet.domain.Document;
import ai.sachet.domain.GeneratedArtifact;
import ai.sachet.domain.User;
import ai.sachet.rag.RagService;
import ai.sachet.repo.DocumentRepository;
import ai.sachet.repo.GeneratedArtifactRepository;
import ai.sachet.repo.UserRepository;
import ai.sachet.security.SachetPrincipal;
import ai.sachet.storage.LocalStorageService;
import ai.sachet.web.NotFoundException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;

/**
 * Phase 6 artifact pipeline: grounded generation (via RagService) -> validation
 * -> secure storage -> metadata + audit. Never claims success before the bytes
 * are stored and the row committed.
 */
@Service
public class ArtifactService {

  private static final Logger log = LoggerFactory.getLogger(ArtifactService.class);

  public static final int MAX_SLIDES = 30;
  public static final int MAX_ARTIFACTS_PER_USER = 100;

  private final RagService rag;
  private final GeneratedArtifactRepository artifacts;
  private final DocumentRepository documents;
  private final UserRepository users;
  private final DocumentAccessService access;
  private final LocalStorageService storage;
  private final AuditService audit;
  private final SachetProperties props;

  public ArtifactService(RagService rag, GeneratedArtifactRepository artifacts,
      DocumentRepository documents, UserRepository users, DocumentAccessService access,
      LocalStorageService storage, AuditService audit, SachetProperties props) {
    this.rag = rag;
    this.artifacts = artifacts;
    this.documents = documents;
    this.users = users;
    this.access = access;
    this.storage = storage;
    this.audit = audit;
    this.props = props;
  }

  /** True if the user may see this artifact: active, clearance covers its
   *  classification, and (owner OR admin role OR can read every source doc). */
  public boolean canAccess(User user, GeneratedArtifact a) {
    if (user == null || !user.isActive()) {
      return false;
    }
    Classification clearance;
    try {
      clearance = user.getClearance();
    } catch (IllegalArgumentException e) {
      return false;
    }
    if (!Classification.valueOf(a.getClassification()).accessibleTo(clearance)) {
      return false;
    }
    if (user.getId().equals(a.getOwnerId())) {
      return true;
    }
    boolean admin = user.getRoles().stream()
        .anyMatch(r -> r.getName().equals("ADMIN") || r.getName().equals("SECURITY_ADMIN"));
    if (admin) {
      return true;
    }
    for (String src : sourceIds(a)) {
      if (!access.canRead(user.getId(), src)) {
        return false;
      }
    }
    return !sourceIds(a).isEmpty();
  }

  private List<String> sourceIds(GeneratedArtifact a) {
    if (a.getSourceDocIds() == null || a.getSourceDocIds().isBlank()) {
      return List.of();
    }
    return List.of(a.getSourceDocIds().split(","));
  }

  private User requireActive(SachetPrincipal p) {
    return users.findById(p.getUserId()).filter(User::isActive)
        .orElseThrow(() -> new AccessDeniedException("Forbidden."));
  }

  public GeneratedArtifact getAuthorized(SachetPrincipal p, String id) {
    User user = requireActive(p);
    GeneratedArtifact a = artifacts.findById(id).orElseThrow(() -> new NotFoundException());
    if (!canAccess(user, a)) {
      throw new NotFoundException(); // existence hiding
    }
    return a;
  }

  public org.springframework.data.domain.Page<GeneratedArtifact> listVisible(SachetPrincipal p, int page, int size) {
    User user = requireActive(p);
    int safeSize = Math.min(Math.max(size, 1), 100);
    var pageable = org.springframework.data.domain.PageRequest.of(Math.max(page, 0), safeSize);
    boolean admin = user.getRoles().stream()
        .anyMatch(r -> r.getName().equals("ADMIN") || r.getName().equals("SECURITY_ADMIN"));
    if (admin) {
      return artifacts.findAll(org.springframework.data.domain.Sort.by("createdAt").descending())
          .stream().collect(Collectors.collectingAndThen(Collectors.toList(),
              l -> new org.springframework.data.domain.PageImpl<>(l.subList(
                  Math.min((int)pageable.getOffset(), l.size()),
                  Math.min((int)(pageable.getOffset() + safeSize), l.size())), pageable, l.size())));
    }
    // Non-admin: own artifacts only — never guess at someone else's.
    return artifacts.findByOwnerIdOrderByCreatedAtDesc(user.getId(), pageable);
  }

  @Transactional
  public void delete(SachetPrincipal p, String id) {
    User user = requireActive(p);
    GeneratedArtifact a = artifacts.findById(id).orElseThrow(() -> new NotFoundException());
    boolean owner = user.getId().equals(a.getOwnerId());
    boolean admin = user.getRoles().stream()
        .anyMatch(r -> r.getName().equals("ADMIN") || r.getName().equals("SECURITY_ADMIN"));
    if (!owner && !admin) {
      throw new NotFoundException();
    }
    try {
      Files.deleteIfExists(storage.resolve(a.getBlobKey()));
    } catch (IOException e) {
      log.warn("artifact blob delete failed id={}", a.getId());
    }
    artifacts.delete(a);
    audit.append(user.getId(), "artifact.deleted", List.of(), a.getClassification(), "", "");
  }

  @Transactional
  public GeneratedArtifact generateDocx(SachetPrincipal p, String title, String topic,
      List<String> docIds, Integer topK) throws IOException {
    User user = requireActive(p);
    if (artifacts.countByOwnerId(user.getId()) >= MAX_ARTIFACTS_PER_USER) {
      throw new IllegalArgumentException("Artifact quota exceeded.");
    }
    List<EvidenceChunk> chunks = gather(p, topic, docIds, topK);
    String classification = strictestBand(docIds);
    byte[] bytes = buildDocx(title, topic, chunks);
    String filename = sanitize(title) + ".docx";
    return persist(user, p, title, "DOCX", classification, docIds, filename, bytes,
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
  }

  @Transactional
  public GeneratedArtifact generatePptx(SachetPrincipal p, String title, String topic,
      List<String> docIds, Integer topK, Integer maxSlides) throws IOException {
    User user = requireActive(p);
    if (artifacts.countByOwnerId(user.getId()) >= MAX_ARTIFACTS_PER_USER) {
      throw new IllegalArgumentException("Artifact quota exceeded.");
    }
    int slides = Math.min(Math.max(maxSlides == null ? 10 : maxSlides, 1), MAX_SLIDES);
    List<EvidenceChunk> chunks = gather(p, topic, docIds, topK);
    String classification = strictestBand(docIds);
    byte[] bytes = buildPptx(title, topic, chunks, slides);
    String filename = sanitize(title) + ".pptx";
    return persist(user, p, title, "PPTX", classification, docIds, filename, bytes,
        "application/vnd.openxmlformats-officedocument.presentationml.presentation");
  }

  private List<EvidenceChunk> gather(SachetPrincipal p, String topic, List<String> docIds, Integer topK) {
    if (topic == null || topic.isBlank()) {
      throw new IllegalArgumentException("Topic is required.");
    }
    RagService.RagResult result = rag.search(p, topic, topK, docIds);
    List<EvidenceChunk> out = new ArrayList<>();
    for (RagService.ResultItem r : result.results()) {
      out.add(new EvidenceChunk(r.documentTitle(), r.documentVersion(), r.page(), r.section(),
          r.documentId()));
    }
    if (out.isEmpty()) {
      throw new IllegalArgumentException("I could not find sufficient supporting information in the authorized documents.");
    }
    return out;
  }

  private record EvidenceChunk(String docTitle, int version, int page, String section, String docId) {}

  private String strictestBand(List<String> docIds) {
    Classification acc = Classification.PUBLIC;
    if (docIds != null) {
      for (String id : docIds) {
        Document d = documents.findById(id).orElse(null);
        if (d != null && d.getClassification().rank() > acc.rank()) {
          acc = d.getClassification();
        }
      }
    }
    return acc.name();
  }

  private byte[] buildDocx(String title, String topic, List<EvidenceChunk> chunks) throws IOException {
    try (XWPFDocument doc = new XWPFDocument()) {
      XWPFParagraph h = doc.createParagraph();
      h.createRun().setText(title);
      h.createRun().addBreak();
      doc.createParagraph().createRun().setText("Topic: " + topic);
      doc.createParagraph().createRun().setText("Generated: " + Instant.now());
      doc.createParagraph().createRun().setText("");
      for (EvidenceChunk c : chunks) {
        XWPFParagraph p = doc.createParagraph();
        p.createRun().setText(c.docTitle() + " v" + c.version() + " p" + c.page());
        doc.createParagraph().createRun().setText(c.section() == null ? "" : c.section());
      }
      StringBuilder sources = new StringBuilder("Sources: ");
      chunks.stream().map(EvidenceChunk::docTitle).distinct().limit(10)
          .forEach(t -> sources.append(t).append("; "));
      doc.createParagraph().createRun().setText(sources.toString());
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      doc.write(bos);
      return bos.toByteArray();
    }
  }

  private byte[] buildPptx(String title, String topic, List<EvidenceChunk> chunks, int maxSlides) throws IOException {
    try (XMLSlideShow show = new XMLSlideShow()) {
      XSLFSlide titleSlide = show.createSlide();
      XSLFTextBox tb = titleSlide.createTextBox();
      tb.setText(title + "\n" + topic);

      int count = Math.min(chunks.size(), Math.max(maxSlides - 1, 0));
      for (int i = 0; i < count; i++) {
        EvidenceChunk c = chunks.get(i);
        XSLFSlide s = show.createSlide();
        XSLFTextBox t = s.createTextBox();
        t.setText(c.docTitle() + " p" + c.page() + (c.section() != null ? " — " + c.section() : ""));
      }
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      show.write(bos);
      return bos.toByteArray();
    }
  }

  private GeneratedArtifact persist(User user, SachetPrincipal p, String title, String type,
      String classification, List<String> docIds, String filename, byte[] bytes, String mime) throws IOException {
    try (InputStream in = new ByteArrayInputStream(bytes)) {
      LocalStorageService.StoredBlob blob = storage.store(filename, in, bytes.length);
      GeneratedArtifact a = new GeneratedArtifact(user.getId(), title, type, classification);
      a.setSourceDocIds(docIds == null ? "" : String.join(",", docIds));
      a.setBlobKey(blob.key());
      a.setMimeType(mime);
      a.setSize(blob.size());
      a.setSha256(blob.sha256());
      artifacts.save(a);
      audit.append(user.getId(), "artifact.generated",
          docIds == null ? List.of() : docIds, classification, "", "");
      return a;
    }
  }

  private String sanitize(String title) {
    if (title == null || title.isBlank()) {
      return "artifact";
    }
    String s = title.replaceAll("[^A-Za-z0-9._-]", "_");
    return s.length() > 80 ? s.substring(0, 80) : s;
  }

  /** Public view of an artifact for API responses. */
  public record ArtifactView(String id, String ownerId, String title, String type,
      String classification, List<String> sourceDocIds, String mimeType, long size,
      String sha256, Instant createdAt) {
    public static ArtifactView from(GeneratedArtifact a) {
      return new ArtifactView(a.getId(), a.getOwnerId(), a.getTitle(), a.getType(),
          a.getClassification(),
          a.getSourceDocIds() == null || a.getSourceDocIds().isBlank() ? List.of()
              : List.of(a.getSourceDocIds().split(",")),
          a.getMimeType(), a.getSize(), a.getSha256(), a.getCreatedAt());
    }
  }
}
