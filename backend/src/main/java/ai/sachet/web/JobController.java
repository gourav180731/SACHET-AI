package ai.sachet.web;

import ai.sachet.ingest.IngestionService;
import ai.sachet.security.SachetPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ingestion job visibility (Phase 3). Jobs are private to their owner
 * (or ADMIN); anything else is a uniform 404.
 */
@RestController
@RequestMapping("${sachet.api-prefix:/api/v1}/ingestion/jobs")
public class JobController {

  private final IngestionService ingestion;

  public JobController(IngestionService ingestion) {
    this.ingestion = ingestion;
  }

  @GetMapping("/{id}")
  public Dtos.JobResponse get(@AuthenticationPrincipal SachetPrincipal principal,
      @PathVariable("id") String id) {
    return Dtos.JobResponse.from(ingestion.job(principal, id));
  }

  @PostMapping("/{id}/retry")
  public ResponseEntity<Dtos.JobResponse> retry(@AuthenticationPrincipal SachetPrincipal principal,
      @PathVariable("id") String id) {
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(Dtos.JobResponse.from(ingestion.retry(principal, id)));
  }
}
