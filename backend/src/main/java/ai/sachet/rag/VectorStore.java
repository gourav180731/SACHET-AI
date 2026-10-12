package ai.sachet.rag;

import java.util.List;

/**
 * Vector search contract. Implementations MUST apply the authorization filter
 * in the candidate query itself: scoring code never sees chunks from
 * documents the caller may not read. There is no unfiltered search method.
 */
public interface VectorStore {

  /** Authorization + scoping filter. {@code docIds} optionally narrows to explicit docs. */
  record VectorSearchFilter(String userId, List<String> roles, String department,
      List<String> bands, List<String> docIds, List<String> queryTerms) {
  }

  record ScoredChunk(long chunkId, double vectorScore) {
  }

  /** Top matches by vector similarity. Never null; may be empty. */
  List<ScoredChunk> search(float[] query, VectorSearchFilter filter, int topK);
}
