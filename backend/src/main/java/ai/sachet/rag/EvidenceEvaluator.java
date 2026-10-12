package ai.sachet.rag;

import ai.sachet.config.SachetProperties;

/**
 * Evidence grading over the best vector (cosine) similarity in a result set.
 * Bands are DOCUMENTED HEURISTICS (configurable), never fake "confidence %":
 * clients display the band + citations so users can verify.
 */
public class EvidenceEvaluator {

  /** Exact insufficient-evidence sentence (contract — Phase 5 chat reuses it). */
  public static final String INSUFFICIENT_MESSAGE =
      "I could not find sufficient supporting information in the authorized documents.";

  public enum EvidenceStatus {
    STRONG,
    MODERATE,
    LIMITED,
    INSUFFICIENT
  }

  public record Evidence(EvidenceStatus status, String message) {
  }

  private EvidenceEvaluator() {
  }

  public static Evidence evaluate(double bestVectorScore, int resultCount, SachetProperties props) {
    return evaluate(bestVectorScore, resultCount, props, false);
  }

  public static Evidence evaluate(double bestVectorScore, int resultCount, SachetProperties props, boolean isScopedOrSynthesis) {
    if (resultCount == 0) {
      return new Evidence(EvidenceStatus.INSUFFICIENT, INSUFFICIENT_MESSAGE);
    }
    if (bestVectorScore >= props.getEvidenceStrong()) {
      return new Evidence(EvidenceStatus.STRONG, null);
    }
    if (bestVectorScore >= props.getEvidenceModerate()) {
      return new Evidence(EvidenceStatus.MODERATE, null);
    }
    if (bestVectorScore >= props.getEvidenceLimited()) {
      return new Evidence(EvidenceStatus.LIMITED, null);
    }
    // When the user has explicitly scoped the search to specific authorized document(s),
    // or when the query is a document summary/synthesis/overview task, the retrieved authorized
    // chunks from those documents constitute verified ground-truth context.
    if (isScopedOrSynthesis) {
      return new Evidence(EvidenceStatus.LIMITED, null);
    }
    return new Evidence(EvidenceStatus.INSUFFICIENT, INSUFFICIENT_MESSAGE);
  }
}
