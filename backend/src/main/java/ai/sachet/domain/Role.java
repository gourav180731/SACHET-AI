package ai.sachet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;

/** Configurable role (USER, ANALYST, OFFICER, REVIEWER, ADMIN, SECURITY_ADMIN, …). */
@Entity
@Table(name = "roles")
public class Role {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 64)
  private String name;

  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(name = "role_permissions",
      joinColumns = @JoinColumn(name = "role_id"),
      inverseJoinColumns = @JoinColumn(name = "permission_id"))
  private Set<Permission> permissions = new HashSet<>();

  protected Role() {
  }

  public Role(String name) {
    this.name = name;
  }

  public Long getId() { return id; }
  public String getName() { return name; }
  public Set<Permission> getPermissions() { return permissions; }
}
