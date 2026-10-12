package ai.sachet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Application user. Passwords are BCrypt hashes only — never plaintext. */
@Entity
@Table(name = "users")
public class User {

  @Id
  @Column(length = 36)
  private String id;

  @Column(nullable = false, unique = true, length = 320)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 255)
  private String passwordHash;

  @Column(nullable = false, length = 32)
  private String clearance = Classification.INTERNAL.name();

  @Column(length = 128)
  private String department;

  @Column(name = "is_active", nullable = false)
  private boolean active = true;

  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(name = "user_roles",
      joinColumns = @JoinColumn(name = "user_id"),
      inverseJoinColumns = @JoinColumn(name = "role_id"))
  private Set<Role> roles = new HashSet<>();

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected User() {
  }

  public User(String email, String passwordHash) {
    this.id = UUID.randomUUID().toString();
    this.email = email;
    this.passwordHash = passwordHash;
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
  public String getEmail() { return email; }
  public String getPasswordHash() { return passwordHash; }
  public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
  public Classification getClearance() { return Classification.valueOf(clearance); }
  public void setClearance(Classification c) { this.clearance = c.name(); }
  public String getDepartment() { return department; }
  public void setDepartment(String department) { this.department = department; }
  public boolean isActive() { return active; }
  public void setActive(boolean active) { this.active = active; }
  public Set<Role> getRoles() { return roles; }
  public Instant getCreatedAt() { return createdAt; }
}
