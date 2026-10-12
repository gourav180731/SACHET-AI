package ai.sachet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Document metadata (Phase 3: registry + ingestion provenance; vectors land Phase 4).
 * Content itself is never embedded in API responses or logs.
 * Versions: rows sharing a {@code familyId} are versions of one logical document;
 * exactly one row per family is CURRENT, older rows become SUPERSEDED.
 */
@Entity
@Table(name = "documents")
public class Document {

  @Id
  @Column(length = 36)
  private String id;

  /** Logical document identity shared across versions. */
  @Column(name = "family_id", nullable = false, length = 36)
  private String familyId;

  @Column(nullable = false, length = 512)
  private String title;

  @Column(name = "owner_id", nullable = false, length = 36)
  private String ownerId;

  @Column(length = 128)
  private String department;

  @Column(nullable = false, length = 32)
  private String classification = Classification.INTERNAL.name();

  /** CURRENT, DRAFT, SUPERSEDED, ARCHIVED, EXPIRED. Retrieval prefers CURRENT. */
  @Column(nullable = false, length = 32)
  private String status = "CURRENT";

  @Column(nullable = false)
  private int version = 1;

  /** SHA-256 of the source blob: integrity, duplicate detection, version identity. */
  @Column(name = "sha256", length = 64)
  private String sha256;

  @Column(name = "mime_type", length = 128)
  private String mimeType;

  @Column(name = "source_filename", length = 512)
  private String sourceFilename;

  @Column(name = "page_count", nullable = false)
  private int pageCount = 0;

  @Column(name = "chunk_count", nullable = false)
  private int chunkCount = 0;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected Document() {
  }

  public Document(String title, String ownerId, Classification classification) {
    this.id = UUID.randomUUID().toString();
    this.familyId = UUID.randomUUID().toString();
    this.title = title;
    this.ownerId = ownerId;
    this.classification = classification.name();
  }

  @PrePersist
  void onCreate() {
    this.createdAt = Instant.now();
    if (this.familyId == null) {
      this.familyId = this.id;
    }
  }

  public String getId() { return id; }
  public String getFamilyId() { return familyId; }
  public void setFamilyId(String familyId) { this.familyId = familyId; }
  public String getTitle() { return title; }
  public String getOwnerId() { return ownerId; }
  public Classification getClassification() { return Classification.valueOf(classification); }
  public void setClassification(Classification c) { this.classification = c.name(); }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getDepartment() { return department; }
  public void setDepartment(String department) { this.department = department; }
  public int getVersion() { return version; }
  public void setVersion(int version) { this.version = version; }
  public String getSha256() { return sha256; }
  public void setSha256(String sha256) { this.sha256 = sha256; }
  public String getMimeType() { return mimeType; }
  public void setMimeType(String mimeType) { this.mimeType = mimeType; }
  public String getSourceFilename() { return sourceFilename; }
  public void setSourceFilename(String sourceFilename) { this.sourceFilename = sourceFilename; }
  public int getPageCount() { return pageCount; }
  public void setPageCount(int pageCount) { this.pageCount = pageCount; }
  public int getChunkCount() { return chunkCount; }
  public void setChunkCount(int chunkCount) { this.chunkCount = chunkCount; }
  public Instant getCreatedAt() { return createdAt; }
}
