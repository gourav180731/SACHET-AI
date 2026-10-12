package ai.sachet.repo;

import ai.sachet.domain.Document;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Permission-filtered document queries. There is deliberately NO unfiltered
 * "list everything" method: every read path carries the caller's grants, so a
 * global vector/keyword search can never ignore authorization (Phase 4 builds
 * on these predicates).
 */
public interface DocumentRepository extends JpaRepository<Document, String> {

  List<Document> findByFamilyIdOrderByVersionAsc(String familyId);

  Optional<Document> findBySha256AndOwnerId(String sha256, String ownerId);

  void deleteByIdIn(List<String> documentIds);

  /**
   * CURRENT documents the caller may read: ownership or an explicit
   * USER/ROLE/DEPARTMENT grant, restricted to classifications at or below
   * the caller's clearance band list.
   */
  @Query("""
      SELECT DISTINCT d FROM Document d LEFT JOIN DocumentPermission g ON g.documentId = d.id
      WHERE d.status = 'CURRENT' AND d.classification IN :bands AND (
        d.ownerId = :uid
        OR (g.principalType = 'USER' AND g.principalId = :uid AND g.canRead = true)
        OR (g.principalType = 'ROLE' AND g.principalId IN :roles AND g.canRead = true)
        OR (g.principalType = 'DEPARTMENT' AND g.principalId = :dept AND g.canRead = true)
      )""")
  Page<Document> findReadable(@Param("uid") String userId,
      @Param("roles") List<String> roles,
      @Param("dept") String department,
      @Param("bands") List<String> bands,
      Pageable pageable);

  /** Readable document IDs (candidate scoping for retrieval; same predicates as {@link #findReadable}). */
  @Query("""
      SELECT DISTINCT d.id FROM Document d LEFT JOIN DocumentPermission g ON g.documentId = d.id
      WHERE d.status = 'CURRENT' AND d.classification IN :bands AND (
        d.ownerId = :uid
        OR (g.principalType = 'USER' AND g.principalId = :uid AND g.canRead = true)
        OR (g.principalType = 'ROLE' AND g.principalId IN :roles AND g.canRead = true)
        OR (g.principalType = 'DEPARTMENT' AND g.principalId = :dept AND g.canRead = true)
      )""")
  List<String> findReadableIds(@Param("uid") String userId,
      @Param("roles") List<String> roles,
      @Param("dept") String department,
      @Param("bands") List<String> bands,
      Pageable pageable);
}
