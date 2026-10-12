package ai.sachet.security;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Authenticated request identity: the {@code security_context} carried by every
 * query (AGENTS.md §3). Phase 2 binds it to DB users + roles; document ACLs and
 * clearance checks consume it before any retrieval reaches a model.
 */
public class SachetPrincipal implements org.springframework.security.core.userdetails.UserDetails {

  private final String userId;
  private final List<String> roles;
  private final String clearance;
  private final String department;
  private final List<String> scopes;

  public SachetPrincipal(String userId, List<String> roles, String clearance,
      String department, List<String> scopes) {
    this.userId = userId;
    this.roles = List.copyOf(roles);
    this.clearance = clearance == null ? "INTERNAL" : clearance;
    this.department = department;
    this.scopes = List.copyOf(scopes);
  }

  public String getUserId() { return userId; }
  public List<String> getRoles() { return roles; }
  public String getClearance() { return clearance; }
  public String getDepartment() { return department; }
  public List<String> getScopes() { return scopes; }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return roles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
  }

  @Override public String getPassword() { return null; }
  @Override public String getUsername() { return userId; }
  @Override public boolean isAccountNonExpired() { return true; }
  @Override public boolean isAccountNonLocked() { return true; }
  @Override public boolean isCredentialsNonExpired() { return true; }
  @Override public boolean isEnabled() { return true; }
}
