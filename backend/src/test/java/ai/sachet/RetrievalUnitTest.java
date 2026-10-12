package ai.sachet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import ai.sachet.config.SachetProperties;
import ai.sachet.rag.EvidenceEvaluator;
import ai.sachet.rag.RrfFusion;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Pure-unit: fusion ordering, evidence bands, exact insufficient phrase. */
class RetrievalUnitTest {

  @Test
  void rrfPrefersConsensus() {
    // doc 7 leads vector, doc 9 leads keyword, doc 5 is runner-up in both.
    List<RrfFusion.Fused> fused = RrfFusion.fuse(List.of(7L, 5L, 3L), List.of(9L, 5L, 1L), 60);
    assertEquals(5L, fused.get(0).chunkId()); // in both lists → first
    assertEquals(5, fused.size()); // union, no duplicates
  }

  @Test
  void rrfSingleModalityKeepsOrder() {
    List<RrfFusion.Fused> fused = RrfFusion.fuse(List.of(1L, 2L, 3L), List.of(), 60);
    assertEquals(List.of(1L, 2L, 3L), fused.stream().map(RrfFusion.Fused::chunkId).toList());
  }

  @Test
  void evidenceBands() {
    SachetProperties p = new SachetProperties();
    assertEquals(EvidenceEvaluator.EvidenceStatus.STRONG,
        EvidenceEvaluator.evaluate(0.85, 3, p).status());
    assertEquals(EvidenceEvaluator.EvidenceStatus.MODERATE,
        EvidenceEvaluator.evaluate(0.60, 3, p).status());
    assertEquals(EvidenceEvaluator.EvidenceStatus.LIMITED,
        EvidenceEvaluator.evaluate(0.40, 3, p).status());
    var insufficient = EvidenceEvaluator.evaluate(0.10, 3, p);
    assertEquals(EvidenceEvaluator.EvidenceStatus.INSUFFICIENT, insufficient.status());
    assertEquals(EvidenceEvaluator.INSUFFICIENT_MESSAGE, insufficient.message());
  }

  @Test
  void emptyResultsAreInsufficientWithExactPhrase() {
    var e = EvidenceEvaluator.evaluate(-2, 0, new SachetProperties());
    assertEquals(EvidenceEvaluator.EvidenceStatus.INSUFFICIENT, e.status());
    assertEquals("I could not find sufficient supporting information in the authorized documents.",
        e.message());
  }

  @Test
  void sufficientEvidenceCarriesNoMessage() {
    var e = EvidenceEvaluator.evaluate(0.9, 2, new SachetProperties());
    assertNull(e.message());
  }

  @Test
  void customThresholdsRespected() {
    SachetProperties p = new SachetProperties();
    p.setEvidenceStrong(0.95);
    p.setEvidenceModerate(0.90);
    p.setEvidenceLimited(0.85);
    assertEquals(EvidenceEvaluator.EvidenceStatus.MODERATE,
        EvidenceEvaluator.evaluate(0.92, 2, p).status());
  }
}
