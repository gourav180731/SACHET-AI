package ai.sachet.rag;

/**
 * Compact portable encoding of float vectors for the {@code embedding} column
 * (VARCHAR, valid on PostgreSQL and H2 — no native vector type required).
 * Comma-separated base-10 floats; null stays null.
 */
public final class FloatArrayCodec {

  private FloatArrayCodec() {
  }

  public static String encode(float[] vec) {
    if (vec == null) {
      return null;
    }
    StringBuilder sb = new StringBuilder(vec.length * 8);
    for (int i = 0; i < vec.length; i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(Float.toString(vec[i]));
    }
    return sb.toString();
  }

  public static float[] decode(String s) {
    if (s == null || s.isEmpty()) {
      return null;
    }
    String[] parts = s.split(",");
    float[] vec = new float[parts.length];
    for (int i = 0; i < parts.length; i++) {
      vec[i] = Float.parseFloat(parts[i]);
    }
    return vec;
  }

  /** Cosine similarity. Returns 0 when either side is degenerate. */
  public static double cosine(float[] a, float[] b) {
    if (a == null || b == null || a.length != b.length || a.length == 0) {
      return 0;
    }
    double dot = 0, na = 0, nb = 0;
    for (int i = 0; i < a.length; i++) {
      dot += (double) a[i] * b[i];
      na += (double) a[i] * a[i];
      nb += (double) b[i] * b[i];
    }
    if (na == 0 || nb == 0) {
      return 0;
    }
    return dot / (Math.sqrt(na) * Math.sqrt(nb));
  }
}
