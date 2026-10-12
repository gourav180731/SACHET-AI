package ai.sachet.ingest;

import ai.sachet.domain.Classification;
import ai.sachet.domain.Document;
import ai.sachet.domain.DocumentPermission;
import ai.sachet.domain.IngestionJob;
import ai.sachet.domain.JobStatus;
import ai.sachet.domain.Role;
import ai.sachet.domain.User;
import ai.sachet.repo.DocumentChunkRepository;
import ai.sachet.repo.DocumentPermissionRepository;
import ai.sachet.repo.DocumentRepository;
import ai.sachet.repo.IngestionJobRepository;
import ai.sachet.repo.UserRepository;
import ai.sachet.security.SachetPrincipal;
import ai.sachet.service.AuditService;
import ai.sachet.service.DocumentAccessService;
import ai.sachet.storage.LocalStorageService;
import ai.sachet.web.JobStateException;
import ai.sachet.web.NotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Document intake (Phase 3). Synchronous part does cheap checks only
 * (auth, classification ceiling, size, type, magic bytes, duplicates) and
 * returns 202; heavy work (scan, parse, OCR, chunk) runs async with full
 * stage tracking. Nothing is ever marked READY on failure.
 */
@Service
public class IngestionService {

  private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

  private final UserRepository users;
  private final DocumentRepository documents;
  private final DocumentPermissionRepository grants;
  private final DocumentChunkRepository chunks;
  private final IngestionJobRepository jobs;
  private final DocumentAccessService access;
  private final FileValidationService validation;
  private final LocalStorageService storage;
  private final AuditService audit;
  private final IngestionWorker worker;
  private final ai.sachet.config.SachetProperties props;

  public IngestionService(UserRepository users, DocumentRepository documents,
      DocumentPermissionRepository grants, DocumentChunkRepository chunks,
      IngestionJobRepository jobs, DocumentAccessService access,
      FileValidationService validation, LocalStorageService storage,
      AuditService audit, IngestionWorker worker,
      ai.sachet.config.SachetProperties props) {
    this.users = users;
    this.documents = documents;
    this.grants = grants;
    this.chunks = chunks;
    this.jobs = jobs;
    this.access = access;
    this.validation = validation;
    this.storage = storage;
    this.audit = audit;
    this.worker = worker;
    this.props = props;
  }

  public record SubmitResult(Document document, IngestionJob job, boolean duplicate) {
  }

  /** Active DB user behind the token (tokens can outlive deactivation). */
  User requireActiveUser(SachetPrincipal principal) {
    return users.findById(principal.getUserId())
        .filter(User::isActive)
        .orElseThrow(() -> new AccessDeniedException("Forbidden."));
  }

