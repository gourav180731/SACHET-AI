package ai.sachet.web;

import ai.sachet.domain.Document;
import ai.sachet.ingest.IngestionService;
import ai.sachet.security.SachetPrincipal;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Document intake + metadata reads (Phase 3). All routes authenticated;
 * missing-or-forbidden reads return uniform 404 (existence hiding).
 */
@RestController
@RequestMapping("${sachet.api-prefix:/api/v1}/documents")
public class DocumentController {

  private final IngestionService ingestion;

  public DocumentController(IngestionService ingestion) {
    this.ingestion = ingestion;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Dtos.SubmitResponse> upload(
      @AuthenticationPrincipal SachetPrincipal principal,
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "title", required = false) String title,
      @RequestParam(value = "classification", required = false) String classification,
      @RequestParam(value = "department", required = false) String department) {
    IngestionService.SubmitResult r = ingestion.submit(principal, file, title, classification, department);
    var body = new Dtos.SubmitResponse(r.duplicate(), r.document().getId(),
        r.job() == null ? null : r.job().getId(),
        r.job() == null ? "READY" : r.job().getStatus().name());
    return ResponseEntity.status(r.duplicate() ? HttpStatus.OK : HttpStatus.ACCEPTED).body(body);
  }

  @PostMapping(value = "/{id}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Dtos.SubmitResponse> newVersion(
      @AuthenticationPrincipal SachetPrincipal principal,
      @PathVariable("id") String id,
      @RequestParam("file") MultipartFile file) {
    IngestionService.SubmitResult r = ingestion.newVersion(principal, id, file);
    var body = new Dtos.SubmitResponse(r.duplicate(), r.document().getId(),
        r.job() == null ? null : r.job().getId(),
        r.job() == null ? "READY" : r.job().getStatus().name());
    return ResponseEntity.status(r.duplicate() ? HttpStatus.OK : HttpStatus.ACCEPTED).body(body);
  }

  @GetMapping
  public Dtos.PageResponse<Dtos.DocumentResponse> list(
      @AuthenticationPrincipal SachetPrincipal principal,
      @RequestParam(value = "page", defaultValue = "0") int page,
      @RequestParam(value = "size", defaultValue = "20") int size) {
    Page<Document> docs = ingestion.list(principal, page(page, size));
    return new Dtos.PageResponse<>(docs.map(Dtos.DocumentResponse::from).toList(),
        docs.getNumber(), docs.getSize(), docs.getTotalElements(), docs.getTotalPages());
  }

  @GetMapping("/{id}")
  public Dtos.DocumentResponse get(@AuthenticationPrincipal SachetPrincipal principal,
      @PathVariable("id") String id) {
    return Dtos.DocumentResponse.from(ingestion.get(principal, id));
  }

  @GetMapping("/{id}/versions")
  public List<Dtos.DocumentResponse> versions(@AuthenticationPrincipal SachetPrincipal principal,
      @PathVariable("id") String id) {
    return ingestion.versions(principal, id).stream().map(Dtos.DocumentResponse::from).toList();
  }

  @GetMapping("/{id}/download")
  public ResponseEntity<org.springframework.core.io.Resource> download(
      @AuthenticationPrincipal SachetPrincipal principal, @PathVariable("id") String id) throws java.io.IOException {
    var d = ingestion.download(principal, id);
    String filename = d.document().getSourceFilename() != null ? d.document().getSourceFilename() : "document";
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename.replace("\"", "_") + "\"")
        .contentType(MediaType.parseMediaType(d.document().getMimeType() != null ? d.document().getMimeType() : "application/octet-stream"))
        .body(new org.springframework.core.io.FileSystemResource(d.path()));
  }

  @GetMapping("/{id}/chunks")
  public Dtos.PageResponse<Dtos.ChunkResponse> chunks(@AuthenticationPrincipal SachetPrincipal principal,
      @PathVariable("id") String id,
      @RequestParam(value = "page", defaultValue = "0") int page,
      @RequestParam(value = "size", defaultValue = "20") int size) {
    var chunks = ingestion.chunks(principal, id, page(page, size));
    return new Dtos.PageResponse<>(chunks.map(Dtos.ChunkResponse::from).toList(),
        chunks.getNumber(), chunks.getSize(), chunks.getTotalElements(), chunks.getTotalPages());
  }

  private static org.springframework.data.domain.Pageable page(int page, int size) {
    return PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal SachetPrincipal principal,
      @PathVariable("id") String id) {
    ingestion.deleteDocument(principal, id);
    return ResponseEntity.noContent().build();
  }
}
