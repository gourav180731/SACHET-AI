package ai.sachet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Document-level grant. Access is deny-by-default: a read is allowed only via
 * ownership, a USER grant, a ROLE grant, or a DEPARTMENT grant — checked
 * server-side BEFORE any retrieval result reaches a model.
 */
@Entity
@Table(name = "document_permissions",
    uniqueConstraints = @UniqueConstraint(columnNames = {"document_id", "principal_type", "principal_id"}))
public class DocumentPermission {

  public enum PrincipalType { USER, ROLE, DEPARTMENT }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "document_id", nullable = false, length = 36)
  private String documentId;

  @Column(name = "principal_type", nullable = false, length = 16)
  private String principalType;

  @Column(name = "principal_id", nullable = false, length = 128)
  private String principalId;

  @Column(name = "can_read", nullable = false)
  private boolean canRead = true;

  protected DocumentPermission() {
  }

  public DocumentPermission(String documentId, PrincipalType type, String principalId) {
    this.documentId = documentId;
    this.principalType = type.name();
    this.principalId = principalId;
  }

  public String getDocumentId() { return documentId; }
  public PrincipalType getPrincipalType() { return PrincipalType.valueOf(principalType); }
  public String getPrincipalId() { return principalId; }
  public boolean isCanRead() { return canRead; }
}