  static Classification parseClassification(String raw) {
    if (raw == null || raw.isBlank()) {
      return Classification.INTERNAL;
    }
    try {
      return Classification.valueOf(raw.strip().toUpperCase(java.util.Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw ValidationException.failed("Unknown classification.");
    }
  }

  @Transactional
  public SubmitResult submit(SachetPrincipal principal, MultipartFile file,
      String title, String classificationRaw, String department) {
    User user = requireActiveUser(principal);
    Classification classification = parseClassification(classificationRaw);
    if (!classification.accessibleTo(user.getClearance())) {
      throw new AccessDeniedException("Forbidden."); // above caller's clearance
    }
    if (file == null || file.isEmpty()) {
      throw ValidationException.failed("No file uploaded.");
    }
    if (file.getSize() > props.getIngestionMaxFileBytes()) {
      throw ValidationException.tooBig("File exceeds the configured size limit.");
    }
    LocalStorageService.StoredBlob blob = storeQuietly(file);
    FileValidationService.ValidatedFile valid;
    try {
      valid = validation.validate(storagePath(blob.key()), file.getOriginalFilename(), file.getSize());
    } catch (ValidationException e) {
      deleteQuietly(blob.key());
      throw e;
    }
    var dup = documents.findBySha256AndOwnerId(valid.sha256(), user.getId());
    if (dup.isPresent()) {
      deleteQuietly(blob.key());
      IngestionJob latest = jobs.findTopByDocumentIdOrderByCreatedAtDesc(dup.get().getId()).orElse(null);
      return new SubmitResult(dup.get(), latest, true);
    }
    Document doc = new Document(
        title == null || title.isBlank() ? file.getOriginalFilename() : title.strip(),
        user.getId(), classification);
    doc.setDepartment(department == null || department.isBlank() ? user.getDepartment() : department.strip());
    doc.setSha256(valid.sha256());
    doc.setMimeType(valid.mimeType());
    doc.setSourceFilename(file.getOriginalFilename());
    documents.save(doc);
    IngestionJob job = jobs.save(new IngestionJob(doc.getId(), user.getId(), blob.key()));
    audit.append(user.getId(), "document.uploaded", List.of(doc.getId()),
        classification.name(), "", "");
    dispatch(job);
    return new SubmitResult(doc, job, false);
  }

  /** New version of an existing document: owner or ADMIN only. */
  @Transactional
  public SubmitResult newVersion(SachetPrincipal principal, String documentId, MultipartFile file) {
    User user = requireActiveUser(principal);
    Document current = documents.findById(documentId).orElseThrow(NotFoundException::new);
    if (!access.canRead(user.getId(), current.getId())) {
      throw new NotFoundException(); // existence hiding
    }
    boolean admin = isAdmin(user);
    if (!user.getId().equals(current.getOwnerId()) && !admin) {
      throw new AccessDeniedException("Forbidden.");
    }
    if (file == null || file.isEmpty()) {
      throw ValidationException.failed("No file uploaded.");
    }
    if (file.getSize() > props.getIngestionMaxFileBytes()) {
      throw ValidationException.tooBig("File exceeds the configured size limit.");
    }
    LocalStorageService.StoredBlob blob = storeQuietly(file);
    FileValidationService.ValidatedFile valid;
    try {
      valid = validation.validate(storagePath(blob.key()), file.getOriginalFilename(), file.getSize());
    } catch (ValidationException e) {
      deleteQuietly(blob.key());
      throw e;
    }
    if (valid.sha256().equals(current.getSha256())) {
      deleteQuietly(blob.key());
      IngestionJob latest = jobs.findTopByDocumentIdOrderByCreatedAtDesc(current.getId()).orElse(null);
      return new SubmitResult(current, latest, true);
    }
    current.setStatus("SUPERSEDED");
    documents.save(current);
    Document next = new Document(current.getTitle(), current.getOwnerId(), current.getClassification());
    next.setFamilyId(current.getFamilyId());
    next.setVersion(current.getVersion() + 1);
    next.setDepartment(current.getDepartment());
    next.setSha256(valid.sha256());
    next.setMimeType(valid.mimeType());
    next.setSourceFilename(file.getOriginalFilename());
    documents.save(next);
    // Grants follow the family: the new version inherits the old version's ACLs.
    for (DocumentPermission g : grants.findByDocumentId(current.getId())) {
      grants.save(new DocumentPermission(next.getId(), g.getPrincipalType(), g.getPrincipalId()));
    }
    IngestionJob job = jobs.save(new IngestionJob(next.getId(), user.getId(), blob.key()));
    audit.append(user.getId(), "document.versioned", List.of(next.getId()),
        next.getClassification().name(), "", "");
    dispatch(job);
    return new SubmitResult(next, job, false);
  }

  @Transactional(readOnly = true)
  public Page<Document> list(SachetPrincipal principal, Pageable pageable) {
    User user = requireActiveUser(principal);
    List<String> bands = Arrays.stream(Classification.values())
        .filter(c -> c.accessibleTo(user.getClearance()))
        .map(Enum::name).toList();
    List<String> roles = user.getRoles().stream().map(Role::getName).toList();
    if (roles.isEmpty()) {
      roles = List.of("");
    }
    return documents.findReadable(user.getId(), roles, user.getDepartment(), bands, pageable);
  }

  @Transactional(readOnly = true)
  public Document get(SachetPrincipal principal, String documentId) {
    if (!access.canRead(principal.getUserId(), documentId)) {
      throw new NotFoundException();
    }
    return documents.findById(documentId).orElseThrow(NotFoundException::new);
  }

  @Transactional(readOnly = true)
  public List<Document> versions(SachetPrincipal principal, String documentId) {
    Document doc = get(principal, documentId);
    return documents.findByFamilyIdOrderByVersionAsc(doc.getFamilyId());
  }

  @Transactional(readOnly = true)
  public Page<ai.sachet.domain.DocumentChunk> chunks(SachetPrincipal principal,
      String documentId, Pageable pageable) {
    get(principal, documentId); // 404 when missing or no access
    return chunks.findByDocumentIdOrderByChunkIndexAsc(documentId, pageable);
  }

  /** Authorized download of the original blob via the latest job's blob key. */
  @Transactional
  public DocumentDownload download(SachetPrincipal principal, String documentId) {
    Document doc = get(principal, documentId); // 404 when missing or no access
    IngestionJob job = jobs.findTopByDocumentIdOrderByCreatedAtDesc(documentId)
        .filter(j -> j.getBlobKey() != null && !j.getBlobKey().isBlank())
        .orElseThrow(NotFoundException::new);
    try {
      java.nio.file.Path path = storage.resolve(job.getBlobKey());
      if (!java.nio.file.Files.exists(path)) {
        throw new NotFoundException();
      }
      audit.append(principal.getUserId(), "document.downloaded", List.of(documentId),
          doc.getClassification().name(), "", "");
      return new DocumentDownload(doc, path);
    } catch (IOException e) {
      throw new NotFoundException();
    }
  }

  public record DocumentDownload(Document document, java.nio.file.Path path) {
  }

  @Transactional(readOnly = true)
  public IngestionJob job(SachetPrincipal principal, String jobId) {
    IngestionJob job = jobs.findById(jobId).orElseThrow(NotFoundException::new);
    User user = requireActiveUser(principal);
    if (!job.getCreatedBy().equals(user.getId()) && !isAdmin(user)) {
      throw new NotFoundException(); // jobs are private to owner/admin
    }
    return job;
  }

  @Transactional
  public IngestionJob retry(SachetPrincipal principal, String jobId) {
    IngestionJob job = job(principal, jobId);
    if (job.getStatus() != JobStatus.FAILED) {
      throw new JobStateException("Only FAILED jobs can be retried.");
    }
    try {
      storage.resolve(job.getBlobKey());
    } catch (IOException e) {
      job.fail(IngestionJob.Errors.INTERNAL, "Source missing; please re-upload.");
      jobs.save(job);
      throw new JobStateException("Source missing; please re-upload.");
    }
    job.setAttempts(job.getAttempts() + 1);
    job.setStatus(JobStatus.QUEUED);
    job.setProgress(0);
    jobs.save(job);
    audit.append(principal.getUserId(), "job.retry", List.of(job.getDocumentId()), null, "", "");
    dispatch(job);
    return job;
  }

  private void dispatch(IngestionJob job) {
    // The worker MUST see committed rows: hand off only after this transaction
    // commits. Dispatching earlier races the commit and strands jobs in QUEUED.
    org.springframework.transaction.support.TransactionSynchronizationManager
        .registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
          @Override
          public void afterCommit() {
            try {
              worker.process(job.getId());
            } catch (RejectedExecutionException e) {
              log.warn("ingestion queue full");
              job.fail(IngestionJob.Errors.INTERNAL, "Server busy; please retry.");
              jobs.save(job);
            }
          }
        });
  }

