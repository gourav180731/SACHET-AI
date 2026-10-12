package ai.sachet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.sachet.domain.Classification;
import ai.sachet.domain.Document;
import ai.sachet.domain.DocumentPermission;
import ai.sachet.domain.DocumentPermission.PrincipalType;
import ai.sachet.domain.Role;
import ai.sachet.domain.User;
import ai.sachet.repo.DocumentPermissionRepository;
import ai.sachet.repo.DocumentRepository;
import ai.sachet.repo.RoleRepository;
import ai.sachet.repo.UserRepository;
import ai.sachet.service.DocumentAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

/**
 * MANDATORY isolation tests: cross-user retrieval blocked, clearance gate
 * outranks ownership/grants, deny-by-default. (AGENTS.md §50 equivalent.)
 */
@DataJpaTest
class RbacIsolationTest {

  @Autowired UserRepository users;
  @Autowired RoleRepository roles;
  @Autowired DocumentRepository docs;
  @Autowired DocumentPermissionRepository grants;

  DocumentAccessService access;

  User alice; // OFFICER, RESTRICTED clearance
  User bob;   // USER, INTERNAL clearance
  Document secret;
  Document internal;

  @BeforeEach
  void seed() {
    access = new DocumentAccessService(users, docs, grants);
    Role officer = roles.save(new Role("OFFICER"));

    alice = new User("alice@example.gov", "hash");
    alice.setClearance(Classification.RESTRICTED);
    alice.setDepartment("C-DOT");
    alice.getRoles().add(officer);
    alice = users.save(alice);

    bob = new User("bob@example.gov", "hash");
    bob.setClearance(Classification.INTERNAL);
    bob = users.save(bob);

    secret = new Document("Secret Plan", alice.getId(), Classification.SECRET);
    secret.setDepartment("C-DOT");
    secret = docs.save(secret);

    internal = new Document("Internal Note", alice.getId(), Classification.INTERNAL);
    internal = docs.save(internal);
  }

  @Test
  void crossUserRetrievalBlocked() {
    // Bob asks for Alice's INTERNAL doc with no grant: denied.
    assertFalse(access.canRead(bob.getId(), internal.getId()));
  }

  @Test
  void clearanceGateOutranksOwnership() {
    // Alice OWNS the SECRET doc but her clearance is RESTRICTED: denied.
    assertFalse(access.canRead(alice.getId(), secret.getId()));
  }

  @Test
  void ownerWithClearanceReads() {
    alice.setClearance(Classification.SECRET);
    users.save(alice);
    assertTrue(access.canRead(alice.getId(), secret.getId()));
  }

  @Test
  void userGrantAllowsWithinClearance() {
    grants.save(new DocumentPermission(internal.getId(), PrincipalType.USER, bob.getId()));
    assertTrue(access.canRead(bob.getId(), internal.getId()));
  }

  @Test
  void grantCannotEscalateClearance() {
    grants.save(new DocumentPermission(secret.getId(), PrincipalType.USER, alice.getId()));
    // Grant exists but clearance still RESTRICTED < SECRET: still denied.
    assertFalse(access.canRead(alice.getId(), secret.getId()));
  }

  @Test
  void roleGrantAllows() {
    grants.save(new DocumentPermission(internal.getId(), PrincipalType.ROLE, "OFFICER"));
    assertTrue(access.canRead(alice.getId(), internal.getId()));
    assertFalse(access.canRead(bob.getId(), internal.getId())); // Bob has no OFFICER role
  }

  @Test
  void departmentGrantAllows() {
    internal.setDepartment("C-DOT");
    docs.save(internal);
    grants.save(new DocumentPermission(internal.getId(), PrincipalType.DEPARTMENT, "C-DOT"));
    assertTrue(access.canRead(alice.getId(), internal.getId()));
    assertFalse(access.canRead(bob.getId(), internal.getId())); // Bob has no department
  }

  @Test
  void inactiveUserDenied() {
    grants.save(new DocumentPermission(internal.getId(), PrincipalType.USER, bob.getId()));
    bob.setActive(false);
    users.save(bob);
    assertFalse(access.canRead(bob.getId(), internal.getId()));
  }

  @Test
  void unknownIdsDenied() {
    assertFalse(access.canRead("no-such-user", internal.getId()));
    assertFalse(access.canRead(alice.getId(), "no-such-doc"));
  }
}
