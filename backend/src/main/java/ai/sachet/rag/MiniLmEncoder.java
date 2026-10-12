package ai.sachet.rag;

import java.util.stream.IntStream;

/**
 * Pure-Java BERT-family encoder (MiniLM-L12-H384 shape), exact inference math:
 * embeddings + 12 × (multi-head attention → residual+LayerNorm → GELU FFN →
 * residual+LayerNorm), then mean pooling + L2 normalization.
 *
 * No native libraries: the same safetensors weights any ONNX runtime would
 * use, evaluated with plain float arithmetic. Row-independent loops run on the
 * common ForkJoinPool (deterministic: rows never share state).
 */
public final class MiniLmEncoder {

  private static final int HEADS = 12;
  private static final double LN_EPS = 1e-12;

  private final int hidden;
  private final int headDim;
  private final int ffn;
  private final SafetensorsReader weights;
  private final float[][] position;
  private final float[][] tokType;
  private final float[] embLnW;
  private final float[] embLnB;
  private final Layer[] layers;

  private static final class Layer {
    float[][] wq, wk, wv, wo, wi, wo2;
    float[] bq, bk, bv, bo, bi, bo2, ln1w, ln1b, ln2w, ln2b;
  }

  public MiniLmEncoder(SafetensorsReader weights) {
    this.weights = weights;
    // NOTE: sentence-transformers export layout (no "bert." prefix).
    var wordInfo = weights.info("embeddings.word_embeddings.weight");
    this.hidden = Math.toIntExact(wordInfo.shape().get(1));
    if (hidden % HEADS != 0) {
      throw new EmbeddingUnavailableException("Unexpected hidden size: " + hidden);
    }
    this.headDim = hidden / HEADS;
    this.ffn = hidden * 4;
    this.position = weights.readMatrix("embeddings.position_embeddings.weight");
    this.tokType = weights.readMatrix("embeddings.token_type_embeddings.weight");
    this.embLnW = weights.readVector("embeddings.LayerNorm.weight");
    this.embLnB = weights.readVector("embeddings.LayerNorm.bias");
    this.layers = new Layer[12];
    for (int i = 0; i < 12; i++) {
      String p = "encoder.layer." + i + ".";
      Layer l = new Layer();
      l.wq = weights.readMatrix(p + "attention.self.query.weight");
      l.bq = weights.readVector(p + "attention.self.query.bias");
      l.wk = weights.readMatrix(p + "attention.self.key.weight");
      l.bk = weights.readVector(p + "attention.self.key.bias");
      l.wv = weights.readMatrix(p + "attention.self.value.weight");
      l.bv = weights.readVector(p + "attention.self.value.bias");
      l.wo = weights.readMatrix(p + "attention.output.dense.weight");
      l.bo = weights.readVector(p + "attention.output.dense.bias");
      l.ln1w = weights.readVector(p + "attention.output.LayerNorm.weight");
      l.ln1b = weights.readVector(p + "attention.output.LayerNorm.bias");
      l.wi = weights.readMatrix(p + "intermediate.dense.weight");
      l.bi = weights.readVector(p + "intermediate.dense.bias");
      l.wo2 = weights.readMatrix(p + "output.dense.weight");
      l.bo2 = weights.readVector(p + "output.dense.bias");
      l.ln2w = weights.readVector(p + "output.LayerNorm.weight");
      l.ln2b = weights.readVector(p + "output.LayerNorm.bias");
      assertShape(l.wq, hidden, hidden, p + "q");
      assertShape(l.wi, ffn, hidden, p + "intermediate");
      assertShape(l.wo2, hidden, ffn, p + "output");
      layers[i] = l;
    }
  }

  public int hidden() {
    return hidden;
  }

  private static void assertShape(float[][] m, int rows, int cols, String what) {
    if (m.length != rows || m[0].length != cols) {
      throw new EmbeddingUnavailableException("Unexpected weight shape: " + what);
    }
  }

  /** Encodes token IDs to a normalized sentence vector (mean pooling). */
  public float[] encode(long[] tokenIds) {
    int n = tokenIds.length;
    if (n == 0) {
      throw new EmbeddingUnavailableException("Embedding failed: empty input.");
    }
    float[][] x = new float[n][hidden];
    for (int t = 0; t < n; t++) {
      float[] word = weights.readRow("embeddings.word_embeddings.weight",
          Math.toIntExact(tokenIds[t]));
      float[] pos = position[t];
      float[] typ = tokType[0];
      for (int h = 0; h < hidden; h++) {
        x[t][h] = word[h] + pos[h] + typ[h];
      }
      layerNormInPlace(x[t], embLnW, embLnB);
    }
    for (Layer layer : layers) {
      x = block(x, layer);
    }
    double[] sum = new double[hidden];
    for (float[] row : x) {
      for (int h = 0; h < hidden; h++) {
        sum[h] += row[h];
      }
    }
    float[] out = new float[hidden];
    double norm = 0;
    for (int h = 0; h < hidden; h++) {
      out[h] = (float) (sum[h] / n);
      norm += (double) out[h] * out[h];
    }
    norm = Math.sqrt(norm);
    if (norm == 0) {
      throw new EmbeddingUnavailableException("Embedding failed: zero vector.");
    }
    for (int h = 0; h < hidden; h++) {
      out[h] /= (float) norm;
    }
    return out;
  }

