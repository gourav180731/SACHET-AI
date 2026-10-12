package ai.sachet.rag;

import ai.sachet.domain.DocumentChunk;
import ai.sachet.repo.DocumentRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Portable lexical search over the SAME authorized candidate pool as vector
 * search (readable IDs first, then term matching). Scores normalize term
 * coverage: fraction of distinct query terms present, tie-broken by total hits.
 */
@Service
public class LikeKeywordSearch {

  private final DocumentRepository documents;

  @PersistenceContext
  private EntityManager em;

  public LikeKeywordSearch(DocumentRepository documents) {
    this.documents = documents;
  }

  public record KeywordHit(long chunkId, double score) {
  }

  @Transactional(readOnly = true)
  public List<KeywordHit> search(VectorStore.VectorSearchFilter filter, int limit) {
    List<String> terms = filter.queryTerms() == null ? List.of() : filter.queryTerms();
    if (terms.isEmpty()) {
      return List.of();
    }
    List<String> docIds = RetrievalScope.readableIds(documents, filter, 10_000);
    if (docIds.isEmpty()) {
      return List.of();
    }
    StringBuilder jpql = new StringBuilder(
        "SELECT c FROM DocumentChunk c WHERE c.documentId IN :docIds AND (");
    for (int i = 0; i < terms.size(); i++) {
      if (i > 0) {
        jpql.append(" OR ");
      }
      jpql.append("LOWER(c.text) LIKE :t").append(i);
    }
    jpql.append(") ORDER BY c.id DESC");
    TypedQuery<DocumentChunk> q = em.createQuery(jpql.toString(), DocumentChunk.class);
    q.setParameter("docIds", docIds);
    for (int i = 0; i < terms.size(); i++) {
      q.setParameter("t" + i, "%" + terms.get(i).toLowerCase(Locale.ROOT) + "%");
    }
    q.setMaxResults(Math.max(limit, 1));
    List<KeywordHit> hits = new ArrayList<>();
    for (DocumentChunk c : q.getResultList()) {
      String lower = c.getText().toLowerCase(Locale.ROOT);
      int covered = 0;
      int total = 0;
      for (String t : terms) {
        int idx = -1;
        int count = 0;
        while ((idx = lower.indexOf(t, idx + 1)) >= 0) {
          count++;
        }
        if (count > 0) {
          covered++;
          total += count;
        }
      }
      double score = (double) covered / terms.size() + Math.min(total, 20) / 200.0;
      hits.add(new KeywordHit(c.getId(), score));
    }
    hits.sort(Comparator.comparingDouble(KeywordHit::score).reversed());
    return hits;
  }
}
