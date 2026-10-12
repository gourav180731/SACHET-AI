package ai.sachet.service;

import ai.sachet.config.SachetProperties;
import ai.sachet.domain.Classification;
import org.springframework.stereotype.Service;

/**
 * External-transfer gate (Phase 2 model policy). Decides whether document
 * context of a given classification may leave the SACHET boundary to an
 * external model/provider. Deny-by-default: any doubt blocks with an explicit
 * error — never a silent fallback.
 */
@Service
public class ModelPolicy {

  /** Bands that may never go external, regardless of flags. */
  private static final Classification EXTERNAL_CEILING = Classification.INTERNAL;

  private final SachetProperties props;

  public ModelPolicy(SachetProperties props) {
    this.props = props;
  }

  public boolean allowsExternal(Classification classification) {
    if (props.isSensitiveMode() && !props.isAllowExternalModels()) {
      return false;
    }
    if (classification == null) {
      return false;
    }
    return classification.rank() <= EXTERNAL_CEILING.rank();
  }

  /** Throws when transfer is forbidden — callers convert to an explicit 403. */
  public void requireExternalAllowed(Classification classification) {
    if (!allowsExternal(classification)) {
      throw new ExternalTransferBlockedException(
          "External processing is blocked for classification " + classification);
    }
  }

  public static class ExternalTransferBlockedException extends RuntimeException {
    public ExternalTransferBlockedException(String message) {
      super(message);
    }
  }
}
