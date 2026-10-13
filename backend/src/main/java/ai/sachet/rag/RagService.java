package ai.sachet.rag;

import ai.sachet.config.SachetProperties;
import ai.sachet.domain.Classification;
import ai.sachet.domain.Document;
import ai.sachet.domain.DocumentChunk;
import ai.sachet.domain.Role;
import ai.sachet.domain.User;
import ai.sachet.repo.DocumentChunkRepository;
import ai.sachet.repo.DocumentRepository;
import ai.sachet.repo.UserRepository;
import ai.sachet.security.SachetPrincipal;
import ai.sachet.service.AuditService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Permission-first hybrid retrieval (Phase 4). Order of operations is the
 * security property: authorize → scope candidates → score → fuse → cite.
 * The model/LLM layer (Phase 5) only ever receives these cited, authorized
 * results — never raw store access.
 */
@Service
public class RagService {

  private static final Logger log = LoggerFactory.getLogger(RagService.class);

  private final UserRepository users;
  private final DocumentRepository documents;
  private final DocumentChunkRepository chunks;
  private final EmbeddingService embeddings;
  private final JvmVectorStore jvmStore;
  private final LikeKeywordSearch keywordSearch;
  private final AuditService audit;
  private final SachetProperties props;
  private final java.util.Optional<PgVectorStore> pgStore;

  public RagService(UserRepository users, DocumentRepository documents,
      DocumentChunkRepository chunks, EmbeddingService embeddings, JvmVectorStore jvmStore,
      LikeKeywordSearch keywordSearch, AuditService audit, SachetProperties props,
      java.util.Optional<PgVectorStore> pgStore) {
    this.users = users;
    this.documents = documents;
    this.chunks = chunks;
    this.embeddings = embeddings;
    this.jvmStore = jvmStore;
    this.keywordSearch = keywordSearch;
    this.audit = audit;
    this.props = props;
    this.pgStore = pgStore;
  }

  public record ResultItem(long chunkId, String documentId, String documentTitle,
      int documentVersion, int page, String section, double score, double vectorScore) {
  }

  public record RagResult(List<ResultItem> results, EvidenceEvaluator.EvidenceStatus evidenceStatus,
      String message) {
  }

