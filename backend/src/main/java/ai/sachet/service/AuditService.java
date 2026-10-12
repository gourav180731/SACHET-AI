package ai.sachet.service;

import ai.sachet.domain.AuditLogEntry;
import ai.sachet.repo.AuditLogRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hash-chained audit log. Every entry commits to the previous entry's hash, so
 * deletion or modification of any record breaks {@link #verifyChain()}.
 * Metadata only — callers must never pass document content, passwords, or tokens.
 */
@Service
public class AuditService {

  private static final Logger log = LoggerFactory.getLogger(AuditService.class);
  /** Genesis previous-hash for the first entry (fixed, public constant). */
  public static final String GENESIS = "GENESIS";

  private final AuditLogRepository repo;

  public AuditService(AuditLogRepository repo) {
    this.repo = repo;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public AuditLogEntry append(String actor, String action, List<String> docIds,
      String classification, String pages, String model) {
    String prev = repo.findTopByOrderByIdDesc().map(AuditLogEntry::getEntryHash).orElse(GENESIS);
    AuditLogEntry e = new AuditLogEntry();
    e.setActor(actor);
    e.setAction(action);
    e.setDocIds(docIds == null ? "" : String.join(",", docIds));
    e.setClassification(classification);
    e.setPages(pages == null ? "" : pages);
    e.setModel(model == null ? "" : model);
    e.setPrevHash(prev);
    e.setEntryHash(hash(prev, e));
    AuditLogEntry saved = repo.save(e);
    log.info("audit appended action={} actor={} seq={}", action, actor, saved.getId());
    return saved;
  }

  /** Recomputes the full chain. Returns false on any gap or tampering. */
  @Transactional(readOnly = true)
  public boolean verifyChain() {
    List<AuditLogEntry> all = repo.findAllByOrderByIdAsc();
    String prev = GENESIS;
    for (AuditLogEntry e : all) {
      if (!prev.equals(e.getPrevHash())) {
        return false;
      }
      if (!hash(prev, e).equals(e.getEntryHash())) {
        return false;
      }
      prev = e.getEntryHash();
    }
    return true;
  }

  static String hash(String prev, AuditLogEntry e) {
    String canonical = String.join("|",
        prev, e.getActor(), e.getAction(), e.getDocIds(),
        String.valueOf(e.getClassification()), e.getPages(), e.getModel());
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(canonical.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (Exception ex) {
      throw new IllegalStateException("SHA-256 unavailable", ex);
    }
  }
}
