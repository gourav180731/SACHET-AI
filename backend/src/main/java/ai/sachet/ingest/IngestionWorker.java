package ai.sachet.ingest;

import ai.sachet.config.SachetProperties;
import ai.sachet.domain.Document;
import ai.sachet.domain.DocumentChunk;
import ai.sachet.domain.IngestionJob;
import ai.sachet.domain.JobStatus;
import ai.sachet.repo.DocumentChunkRepository;
import ai.sachet.repo.DocumentRepository;
import ai.sachet.repo.IngestionJobRepository;
import ai.sachet.repo.UserRepository;
import ai.sachet.service.AuditService;
import ai.sachet.storage.LocalStorageService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Async ingestion worker. Each stage persists its state, so progress is
 * observable and failures land in FAILED with a safe code — never silently
 * READY. Repository calls run in their own short transactions (no long-lived
 * transaction holds a connection across OCR/parsing).
 */
@Service
public class IngestionWorker {

  private static final Logger log = LoggerFactory.getLogger(IngestionWorker.class);

  private final IngestionJobRepository jobs;
  private final DocumentRepository documents;
  private final DocumentChunkRepository chunks;
  private final UserRepository users;
  private final ParserRegistry parsers;
  private final OcrService ocr;
  private final ChunkingService chunking;
  private final MalwareScanner scanner;
  private final LocalStorageService storage;
  private final AuditService audit;
  private final SachetProperties props;
  private final ai.sachet.rag.EmbeddingService embeddings;

  public IngestionWorker(IngestionJobRepository jobs, DocumentRepository documents,
      DocumentChunkRepository chunks, UserRepository users, ParserRegistry parsers,
      OcrService ocr, ChunkingService chunking, MalwareScanner scanner,
      LocalStorageService storage, AuditService audit, SachetProperties props,
      ai.sachet.rag.EmbeddingService embeddings) {
    this.jobs = jobs;
    this.documents = documents;
    this.chunks = chunks;
    this.users = users;
    this.parsers = parsers;
    this.ocr = ocr;
    this.chunking = chunking;
    this.scanner = scanner;
    this.storage = storage;
    this.audit = audit;
    this.props = props;
    this.embeddings = embeddings;
  }

  @Async
  public void process(String jobId) {
    IngestionJob job = jobs.findById(jobId).orElse(null);
    if (job == null || job.getStatus() != JobStatus.QUEUED) {
      return; // idempotent guard: retries re-queue explicitly
    }
    try {
      run(job);
    } catch (Exception e) { // last line of defense: generic, safe, audited
      log.error("ingestion failed type={}", e.getClass().getSimpleName());
      fail(job, IngestionJob.Errors.INTERNAL, "Processing failed.");
    }
  }