  private LocalStorageService.StoredBlob storeQuietly(MultipartFile file) {
    try (InputStream in = file.getInputStream()) {
      return storage.store(file.getOriginalFilename(), in, file.getSize());
    } catch (IOException e) {
      throw ValidationException.failed("Could not store upload.");
    }
  }

  private java.nio.file.Path storagePath(String key) {
    try {
      return storage.resolve(key);
    } catch (IOException e) {
      throw ValidationException.failed("Could not read upload.");
    }
  }

  private void deleteQuietly(String key) {
    try {
      Files.deleteIfExists(storage.resolve(key));
    } catch (IOException e) {
      log.warn("stale blob cleanup failed: {}", e.getClass().getSimpleName());
    }
  }

  static boolean isAdmin(User user) {
    return user.getRoles().stream().map(Role::getName).collect(Collectors.toSet()).contains("ADMIN");
  }

  /** Delete a document and all its versions/chunks/jobs/blobs. Owner or ADMIN only. */
  @Transactional
  public void deleteDocument(SachetPrincipal principal, String documentId) {
    User user = requireActiveUser(principal);
    Document doc = documents.findById(documentId).orElseThrow(NotFoundException::new);

    // Check ownership or admin
    boolean admin = isAdmin(user);
    if (!user.getId().equals(doc.getOwnerId()) && !admin) {
      throw new AccessDeniedException("Forbidden.");
    }

    // Find all versions in the family
    List<Document> family = documents.findByFamilyIdOrderByVersionAsc(doc.getFamilyId());
    List<String> allDocIds = family.stream().map(Document::getId).toList();

    // Delete chunks
    chunks.deleteByDocumentIdIn(allDocIds);

    // Delete jobs and their blobs
    for (String dId : allDocIds) {
      List<IngestionJob> docJobs = jobs.findByDocumentIdOrderByCreatedAtDesc(dId);
      for (IngestionJob j : docJobs) {
        if (j.getBlobKey() != null && !j.getBlobKey().isBlank()) {
          deleteQuietly(j.getBlobKey());
        }
      }
      jobs.deleteByDocumentId(dId);
    }

    // Delete permissions
    grants.deleteByDocumentIdIn(allDocIds);

    // Delete documents
    documents.deleteAll(family);

    audit.append(user.getId(), "document.deleted", allDocIds,
        doc.getClassification().name(), "", "");
    log.info("User {} deleted document family {} ({} versions)", user.getId(), doc.getFamilyId(), family.size());
  }
}
