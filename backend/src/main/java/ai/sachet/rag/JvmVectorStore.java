package ai.sachet.rag;

import ai.sachet.config.SachetProperties;
import ai.sachet.repo.DocumentChunkRepository;
import ai.sachet.repo.DocumentRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default vector store: permission-first candidate SQL, cosine scoring on the
 * JVM. Candidates are (a) keyword-matching embedded chunks of readable docs,
 * then (b) recent embedded chunks to fill the cap. Scoring runs ONLY over
 * these authorized candidates — unauthorized chunk embeddings are never loaded.
 */
@Service
public class JvmVectorStore implements VectorStore {

  private final DocumentRepository documents;
  private final DocumentChunkRepository chunks;
  private final SachetProperties props;

  @PersistenceContext
  private EntityManager em;

  public JvmVectorStore(DocumentRepository documents, DocumentChunkRepository chunks,
      SachetProperties props) {
    this.documents = documents;
    this.chunks = chunks;
    this.props = props;
  }

  @Override
  @Transactional(readOnly = true)
  public List<ScoredChunk> search(float[] query, VectorSearchFilter filter, int topK) {
    List<String> docIds = readableIds(filter);
    if (docIds.isEmpty()) {
      return List.of();
    }
    List<ai.sachet.domain.DocumentChunk> candidates = candidates(docIds, filter);
    List<ScoredChunk> scored = new ArrayList<>(candidates.size());
    for (var c : candidates) {
      float[] vec = c.getEmbedding();
      if (vec == null || vec.length != query.length) {
        continue;
      }
      scored.add(new ScoredChunk(c.getId(), FloatArrayCodec.cosine(query, vec)));
    }
    scored.sort((a, b) -> Double.compare(b.vectorScore(), a.vectorScore()));
    return scored.subList(0, Math.min(topK, scored.size()));
  }

  /** Readable CURRENT doc IDs for the caller (bounded; same predicates as listing). */
  List<String> readableIds(VectorSearchFilter filter) {
    return RetrievalScope.readableIds(documents, filter, 10_000);
  }

  private List<ai.sachet.domain.DocumentChunk> candidates(List<String> docIds,
      VectorSearchFilter filter) {
    int cap = props.getRetrievalCandidateCap();
    Map<Long, ai.sachet.domain.DocumentChunk> pool = new LinkedHashMap<>();
    List<String> terms = filter.queryTerms() == null ? List.of() : filter.queryTerms();
    for (var c : keywordMatches(docIds, terms, cap)) {
      pool.putIfAbsent(c.getId(), c);
    }
    if (pool.size() < cap) {
      for (var c : chunks.findEmbeddedRecent(docIds, PageRequest.ofSize(cap - pool.size()))) {
        pool.putIfAbsent(c.getId(), c);
      }
    }
    return List.copyOf(pool.values());
  }

  /**
   * Dynamic JPQL keyword prefilter. Only the predicate COUNT varies with the
   * term list; terms themselves are bound parameters (no injection surface).
   */
  List<ai.sachet.domain.DocumentChunk> keywordMatches(List<String> docIds,
      List<String> terms, int limit) {
    if (docIds.isEmpty() || terms.isEmpty()) {
      return List.of();
    }
    StringBuilder jpql = new StringBuilder(
        "SELECT c FROM DocumentChunk c WHERE c.documentId IN :docIds"
            + " AND c.embedding IS NOT NULL AND (");
    for (int i = 0; i < terms.size(); i++) {
      if (i > 0) {
        jpql.append(" OR ");
      }
      jpql.append("LOWER(c.text) LIKE :t").append(i);
    }
    jpql.append(") ORDER BY c.id DESC");
    TypedQuery<ai.sachet.domain.DocumentChunk> q =
        em.createQuery(jpql.toString(), ai.sachet.domain.DocumentChunk.class);
    q.setParameter("docIds", docIds);
    for (int i = 0; i < terms.size(); i++) {
      q.setParameter("t" + i, "%" + terms.get(i).toLowerCase(Locale.ROOT) + "%");
    }
    q.setMaxResults(limit);
    return q.getResultList();
  }

  /** Query terms for keyword paths: lowercase alphanumerics, length ≥ 2, capped at 8. */
  public static List<String> termsOf(String query) {
    if (query == null) {
      return List.of();
    }
    List<String> terms = new ArrayList<>();
    for (String part : query.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
      if (part.length() >= 2 && !terms.contains(part) && terms.size() < 8) {
        terms.add(part);
      }
    }
    return terms;
  }
}
