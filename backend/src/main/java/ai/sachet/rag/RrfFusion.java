package ai.sachet.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reciprocal Rank Fusion over the union of vector and keyword rankings.
 * RRF needs no score calibration between the two modalities — only ranks —
 * which keeps the hybrid honest without tuned weights.
 */
public final class RrfFusion {

  private RrfFusion() {
  }

  public record Fused(long chunkId, double rrf) {
  }

  /**
   * @param vectorRanked chunk IDs ordered best-first by vector similarity
   * @param keywordRanked chunk IDs ordered best-first by lexical score
   * @param k smoothing constant (standard 60)
   */
  public static List<Fused> fuse(List<Long> vectorRanked, List<Long> keywordRanked, int k) {
    Map<Long, Double> acc = new HashMap<>();
    rankInto(acc, vectorRanked, k);
    rankInto(acc, keywordRanked, k);
    List<Fused> out = new ArrayList<>(acc.size());
    for (var e : acc.entrySet()) {
      out.add(new Fused(e.getKey(), e.getValue()));
    }
    out.sort(Comparator.comparingDouble(Fused::rrf).reversed());
    return out;
  }

  private static void rankInto(Map<Long, Double> acc, List<Long> ranked, int k) {
    for (int i = 0; i < ranked.size(); i++) {
      acc.merge(ranked.get(i), 1.0 / (k + i + 1), Double::sum);
    }
  }
}
