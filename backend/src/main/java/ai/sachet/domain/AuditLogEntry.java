package ai.sachet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Append-only audit entry, hash-chained (each entry commits to the previous
 * hash). Stores metadata only — never document content, passwords, or tokens.
 */
@Entity
@Table(name = "audit_log")
public class AuditLogEntry {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 36)
  private String actor;

  @Column(nullable = false, length = 64)
  private String action;

  @Column(name = "doc_ids", length = 4000)
  private String docIds = "";

  @Column(length = 32)
  private String classification;

  @Column(length = 512)
  private String pages = "";

  @Column(length = 128)
  private String model = "";

  @Column(name = "prev_hash", nullable = false, length = 64)
  private String prevHash = "";

  @Column(name = "entry_hash", nullable = false, length = 64)
  private String entryHash = "";

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  public AuditLogEntry() {
  }

  @PrePersist
  void onCreate() {
    this.createdAt = Instant.now();
  }

  public Long getId() { return id; }
  public String getActor() { return actor; }
  public void setActor(String actor) { this.actor = actor; }
  public String getAction() { return action; }
  public void setAction(String action) { this.action = action; }
  public String getDocIds() { return docIds; }
  public void setDocIds(String docIds) { this.docIds = docIds; }
  public String getClassification() { return classification; }
  public void setClassification(String classification) { this.classification = classification; }
  public String getPages() { return pages; }
  public void setPages(String pages) { this.pages = pages; }
  public String getModel() { return model; }
  public void setModel(String model) { this.model = model; }
  public String getPrevHash() { return prevHash; }
  public void setPrevHash(String prevHash) { this.prevHash = prevHash; }
  public String getEntryHash() { return entryHash; }
  public void setEntryHash(String entryHash) { this.entryHash = entryHash; }
  public Instant getCreatedAt() { return createdAt; }
}
