package ai.sachet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A generated studio artifact (DOCX report / PPTX briefing). Metadata only in
 * the DB; bytes live in LocalStorageService under a server-generated key.
 * Classification is inherited as the strictest band of its source documents.
 */
@Entity
@Table(name = "generated_artifacts")
public class GeneratedArtifact {

  @Id
  @Column(length = 36)
  private String id;

  @Column(name = "owner_id", nullable = false, length = 36)
  private String ownerId;

  @Column(nullable = false, length = 512)
  private String title;

  @Column(nullable = false, length = 16)
  private String type; // DOCX | PPTX | REPORT

  @Column(nullable = false, length = 32)
  private String classification = Classification.INTERNAL.name();

  /** Comma-separated source document ids used for grounding. */
  @Column(name = "source_doc_ids", length = 4000)
  private String sourceDocIds = "";

  @Column(name = "blob_key", nullable = false, length = 512)
  private String blobKey;

  @Column(name = "mime_type", length = 128)
  private String mimeType;

  @Column(nullable = false)
  private long size = 0;

  @Column(length = 64)
  private String sha256;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected GeneratedArtifact() {
  }

  public GeneratedArtifact(String ownerId, String title, String type, String classification) {
    this.id = UUID.randomUUID().toString();
    this.ownerId = ownerId;
    this.title = title;
    this.type = type;
    this.classification = classification;
  }

  @PrePersist
  void onCreate() {
    if (this.createdAt == null) {
      this.createdAt = Instant.now();
    }
  }

  public String getId() { return id; }
  public String getOwnerId() { return ownerId; }
  public String getTitle() { return title; }
  public String getType() { return type; }
  public String getClassification() { return classification; }
  public String getSourceDocIds() { return sourceDocIds; }
  public void setSourceDocIds(String s) { this.sourceDocIds = s; }
  public String getBlobKey() { return blobKey; }
  public void setBlobKey(String b) { this.blobKey = b; }
  public String getMimeType() { return mimeType; }
  public void setMimeType(String m) { this.mimeType = m; }
  public long getSize() { return size; }
  public void setSize(long s) { this.size = s; }
  public String getSha256() { return sha256; }
  public void setSha256(String s) { this.sha256 = s; }
  public Instant getCreatedAt() { return createdAt; }
}