  private float[][] block(float[][] x, Layer l) {
    int n = x.length;
    float[][] q = linear(x, l.wq, l.bq);
    float[][] k = linear(x, l.wk, l.bk);
    float[][] v = linear(x, l.wv, l.bv);
    // Per-head attention (heads are independent → parallel).
    float[][][] attn = new float[HEADS][][];
    IntStream.range(0, HEADS).parallel().forEach(h -> {
      float[][] scores = new float[n][n];
      for (int i = 0; i < n; i++) {
        for (int j = 0; j < n; j++) {
          double s = 0;
          for (int d = 0; d < headDim; d++) {
            s += (double) q[i][h * headDim + d] * k[j][h * headDim + d];
          }
          scores[i][j] = (float) (s / Math.sqrt(headDim));
        }
        softmaxInPlace(scores[i]);
      }
      float[][] o = new float[n][headDim];
      for (int i = 0; i < n; i++) {
        for (int j = 0; j < n; j++) {
          float p = scores[i][j];
          for (int d = 0; d < headDim; d++) {
            o[i][d] += p * v[j][h * headDim + d];
          }
        }
      }
      attn[h] = o;
    });
    float[][] joined = new float[n][hidden];
    for (int i = 0; i < n; i++) {
      for (int h = 0; h < HEADS; h++) {
        System.arraycopy(attn[h][i], 0, joined[i], h * headDim, headDim);
      }
    }
    float[][] a = linear(joined, l.wo, l.bo);
    for (int i = 0; i < n; i++) {
      for (int h = 0; h < hidden; h++) {
        a[i][h] += x[i][h];
      }
      layerNormInPlace(a[i], l.ln1w, l.ln1b);
    }
    float[][] inter = linear(a, l.wi, l.bi);
    for (int i = 0; i < n; i++) {
      for (int h = 0; h < ffn; h++) {
        inter[i][h] = (float) gelu(inter[i][h]);
      }
    }
    float[][] o2 = linear(inter, l.wo2, l.bo2);
    for (int i = 0; i < n; i++) {
      for (int h = 0; h < hidden; h++) {
        o2[i][h] += a[i][h];
      }
      layerNormInPlace(o2[i], l.ln2w, l.ln2b);
    }
    return o2;
  }

  /** y = Wx + b, W row-major [out][in]. Rows are independent → parallel. */
  static float[][] linear(float[][] x, float[][] w, float[] b) {
    int n = x.length;
    int out = w.length;
    int in = w[0].length;
    float[][] y = new float[n][out];
    IntStream.range(0, n).parallel().forEach(i -> {
      float[] xi = x[i];
      float[] yi = y[i];
      for (int o = 0; o < out; o++) {
        double s = b[o];
        float[] wo = w[o];
        for (int j = 0; j < in; j++) {
          s += (double) wo[j] * xi[j];
        }
        yi[o] = (float) s;
      }
    });
    return y;
  }

  static void layerNormInPlace(float[] x, float[] gamma, float[] beta) {
    double mean = 0;
    for (float v : x) {
      mean += v;
    }
    mean /= x.length;
    double var = 0;
    for (float v : x) {
      double d = v - mean;
      var += d * d;
    }
    var /= x.length;
    double inv = 1.0 / Math.sqrt(var + LN_EPS);
    for (int i = 0; i < x.length; i++) {
      x[i] = (float) (gamma[i] * (x[i] - mean) * inv + beta[i]);
    }
  }

  static void softmaxInPlace(float[] x) {
    float max = x[0];
    for (float v : x) {
      max = Math.max(max, v);
    }
    double sum = 0;
    for (int i = 0; i < x.length; i++) {
      x[i] = (float) Math.exp(x[i] - max);
      sum += x[i];
    }
    for (int i = 0; i < x.length; i++) {
      x[i] /= (float) sum;
    }
  }

  /** Exact GELU via erf (Abramowitz–Stegun, |err| ≤ 1.5e-7). */
  static double gelu(double x) {
    return 0.5 * x * (1.0 + erf(x / Math.sqrt(2.0)));
  }

  static double erf(double x) {
    double t = 1.0 / (1.0 + 0.3275911 * Math.abs(x));
    double y = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t
        - 0.284496736) * t + 0.254829592) * t * Math.exp(-x * x);
    return x < 0 ? -y : y;
  }
}
