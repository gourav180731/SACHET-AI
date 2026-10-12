package ai.sachet.service;

import ai.sachet.domain.Classification;
import ai.sachet.domain.Document;
import ai.sachet.domain.DocumentPermission;
import ai.sachet.domain.Role;
import ai.sachet.domain.User;
import ai.sachet.repo.DocumentPermissionRepository;
import ai.sachet.repo.DocumentRepository;
import ai.sachet.repo.UserRepository;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Server-side document authorization (Phase 2). Deny-by-default: a read is
 * granted only when ALL of these hold —
 * <ol>
 *   <li>user exists and is active,</li>
 *   <li>user clearance rank covers the document classification,</li>
 *   <li>ownership OR an explicit USER / ROLE / DEPARTMENT grant exists.</li>
 * </ol>
 * This check runs BEFORE retrieval results may reach any model.
 */
@Service
public class DocumentAccessService {

  private final UserRepository users;
  private final DocumentRepository documents;
  private final DocumentPermissionRepository grants;

  public DocumentAccessService(UserRepository users, DocumentRepository documents,
      DocumentPermissionRepository grants) {
    this.users = users;
    this.documents = documents;
    this.grants = grants;
  }

  @Transactional(readOnly = true)
  public boolean canRead(String userId, String documentId) {
    Optional<User> user = users.findById(userId);
    Optional<Document> doc = documents.findById(documentId);
    if (user.isEmpty() || doc.isEmpty() || !user.get().isActive()) {
      return false;
    }
    return canRead(user.get(), doc.get());
  }

  /** Pure rule evaluation (unit-testable without a database). */
  public boolean canRead(User user, Document doc) {
    if (user == null || doc == null || !user.isActive()) {
      return false;
    }
    Classification clearance;
    try {
      clearance = user.getClearance();
    } catch (IllegalArgumentException e) {
      return false;
    }
    if (!doc.getClassification().accessibleTo(clearance)) {
      return false; // clearance gate first: classification outranks any grant
    }
    if (user.getId().equals(doc.getOwnerId())) {
      return true;
    }
    Set<String> roleNames = user.getRoles().stream().map(Role::getName).collect(Collectors.toSet());
    for (DocumentPermission g : grants.findByDocumentId(doc.getId())) {
      if (!g.isCanRead()) {
        continue;
      }
      switch (g.getPrincipalType()) {
        case USER -> { if (user.getId().equals(g.getPrincipalId())) return true; }
        case ROLE -> { if (roleNames.contains(g.getPrincipalId())) return true; }
        case DEPARTMENT -> {
          if (user.getDepartment() != null && user.getDepartment().equals(g.getPrincipalId())) return true;
        }
      }
    }
    return false;
  }
}
