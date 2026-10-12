package ai.sachet.web;

import ai.sachet.domain.Document;
import ai.sachet.domain.DocumentChunk;
import ai.sachet.domain.IngestionJob;
import java.time.Instant;
import java.util.List;

/** API shapes (metadata only — chunk text is served paginated, never document blobs). */
public final class Dtos {

  private Dtos() {
  }

  public record DocumentResponse(String id, String familyId, String title, String ownerId,
      String department, String classification, String status, int version, String sha256,
      String mimeType, String sourceFilename, int pageCount, int chunkCount, Instant createdAt) {
    static DocumentResponse from(Document d) {
      return new DocumentResponse(d.getId(), d.getFamilyId(), d.getTitle(), d.getOwnerId(),
          d.getDepartment(), d.getClassification().name(), d.getStatus(), d.getVersion(),
          d.getSha256(), d.getMimeType(), d.getSourceFilename(), d.getPageCount(),
          d.getChunkCount(), d.getCreatedAt());
    }
  }

  public record JobResponse(String id, String documentId, String status, int progress,
      String errorCode, String errorMessage, int attempts, Instant createdAt) {
    static JobResponse from(IngestionJob j) {
      return new JobResponse(j.getId(), j.getDocumentId(), j.getStatus().name(), j.getProgress(),
          j.getErrorCode(), j.getErrorMessage(), j.getAttempts(), j.getCreatedAt());
    }
  }

  public record ChunkResponse(long id, int chunkIndex, int pageNumber, String section,
      String text, int charCount) {
    static ChunkResponse from(DocumentChunk c) {
      return new ChunkResponse(c.getId(), c.getChunkIndex(), c.getPageNumber(),
          c.getSection(), c.getText(), c.getCharCount());
    }
  }

  public record SubmitResponse(boolean duplicate, String documentId, String jobId, String status) {
  }

  public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
  }
}
