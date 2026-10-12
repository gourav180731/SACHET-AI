package ai.sachet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.sachet.config.SachetProperties;
import ai.sachet.rag.EmbeddingUnavailableException;
import ai.sachet.rag.FloatArrayCodec;
import ai.sachet.rag.LocalEmbeddingService;
import org.junit.jupiter.api.Test;

/**
 * Real local embeddings, pure-Java inference (no mocks, no natives):
 * dimensionality, normalization, determinism, semantic ordering, Hindi
 * support, honest failure. First run memory-maps the ~470MB weights.
 */
class LocalEmbeddingServiceTest {

  private LocalEmbeddingService service() {
    SachetProperties p = new SachetProperties();
    p.setEmbeddingModelDir("./models/paraphrase-multilingual-minilm-l12-v2");
    return new LocalEmbeddingService(p);
  }

  @Test
  void dimsAndNormalization() {
    long t0 = System.currentTimeMillis();
    float[] v = service().embed("The emergency communication process requires approval.");
    System.out.println("EMBED_MS=" + (System.currentTimeMillis() - t0));
    assertEquals(384, v.length);
    double norm = 0;
    for (float f : v) {
      norm += (double) f * f;
    }
    assertTrue(Math.abs(Math.sqrt(norm) - 1.0) < 1e-4, "must be L2-normalized");
  }

  @Test
  void deterministic() {
    var s = service();
    float[] a = s.embed("Quarterly budget review schedule.");
    float[] b = s.embed("Quarterly budget review schedule.");
    assertTrue(FloatArrayCodec.cosine(a, b) > 0.9999);
  }

  @Test
  void paraphraseBeatsUnrelated() {
    var s = service();
    float[] q = s.embed("How are emergency alerts approved?");
    float[] related = s.embed("The emergency communication process requires district approval.");
    float[] unrelated = s.embed("Quarterly budget review schedule for telecom circles.");
    double simRelated = FloatArrayCodec.cosine(q, related);
    double simUnrelated = FloatArrayCodec.cosine(q, unrelated);
    System.out.println("RELATED_SIM=" + simRelated + " UNRELATED_SIM=" + simUnrelated);
    assertTrue(simRelated > simUnrelated + 0.15,
        "paraphrase must clearly beat unrelated: " + simRelated + " vs " + simUnrelated);
  }

  @Test
  void hindiEmbeds() {
    float[] v = service().embed("आपातकालीन संचार प्रक्रिया के लिए अनुमोदन आवश्यक है।");
    assertEquals(384, v.length);
    double norm = 0;
    for (float f : v) {
      norm += (double) f * f;
    }
    assertTrue(Math.abs(Math.sqrt(norm) - 1.0) < 1e-4);
  }

  @Test
  void missingModelFailsHonestly() {
    SachetProperties p = new SachetProperties();
    p.setEmbeddingModelDir("./target/no-such-model-dir");
    p.setEmbeddingAutoDownload(false);
    assertThrows(EmbeddingUnavailableException.class,
        () -> new LocalEmbeddingService(p).embed("hello"));
  }

  @Test
  void codecRoundtrip() {
    float[] v = service().embed("codec check");
    float[] back = FloatArrayCodec.decode(FloatArrayCodec.encode(v));
    assertEquals(v.length, back.length);
    assertTrue(FloatArrayCodec.cosine(v, back) > 0.999999);
  }
}
