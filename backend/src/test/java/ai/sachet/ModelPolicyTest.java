package ai.sachet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.sachet.config.SachetProperties;
import ai.sachet.domain.Classification;
import ai.sachet.service.ModelPolicy;
import org.junit.jupiter.api.Test;

/** External-transfer gate: deny-by-default, explicit block, never silent. */
class ModelPolicyTest {

  private ModelPolicy policy(boolean sensitive, boolean allowExternal) {
    SachetProperties p = new SachetProperties();
    p.setJwtSecret("test-only-secret-32-chars-minimum");
    p.setSensitiveMode(sensitive);
    p.setAllowExternalModels(allowExternal);
    return new ModelPolicy(p);
  }

  @Test
  void sensitiveModeBlocksEverything() {
    ModelPolicy m = policy(true, false);
    for (Classification c : Classification.values()) {
      assertFalse(m.allowsExternal(c));
    }
  }

  @Test
  void ceilingBlocksRestrictedAndAbove() {
    ModelPolicy m = policy(false, true);
    assertTrue(m.allowsExternal(Classification.PUBLIC));
    assertTrue(m.allowsExternal(Classification.INTERNAL));
    assertFalse(m.allowsExternal(Classification.RESTRICTED));
    assertFalse(m.allowsExternal(Classification.CONFIDENTIAL));
    assertFalse(m.allowsExternal(Classification.SECRET));
  }

  @Test
  void nullClassificationDenied() {
    assertFalse(policy(false, true).allowsExternal(null));
  }

  @Test
  void requireThrowsExplicitBlock() {
    assertThrows(ModelPolicy.ExternalTransferBlockedException.class,
        () -> policy(true, false).requireExternalAllowed(Classification.PUBLIC));
  }
}
