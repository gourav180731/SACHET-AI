package ai.sachet.web;

import ai.sachet.domain.GeneratedArtifact;
import ai.sachet.security.SachetPrincipal;
import ai.sachet.service.ArtifactService;
import ai.sachet.storage.LocalStorageService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Generated artifacts API. Download goes through authorization + audit;
 * no public blob URLs. 404s hide existence for unauthorized requests.
 */
@RestController
@RequestMapping("${sachet.api-prefix:/api/v1}/artifacts")
public class ArtifactController {

  private final ArtifactService artifacts;
  private final LocalStorageService storage;
  private final ai.sachet.service.AuditService audit;

  public ArtifactController(ArtifactService artifacts, LocalStorageService storage,
      ai.sachet.service.AuditService audit) {
    this.artifacts = artifacts;
    this.storage = storage;
    this.audit = audit;
  }

  @GetMapping
  public Dtos.PageResponse<ArtifactService.ArtifactView> list(
      @AuthenticationPrincipal SachetPrincipal p,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    var res = artifacts.listVisible(p, page, size);
    List<ArtifactService.ArtifactView> content = res.getContent().stream()
        .map(ArtifactService.ArtifactView::from).toList();
    return new Dtos.PageResponse<>(content, res.getNumber(), res.getSize(),
        res.getTotalElements(), res.getTotalPages());
  }

  @GetMapping("/{id}")
  public ArtifactService.ArtifactView get(@AuthenticationPrincipal SachetPrincipal p,
      @PathVariable String id) {
    return ArtifactService.ArtifactView.from(artifacts.getAuthorized(p, id));
  }

  @GetMapping("/{id}/download")
  public ResponseEntity<Resource> download(@AuthenticationPrincipal SachetPrincipal p,
      @PathVariable String id) throws IOException {
    GeneratedArtifact a = artifacts.getAuthorized(p, id);
    Path path = storage.resolve(a.getBlobKey());
    if (!Files.exists(path)) {
      throw new NotFoundException();
    }
    Resource body = new FileSystemResource(path);
    audit.append(p.getUserId(), "artifact.downloaded",
        a.getSourceDocIds() == null || a.getSourceDocIds().isBlank() ? List.of()
            : List.of(a.getSourceDocIds().split(",")),
        a.getClassification(), "", "");
    String filename = a.getTitle().replaceAll("[^A-Za-z0-9._-]", "_")
        + (a.getType().equalsIgnoreCase("PPTX") ? ".pptx" : ".docx");
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
        .contentType(MediaType.parseMediaType(a.getMimeType() != null ? a.getMimeType() : "application/octet-stream"))
        .contentLength(a.getSize())
        .body(body);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@AuthenticationPrincipal SachetPrincipal p,
      @PathVariable String id) {
    artifacts.delete(p, id);
    return ResponseEntity.noContent().build();
  }
}
