package ai.sachet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Async ingestion job tracking. One job per upload/version attempt.
 * {@code errorCode} is a stable machine code (safe for clients);
 * {@code errorMessage} is a generic human sentence (never internals).
 */
@Entity
@Table(name = "ingestion_jobs")
public class IngestionJob {

  /** Stable, client-safe failure codes. */
  public static final class Errors {
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String POLICY_DENIED = "POLICY_DENIED";
    public static final String MALWARE_SUSPECT = "MALWARE_SUSPECT";
    public static final String SCAN_UNAVAILABLE = "SCAN_UNAVAILABLE";
    public static final String PARSE_ERROR = "PARSE_ERROR";
    public static final String OCR_REQUIRED = "OCR_REQUIRED";
    public static final String OCR_UNAVAILABLE = "OCR_UNAVAILABLE";
    public static final String EMBEDDING_FAILED = "EMBEDDING_FAILED";
    public static final String LIMIT_EXCEEDED = "LIMIT_EXCEEDED";
    public static final String INTERNAL = "INTERNAL";
    private Errors() {
    }
  }

  @Id
  @Column(length = 36)
  private String id;

  @Column(name = "document_id", nullable = false, length = 36)
  private String documentId;

  @Column(nullable = false, length = 16)
  private String status = JobStatus.QUEUED.name();

  @Column(nullable = false)
  private int progress = 0;

  @Column(name = "error_code", length = 32)
  private String errorCode;

  @Column(name = "error_message", length = 512)
  private String errorMessage;

  /** Storage key of the uploaded blob so FAILED jobs can be retried. */
  @Column(name = "blob_key", length = 512)
  private String blobKey;

  @Column(name = "created_by", nullable = false, length = 36)
  private String createdBy;

  @Column(nullable = false)
  private int attempts = 1;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected IngestionJob() {
  }

  public IngestionJob(String documentId, String createdBy, String blobKey) {
    this.id = UUID.randomUUID().toString();
    this.documentId = documentId;
    this.createdBy = createdBy;
    this.blobKey = blobKey;
  }

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    this.updatedAt = Instant.now();
  }

  public String getId() { return id; }
  public String getDocumentId() { return documentId; }
  public JobStatus getStatus() { return JobStatus.valueOf(status); }
  public void setStatus(JobStatus s) { this.status = s.name(); }
  public int getProgress() { return progress; }
  public void setProgress(int progress) { this.progress = progress; }
  public String getErrorCode() { return errorCode; }
  public String getErrorMessage() { return errorMessage; }
  public void fail(String code, String message) {
    this.status = JobStatus.FAILED.name();
    this.errorCode = code;
    this.errorMessage = message;
    this.progress = 100;
  }
  public String getBlobKey() { return blobKey; }
  public String getCreatedBy() { return createdBy; }
  public int getAttempts() { return attempts; }
  public void setAttempts(int attempts) { this.attempts = attempts; }
  public Instant getCreatedAt() { return createdAt; }
}
