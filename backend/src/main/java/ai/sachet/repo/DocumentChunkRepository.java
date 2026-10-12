package ai.sachet.repo;

import ai.sachet.domain.DocumentChunk;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {
  List<DocumentChunk> findByDocumentIdOrderByChunkIndexAsc(String documentId);
  Page<DocumentChunk> findByDocumentIdOrderByChunkIndexAsc(String documentId, Pageable pageable);
  long countByDocumentId(String documentId);
  @Modifying
  @Transactional
  void deleteByDocumentId(String documentId);
  void deleteByDocumentIdIn(List<String> documentIds);

  /**
   * Embedded chunks of readable documents, newest first (used to fill the
   * candidate pool after keyword matches). Authorization is enforced by the
   * caller passing only readable document IDs.
   */
  @Query("SELECT c FROM DocumentChunk c WHERE c.documentId IN :docIds AND c.embedding IS NOT NULL"
      + " ORDER BY c.id DESC")
  List<DocumentChunk> findEmbeddedRecent(@Param("docIds") List<String> docIds, Pageable pageable);
}
