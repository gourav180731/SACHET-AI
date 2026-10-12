package ai.sachet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Fine-grained permission (e.g. {@code doc:read}, {@code doc:export:high}, {@code admin:users}). */
@Entity
@Table(name = "permissions")
public class Permission {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 128)
  private String name;

  protected Permission() {
  }

  public Permission(String name) {
    this.name = name;
  }

  public Long getId() { return id; }
  public String getName() { return name; }
}