  @Transactional(readOnly = true)
  public RagResult search(SachetPrincipal principal, String query, Integer topK,
      List<String> docIds) {
    if (query == null || query.isBlank() || query.length() > 2000) {
      throw new IllegalArgumentException("Query must be 1..2000 characters.");
    }
    User user = users.findById(principal.getUserId())
        .filter(User::isActive)
        .orElseThrow(() -> new AccessDeniedException("Forbidden."));
    int k = topK == null ? props.getRetrievalTopK()
        : Math.min(Math.max(topK, 1), props.getRetrievalMaxTopK());
    List<String> bands = Arrays.stream(Classification.values())
        .filter(c -> c.accessibleTo(user.getClearance()))
        .map(Enum::name).toList();
    List<String> roles = user.getRoles().stream().map(Role::getName).toList();
    var filter = new VectorStore.VectorSearchFilter(user.getId(), roles, user.getDepartment(),
        bands, docIds, JvmVectorStore.termsOf(query));

    float[] qvec = embeddings.embed(query); // 503 when the engine is down (never silent)

    VectorStore store = jvmStore;
    if (pgStore.isPresent() && pgStore.get().available()) {
      store = pgStore.get();
    }
    int over = Math.min(k * 2, props.getRetrievalMaxTopK());
    List<VectorStore.ScoredChunk> vecHits = store.search(qvec, filter, over);
    List<LikeKeywordSearch.KeywordHit> kwHits = keywordSearch.search(filter, over);

    List<Long> vecIds = vecHits.stream().map(VectorStore.ScoredChunk::chunkId).toList();
    List<Long> kwIds = kwHits.stream().map(LikeKeywordSearch.KeywordHit::chunkId).toList();
    List<RrfFusion.Fused> fused = RrfFusion.fuse(vecIds, kwIds, 60).stream().limit(k).toList();

    Map<Long, Double> vecById = new HashMap<>();
    for (var h : vecHits) {
      vecById.put(h.chunkId(), h.vectorScore());
    }
    Map<Long, ai.sachet.domain.DocumentChunk> chunkById = new HashMap<>();
    for (DocumentChunk c : chunks.findAllById(fused.stream().map(RrfFusion.Fused::chunkId).toList())) {
      chunkById.put(c.getId(), c);
    }
    Map<String, Document> docById = new HashMap<>();
    List<String> needDocs = chunkById.values().stream().map(DocumentChunk::getDocumentId).distinct().toList();
    for (Document d : documents.findAllById(needDocs)) {
      docById.put(d.getId(), d);
    }
    List<ResultItem> items = new ArrayList<>();
    double best = -2;
    for (var f : fused) {
      DocumentChunk c = chunkById.get(f.chunkId());
      if (c == null) {
        continue;
      }
      Document d = docById.get(c.getDocumentId());
      if (d == null) {
        continue; // document removed after indexing; skip, never leak
      }
      double vs = vecById.getOrDefault(f.chunkId(), 0.0);
      best = Math.max(best, vs);
      items.add(new ResultItem(c.getId(), d.getId(), d.getTitle(), d.getVersion(),
          c.getPageNumber(), c.getSection(), f.rrf(), vs));
    }
    boolean isDocumentScoped = docIds != null && !docIds.isEmpty();
    boolean isSynthesis = isSynthesisQuery(query);

    if (items.isEmpty() && isDocumentScoped) {
      List<String> readableIds = documents.findReadableIds(user.getId(), roles, user.getDepartment(), bands, org.springframework.data.domain.Pageable.unpaged());
      for (String did : docIds) {
        if (!readableIds.contains(did)) {
          continue; // unauthorized document: strictly ignored
        }
        Document d = documents.findById(did).orElse(null);
        if (d != null) {
          List<DocumentChunk> docChunks = chunks.findByDocumentIdOrderByChunkIndexAsc(did);
          for (DocumentChunk c : docChunks.stream().limit(k).toList()) {
            items.add(new ResultItem(c.getId(), d.getId(), d.getTitle(), d.getVersion(),
                c.getPageNumber(), c.getSection(), 0.6, 0.6));
          }
        }
      }
      if (!items.isEmpty()) {
        best = Math.max(best, 0.6);
      }
    }

    var evidence = EvidenceEvaluator.evaluate(best, items.size(), props, isDocumentScoped || isSynthesis);
    List<String> returned = items.stream().map(ResultItem::documentId).distinct().limit(50).toList();
    audit.append(user.getId(), "search.executed", returned, null, "count=" + items.size(), "");
    if (log.isDebugEnabled()) {
      log.debug("search user={} hits={} evidence={}", user.getId(), items.size(), evidence.status());
    }
    return new RagResult(List.copyOf(items), evidence.status(), evidence.message());
  }

  public static boolean isSynthesisQuery(String query) {
    if (query == null) {
      return false;
    }
    String q = query.toLowerCase(Locale.ROOT);
    return q.contains("summar") || q.contains("overview") || q.contains("briefing")
        || q.contains("breakdown") || q.contains("slide") || q.contains("ppt")
        || q.contains("presentation") || q.contains("outline") || q.contains("explain")
        || q.contains("what is this") || q.contains("tell me about") || q.contains("key points")
        || q.contains("highlights") || q.contains("review") || q.contains("analyze")
        || q.contains("analysis") || q.contains("about this document") || q.contains("about this pdf")
        || q.contains("about the document") || q.contains("about the pdf") || q.contains("main points")
        || q.contains("बताओ") || q.contains("समझाओ") || q.contains("बारे में") || q.contains("डेटा")
        || q.contains("पीडीएफ") || q.contains("हाल चाल") || q.contains("की हाल") || q.contains("विश्लेषण")
        || q.contains("जानकारी") || q.contains("दस्तावेज") || q.contains("दस्तावेज़") || q.contains("की अछि")
        || q.contains("की भेल") || q.contains("बताउ") || q.contains("बुझाउ") || q.contains("कहो");
  }
}
