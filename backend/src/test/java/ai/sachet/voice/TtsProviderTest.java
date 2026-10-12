package ai.sachet.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.sachet.config.SachetProperties;
import ai.sachet.domain.Classification;
import ai.sachet.service.ModelPolicy;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Speech-synthesis tests.
 *
 * <p>No network is used: the provider is never invoked with a real credential,
 * so these assert configuration, language mapping and the external-transfer gate
 * rather than audio quality. Actual synthesis requires a valid API key and is
 * verified manually.
 */
class TtsProviderTest {

  private static SachetProperties props(String provider, String key) {
    SachetProperties p = new SachetProperties();
    p.setTtsProvider(provider);
    p.setGeminiApiKey(key);
    p.setJwtSecret("unit-test-secret-that-is-long-enough-32");
    p.setAppEnv("dev");
    return p;
  }

  // ---------- honest capability reporting ----------

  @Test
  @DisplayName("tts: no provider selected reports NOT_CONFIGURED")
  void noProviderIsNotConfigured() {
    GeminiTtsProvider t = new GeminiTtsProvider(props("none", ""));
    assertEquals(TtsProvider.Status.NOT_CONFIGURED, t.status());
    assertNotNull(t.unavailableReason());
  }

  @Test
  @DisplayName("tts: provider selected without a key reports MISSING_CREDENTIAL")
  void missingCredentialIsReported() {
    GeminiTtsProvider t = new GeminiTtsProvider(props("gemini", ""));
    assertEquals(TtsProvider.Status.MISSING_CREDENTIAL, t.status());
    assertTrue(t.unavailableReason().contains("SACHET_GEMINI_API_KEY"));
  }

  @Test
  @DisplayName("tts: provider with a key reports AVAILABLE")
  void withCredentialIsAvailable() {
    GeminiTtsProvider t = new GeminiTtsProvider(props("gemini", "test-key-not-real"));
    assertEquals(TtsProvider.Status.AVAILABLE, t.status());
    assertNull(t.unavailableReason());
  }

  @Test
  @DisplayName("tts: unavailable reason never leaks the credential")
  void reasonDoesNotLeakKey() {
    GeminiTtsProvider t = new GeminiTtsProvider(props("gemini", ""));
    String reason = t.unavailableReason();
    assertFalse(reason != null && reason.contains("test-key"));
    // The env var name is fine to mention; a secret value is not.
    assertTrue(reason.contains("SACHET_GEMINI_API_KEY"));
  }

  // ---------- language coverage, including the requirement ----------

  @Test
  @DisplayName("tts: Maithili is offered as a supported language")
  void maithiliIsSupported() {
    GeminiTtsProvider t = new GeminiTtsProvider(props("gemini", "k"));
    assertTrue(t.supportedLanguages().contains("mai"),
        "Maithili must be offered - it is an explicit requirement");
  }

  @Test
  @DisplayName("tts: major Indian languages are all offered")
  void indianLanguagesCovered() {
    GeminiTtsProvider t = new GeminiTtsProvider(props("gemini", "k"));
    for (String code : List.of("hi", "bn", "te", "mr", "ta", "gu", "kn", "ml", "pa", "ur", "or", "as")) {
      assertTrue(t.supportedLanguages().contains(code), code + " must be supported");
    }
  }

  @Test
  @DisplayName("tts: Maithili maps to the mai-IN BCP-47 tag")
  void maithiliMapsToBcp47() {
    assertEquals("mai-IN", GeminiTtsProvider.normalizeLanguage("mai"));
    assertEquals("mai-IN", GeminiTtsProvider.normalizeLanguage("mai-IN"));
    assertEquals("mai-IN", GeminiTtsProvider.normalizeLanguage("MAI-in"));
  }

  @Test
  @DisplayName("tts: other languages map to region-qualified tags")
  void languageMapping() {
    assertEquals("hi-IN", GeminiTtsProvider.normalizeLanguage("hi"));
    assertEquals("ta-IN", GeminiTtsProvider.normalizeLanguage("ta-IN"));
    assertEquals("kok-IN", GeminiTtsProvider.normalizeLanguage("kok"));
  }

  @Test
  @DisplayName("tts: unknown or absent language falls back to English rather than failing")
  void unknownLanguageFallsBack() {
    assertEquals("en-IN", GeminiTtsProvider.normalizeLanguage(null));
    assertEquals("en-IN", GeminiTtsProvider.normalizeLanguage("  "));
    assertEquals("en-IN", GeminiTtsProvider.normalizeLanguage("xx"));
  }

  @Test
  @DisplayName("tts: default model is the one that documents Maithili")
  void defaultModelSupportsMaithili() {
    GeminiTtsProvider t = new GeminiTtsProvider(props("gemini", "k"));
    assertEquals("gemini-3.8-flash-tts", t.modelId());
  }

  @Test
  @DisplayName("tts: synthesis refuses when unavailable instead of faking audio")
  void refusesWhenUnavailable() {
    GeminiTtsProvider t = new GeminiTtsProvider(props("none", ""));
    assertThrows(IllegalStateException.class, () -> t.synthesize("hello", "en"));
  }

  // ---------- external transfer gate ----------

  @Test
  @DisplayName("policy: sensitive mode blocks external synthesis")
  void sensitiveModeBlocksExternal() {
    SachetProperties p = props("gemini", "k");
    p.setSensitiveMode(true);
    p.setAllowExternalModels(false);
    ModelPolicy policy = new ModelPolicy(p);
    assertFalse(policy.allowsExternal(Classification.INTERNAL));
    assertThrows(ModelPolicy.ExternalTransferBlockedException.class,
        () -> policy.requireExternalAllowed(Classification.INTERNAL));
  }

  @Test
  @DisplayName("policy: sensitive mode cannot be combined with external models at startup")
  void validatorRejectsUnsafeCombination() {
    SachetProperties p = props("gemini", "k");
    p.setSensitiveMode(true);
    p.setAllowExternalModels(true);
    assertThrows(IllegalStateException.class,
        () -> ai.sachet.config.SachetPropertiesValidator.validate(p));
  }

  @Test
  @DisplayName("policy: external allowed only up to INTERNAL, never for SECRET")
  void classificationCeilingStillApplies() {
    SachetProperties p = props("gemini", "k");
    p.setSensitiveMode(false);
    p.setAllowExternalModels(true);
    ModelPolicy policy = new ModelPolicy(p);
    assertTrue(policy.allowsExternal(Classification.PUBLIC));
    assertTrue(policy.allowsExternal(Classification.INTERNAL));
    assertFalse(policy.allowsExternal(Classification.RESTRICTED));
    assertFalse(policy.allowsExternal(Classification.CONFIDENTIAL));
    assertFalse(policy.allowsExternal(Classification.SECRET));
  }
}