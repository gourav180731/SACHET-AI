package ai.sachet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.sachet.repo.AuditLogRepository;
import ai.sachet.service.AuditService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

/** Hash-chained audit log: append/verify round-trip + tamper detection. */
@DataJpaTest
class AuditChainTest {

  @Autowired AuditLogRepository repo;

  @Test
  void appendAndVerify() {
    AuditService audit = new AuditService(repo);
    audit.append("u1", "auth.login", List.of(), null, "", "");
    audit.append("u1", "doc.read", List.of("doc-1"), "INTERNAL", "p3", "local-stub");
    assertTrue(audit.verifyChain());
  }

  @Test
  void tamperDetected() {
    AuditService audit = new AuditService(repo);
    audit.append("u1", "doc.read", List.of("doc-1"), "INTERNAL", "p3", "");
    var entry = repo.findTopByOrderByIdDesc().orElseThrow();
    entry.setPages("p3,p4,p5"); // attacker rewrites history
    repo.saveAndFlush(entry);
    assertFalse(audit.verifyChain());
  }

  @Test
  void emptyChainVerifies() {
    assertTrue(new AuditService(repo).verifyChain());
  }
}
