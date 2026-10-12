package ai.sachet.web;

import ai.sachet.domain.AuditLogEntry;
import ai.sachet.repo.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Audit read API. Metadata only — audit rows never contain document content,
 * passwords, or tokens (enforced by AuditService contract). Restricted to
 * security-administrators; backend authorization is the source of truth.
 */
@RestController
@RequestMapping("${sachet.api-prefix:/api/v1}/audit")
public class AuditController {

  private final AuditLogRepository repo;

  public AuditController(AuditLogRepository repo) {
    this.repo = repo;
  }

  public record AuditEntryView(Long id, String createdAt, String actor, String action,
      String docIds, String classification, String pages, String model) {
    static AuditEntryView from(AuditLogEntry e) {
      return new AuditEntryView(e.getId(),
          e.getCreatedAt() != null ? e.getCreatedAt().toString() : null,
          e.getActor(), e.getAction(), e.getDocIds(), e.getClassification(),
          e.getPages(), e.getModel());
    }
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN','SECURITY_ADMIN')")
  public ResponseEntity<Dtos.PageResponse<AuditEntryView>> list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    int safeSize = Math.min(Math.max(size, 1), 100);
    Page<AuditLogEntry> p = repo.findAllByOrderByIdDesc(
        PageRequest.of(Math.max(page, 0), safeSize, Sort.by("id").descending()));
    var content = p.getContent().stream().map(AuditEntryView::from).toList();
    return ResponseEntity.ok(new Dtos.PageResponse<>(content, p.getNumber(), p.getSize(),
        p.getTotalElements(), p.getTotalPages()));
  }
}
