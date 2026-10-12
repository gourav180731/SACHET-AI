package ai.sachet.web;

import ai.sachet.domain.Document;
import ai.sachet.rag.RagService;
import ai.sachet.repo.DocumentRepository;
import ai.sachet.security.SachetPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Grounded search (Phase 4). Returns permission-filtered results with
 * citations + evidence status. Answer generation lands in Phase 5; this
 * endpoint never invents content — insufficient evidence says so exactly.
 */
@RestController
@RequestMapping("${sachet.api-prefix:/api/v1}/search")
public class SearchController {

  private final RagService rag;
  private final DocumentRepository documents;

  public SearchController(RagService rag, DocumentRepository documents) {
    this.rag = rag;
    this.documents = documents;
  }

  public record SearchRequest(
      @Size(min = 1, max = 2000) String query,
      Integer topK,
      List<String> docIds,
      String classification,
      String department,
      String owner,
      String from,
      String to) {
  }

  public record SearchResponse(List<RagService.ResultItem> results, String evidenceStatus,
      String message) {
  }

  @PostMapping
  public ResponseEntity<SearchResponse> search(@AuthenticationPrincipal SachetPrincipal principal,
      @Valid @RequestBody SearchRequest req) {
    RagService.RagResult r = rag.search(principal, req.query(), req.topK(), req.docIds());
    List<RagService.ResultItem> filtered = r.results().stream()
        .filter(item -> matches(item, req))
        .toList();
    return ResponseEntity.ok(new SearchResponse(filtered, r.evidenceStatus().name(), r.message()));
  }

  /** Post-retrieval refinement only — authorization was already enforced by RagService. */
  private boolean matches(RagService.ResultItem item, SearchRequest req) {
    if (req.classification() == null && req.department() == null && req.owner() == null
        && req.from() == null && req.to() == null) {
      return true;
    }
    Document d = documents.findById(item.documentId()).orElse(null);
    if (d == null) {
      return false;
    }
    if (req.classification() != null && !req.classification().isBlank()
        && !d.getClassification().name().equalsIgnoreCase(req.classification())) {
      return false;
    }
    if (req.department() != null && !req.department().isBlank()
        && (d.getDepartment() == null || !d.getDepartment().equalsIgnoreCase(req.department()))) {
      return false;
    }
    if (req.owner() != null && !req.owner().isBlank() && !d.getOwnerId().equals(req.owner())) {
      return false;
    }
    if (req.from() != null && !req.from().isBlank()) {
      try {
        if (d.getCreatedAt().isBefore(java.time.Instant.parse(req.from()))) {
          return false;
        }
      } catch (java.time.format.DateTimeParseException e) {
        return false;
      }
    }
    if (req.to() != null && !req.to().isBlank()) {
      try {
        if (d.getCreatedAt().isAfter(java.time.Instant.parse(req.to()))) {
          return false;
        }
      } catch (java.time.format.DateTimeParseException e) {
        return false;
      }
    }
    return true;
  }
}