  private void run(IngestionJob job) throws Exception {
    Document doc = documents.findById(job.getDocumentId()).orElse(null);
    if (doc == null) {
      fail(job, IngestionJob.Errors.INTERNAL, "Processing failed.");
      return;
    }
    var user = users.findById(job.getCreatedBy()).filter(u -> u.isActive()).orElse(null);
    if (user == null) {
      fail(job, IngestionJob.Errors.POLICY_DENIED, "Uploader authorization revoked.");
      return;
    }
    Path blob = storage.resolve(job.getBlobKey());

    stage(job, JobStatus.VALIDATING, 15);
    MalwareScanner.Verdict verdict;
    try {
      verdict = scanner.scan(blob);
    } catch (Exception e) {
      verdict = MalwareScanner.Verdict.NOT_SCANNED;
    }
    if (verdict == MalwareScanner.Verdict.INFECTED) {
      Files.deleteIfExists(blob);
      fail(job, IngestionJob.Errors.MALWARE_SUSPECT, "Upload blocked by security scan.");
      audit.append(user.getId(), "job.blocked.malware", List.of(doc.getId()),
          doc.getClassification().name(), "", "");
      return;
    }
    if (verdict == MalwareScanner.Verdict.NOT_SCANNED && props.isRequireMalwareScan()) {
      fail(job, IngestionJob.Errors.SCAN_UNAVAILABLE, "Security scan unavailable.");
      return;
    }

    stage(job, JobStatus.PARSING, 40);
    DocumentParser parser = parsers.resolve(doc.getSourceFilename());
    if (parser == null) {
      fail(job, IngestionJob.Errors.PARSE_ERROR, "Unsupported file type.");
      return;
    }
    ParsedDocument parsed;
    try {
      parsed = parser.parse(blob);
    } catch (Exception e) {
      log.warn("parse failed type={}", e.getClass().getSimpleName());
      fail(job, IngestionJob.Errors.PARSE_ERROR, "Could not parse document.");
      return;
    }

    if (parsed.needsOcr() && parser.ocrCapable()) {
      stage(job, JobStatus.OCR, 60);
      if (!ocr.available()) {
        fail(job, IngestionJob.Errors.OCR_UNAVAILABLE,
            "Scanned document requires OCR, which is unavailable.");
        return;
      }
      parsed = fillOcrBlanks(blob, parsed);
      if (parsed == null) {
        fail(job, IngestionJob.Errors.OCR_UNAVAILABLE, "OCR processing failed.");
        return;
      }
    }

    stage(job, JobStatus.CHUNKING, 85);
    List<DocumentChunk> entities = chunking.toEntities(doc.getId(), parsed);
    chunks.deleteByDocumentId(doc.getId()); // retry safety: re-chunk from scratch
    int batch = 100;
    for (int i = 0; i < entities.size(); i += batch) {
      chunks.saveAll(entities.subList(i, Math.min(i + batch, entities.size())));
    }

    stage(job, JobStatus.EMBEDDING, 90);
    try {
      for (DocumentChunk entity : entities) {
        entity.setEmbedding(embeddings.embed(entity.getText()));
      }
      chunks.saveAll(entities);
    } catch (ai.sachet.rag.EmbeddingUnavailableException e) {
      log.warn("embedding failed");
      fail(job, IngestionJob.Errors.EMBEDDING_FAILED, "Embedding failed.");
      return;
    }

    // Vectors are queryable as soon as they are stored (JVM store); the
    // INDEXING stage covers external-index maintenance when enabled.
    stage(job, JobStatus.INDEXING, 96);

    doc.setPageCount(parsed.pages().size());
    doc.setChunkCount(entities.size());
    documents.save(doc);

    job.setStatus(JobStatus.READY);
    job.setProgress(100);
    jobs.save(job);
    audit.append(user.getId(), "document.ready", List.of(doc.getId()),
        doc.getClassification().name(), "pages=" + parsed.pages().size(), "");
  }

  /** Renders blank PDF pages and OCRs them; returns null on OCR failure. */
  private ParsedDocument fillOcrBlanks(Path blob, ParsedDocument parsed) {
    List<Integer> blankPages = parsed.pages().stream()
        .filter(ParsedDocument.ParsedPage::blank)
        .map(ParsedDocument.ParsedPage::pageNumber).toList();
    Path tmp = null;
    try {
      tmp = Files.createTempDirectory("sachet-ocr-");
      List<Path> images = PdfPageRenderer.render(blob, blankPages, tmp);
      List<String> texts = ocr.ocrImages(images, "eng");
      var byPage = new java.util.HashMap<Integer, String>();
      for (int i = 0; i < blankPages.size() && i < texts.size(); i++) {
        byPage.put(blankPages.get(i), texts.get(i));
      }
      List<ParsedDocument.ParsedPage> out = new ArrayList<>();
      for (var page : parsed.pages()) {
        if (page.blank() && byPage.containsKey(page.pageNumber())) {
          String t = byPage.get(page.pageNumber()).strip();
          out.add(new ParsedDocument.ParsedPage(page.pageNumber(), t, page.section(),
              t.length() < PdfParser.BLANK_THRESHOLD));
        } else {
          out.add(page);
        }
      }
      out.sort(Comparator.comparingInt(ParsedDocument.ParsedPage::pageNumber));
      return new ParsedDocument(List.copyOf(out));
    } catch (Exception e) {
      log.warn("ocr failed type={}", e.getClass().getSimpleName());
      return null;
    } finally {
      deleteTree(tmp);
    }
  }

  private void deleteTree(Path dir) {
    if (dir == null) {
      return;
    }
    try (var walk = Files.walk(dir)) {
      walk.sorted(Comparator.reverseOrder()).forEach(p -> {
        try {
          Files.deleteIfExists(p);
        } catch (Exception ignored) {
          // best effort temp cleanup
        }
      });
    } catch (Exception ignored) {
      // best effort temp cleanup
    }
  }

  private void stage(IngestionJob job, JobStatus status, int progress) {
    job.setStatus(status);
    job.setProgress(progress);
    jobs.save(job);
  }

  private void fail(IngestionJob job, String code, String message) {
    job.fail(code, message);
    jobs.save(job);
    audit.append(job.getCreatedBy(), "job.failed", List.of(job.getDocumentId()), null, "", "");
  }
}
