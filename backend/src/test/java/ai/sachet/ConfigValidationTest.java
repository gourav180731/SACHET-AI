package ai.sachet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.sachet.config.SachetProperties;
import ai.sachet.config.SachetPropertiesValidator;
import org.junit.jupiter.api.Test;

/** Config fail-fast guards (pure unit — no Spring context needed). */
class ConfigValidationTest {

  private SachetProperties base() {
    SachetProperties p = new SachetProperties();
    p.setJwtSecret("test-only-secret-32-chars-minimum");
    return p;
  }

  @Test
  void secureDefaults() {
    SachetProperties d = new SachetProperties();
    assertTrue(d.isSensitiveMode());
    assertFalse(d.isAllowExternalModels());
    assertFalse(d.isFeatureWebSearch());
    assertFalse(d.isFeatureCrossTenantRetrieval());
    assertFalse(d.isFeatureVerboseLogging());
  }

  @Test
  void validTestConfigPasses() {
    SachetPropertiesValidator.validate(base());
  }

  @Test
  void placeholderSecretForbiddenOutsideDev() {
    SachetProperties p = base();
    p.setAppEnv("prod");
    p.setJwtSecret(SachetProperties.DEV_JWT_SENTINEL);
    assertThrows(IllegalStateException.class, () -> SachetPropertiesValidator.validate(p));
  }

  @Test
  void devOnlyPrefixedSecretForbiddenOutsideDev() {
    SachetProperties p = base();
    p.setAppEnv("prod");
    p.setJwtSecret("dev-only-local-dev-secret-00000000");
    assertThrows(IllegalStateException.class, () -> SachetPropertiesValidator.validate(p));
  }

  @Test
  void shortSecretRejectedEverywhere() {
    SachetProperties p = base();
    p.setJwtSecret("tiny");
    assertThrows(IllegalStateException.class, () -> SachetPropertiesValidator.validate(p));
  }

  @Test
  void contradictorySecurityFlagsRejected() {
    SachetProperties p = base();
    p.setSensitiveMode(true);
    p.setAllowExternalModels(true);
    assertThrows(IllegalStateException.class, () -> SachetPropertiesValidator.validate(p));
  }

  @Test
  void expiryBoundsEnforced() {
    SachetProperties p = base();
    p.setJwtExpireMin(0);
    assertThrows(IllegalStateException.class, () -> SachetPropertiesValidator.validate(p));
    p.setJwtExpireMin(1500);
    assertThrows(IllegalStateException.class, () -> SachetPropertiesValidator.validate(p));
  }
}
