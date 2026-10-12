package ai.sachet.domain;

/**
 * Document/user clearance bands. Initial logical levels (configurable per
 * deployment; NOT official government markings — see docs/DATA_CLASSIFICATION.md).
 * Rank order governs the clearance gate: a user may only see documents at or
 * below their clearance.
 */
public enum Classification {
  PUBLIC(0),
  INTERNAL(1),
  RESTRICTED(2),
  CONFIDENTIAL(3),
  SECRET(4);

  private final int rank;

  Classification(int rank) {
    this.rank = rank;
  }

  public int rank() {
    return rank;
  }

  /** True when a holder of {@code clearance} may access this band. */
  public boolean accessibleTo(Classification clearance) {
    return clearance != null && clearance.rank >= this.rank;
  }
}
