package ai.sachet.web;

import ai.sachet.security.SachetPrincipal;
import ai.sachet.service.ArtifactService;
import ai.sachet.service.DocumentStudioService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Document Studio (Phase 6). All operations are permission-filtered through
 * RagService via DocumentStudioService — Studio can never read documents the
 * caller is not authorized for. DOCX/PPTX artifacts are generated via
 * ArtifactService and exposed through /api/v1/artifacts.
 */
@RestController
@RequestMapping("${sachet.api-prefix:/api/v1}/studio")
public class StudioController {

  private final DocumentStudioService studio;
  private final ArtifactService artifacts;

  public StudioController(DocumentStudioService studio, ArtifactService artifacts) {
    this.studio = studio;
    this.artifacts = artifacts;
  }

  public record StudioRequest(
      @Size(max = 2000) String query,
      List<String> docIds,
      Integer topK,
      String targetLang,
      String topic,
      String docIdA,
      String docIdB,
      String title,
      Integer slides) {}

  public record StudioResponse(String output, List<String> sources) {}

  private ResponseEntity<StudioResponse> ok(String output) {
    return ResponseEntity.ok(new StudioResponse(output, List.of()));
  }

  @PostMapping("/executive-summary")
  public ResponseEntity<StudioResponse> executiveSummary(@AuthenticationPrincipal SachetPrincipal p, @Valid @RequestBody StudioRequest r) {
    return ok(studio.executiveSummary(p, r.query(), r.docIds(), r.topK()));
  }

  @PostMapping("/detailed-summary")
  public ResponseEntity<StudioResponse> detailedSummary(@AuthenticationPrincipal SachetPrincipal p, @Valid @RequestBody StudioRequest r) {
    return ok(studio.detailedSummary(p, r.query(), r.docIds(), r.topK()));
  }

  @PostMapping("/key-points")
  public ResponseEntity<StudioResponse> keyPoints(@AuthenticationPrincipal SachetPrincipal p, @Valid @RequestBody StudioRequest r) {
    return ok(studio.keyPoints(p, r.query(), r.docIds(), r.topK()));
  }

  @PostMapping("/entities")
  public ResponseEntity<StudioResponse> entities(@AuthenticationPrincipal SachetPrincipal p, @Valid @RequestBody StudioRequest r) {
    return ok(studio.extractEntities(p, r.query(), r.docIds(), r.topK()));
  }

  @PostMapping("/action-items")
  public ResponseEntity<StudioResponse> actionItems(@AuthenticationPrincipal SachetPrincipal p, @Valid @RequestBody StudioRequest r) {
    return ok(studio.extractActionItems(p, r.query(), r.docIds(), r.topK()));
  }

  @PostMapping("/translate")
  public ResponseEntity<StudioResponse> translate(@AuthenticationPrincipal SachetPrincipal p, @Valid @RequestBody StudioRequest r) {
    return ok(studio.translate(p, r.query(), r.targetLang() != null ? r.targetLang() : "hi", r.docIds(), r.topK()));
  }

  @PostMapping("/explain")
  public ResponseEntity<StudioResponse> explain(@AuthenticationPrincipal SachetPrincipal p, @Valid @RequestBody StudioRequest r) {
    return ok(studio.explain(p, r.topic() != null ? r.topic() : r.query(), r.docIds(), r.topK()));
  }

  @PostMapping("/generate/docx")
  public ResponseEntity<ArtifactService.ArtifactView> generateDocx(@AuthenticationPrincipal SachetPrincipal p, @Valid @RequestBody StudioRequest r) throws java.io.IOException {
    return ResponseEntity.ok(ArtifactService.ArtifactView.from(
        artifacts.generateDocx(p, r.title() != null ? r.title() : "Report", r.topic() != null ? r.topic() : r.query(), r.docIds(), r.topK())));
  }

  @PostMapping("/generate/pptx")
  public ResponseEntity<ArtifactService.ArtifactView> generatePptx(@AuthenticationPrincipal SachetPrincipal p, @Valid @RequestBody StudioRequest r) throws java.io.IOException {
    return ResponseEntity.ok(ArtifactService.ArtifactView.from(
        artifacts.generatePptx(p, r.title() != null ? r.title() : "Briefing", r.topic() != null ? r.topic() : r.query(), r.docIds(), r.topK(), r.slides())));
  }

  @PostMapping("/compare")
  public ResponseEntity<StudioResponse> compare(@AuthenticationPrincipal SachetPrincipal p, @Valid @RequestBody StudioRequest r) {
    if (r.docIdA() == null || r.docIdB() == null) {
      throw new IllegalArgumentException("docIdA and docIdB are required.");
    }
    return ok(studio.compareDocuments(p, r.topic() != null ? r.topic() : r.query(), r.docIdA(), r.docIdB(), r.topK()));
  }
}
