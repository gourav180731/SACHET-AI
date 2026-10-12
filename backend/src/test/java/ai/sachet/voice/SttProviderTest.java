package ai.sachet.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.sachet.config.SachetProperties;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Speech-to-text unit tests.
 * Asserts capability reporting, language support, and sanitization logic.
 */
class SttProviderTest {

  private static SachetProperties props(String provider, String key) {
    SachetProperties p = new SachetProperties();
    p.setSttProvider(provider);
    p.setGeminiApiKey(key);
    p.setJwtSecret("unit-test-secret-that-is-long-enough-32");
    p.setAppEnv("dev");
    return p;
  }

  @Test
  @DisplayName("stt: no provider selected reports NOT_CONFIGURED")
  void noProviderIsNotConfigured() {
    GeminiSttProvider s = new GeminiSttProvider(props("none", ""));
    assertEquals(SttProvider.Status.NOT_CONFIGURED, s.status());
    assertNotNull(s.unavailableReason());
  }

  @Test
  @DisplayName("stt: provider selected without a key reports MISSING_CREDENTIAL")
  void missingCredentialIsReported() {
    GeminiSttProvider s = new GeminiSttProvider(props("gemini", ""));
    assertEquals(SttProvider.Status.MISSING_CREDENTIAL, s.status());
    assertTrue(s.unavailableReason().contains("SACHET_GEMINI_API_KEY"));
  }

  @Test
  @DisplayName("stt: provider with a key reports AVAILABLE")
  void withCredentialIsAvailable() {
    GeminiSttProvider s = new GeminiSttProvider(props("gemini", "test-key-not-real"));
    assertEquals(SttProvider.Status.AVAILABLE, s.status());
    assertNull(s.unavailableReason());
  }

  @Test
  @DisplayName("stt: Maithili is in supported languages list")
  void maithiliIsSupported() {
    GeminiSttProvider s = new GeminiSttProvider(props("gemini", "k"));
    assertTrue(s.supportedLanguages().contains("mai"), "Maithili (mai) must be supported for STT");
  }

  @Test
  @DisplayName("stt: Punjabi and other Indian regional languages supported")
  void regionalLanguagesSupported() {
    GeminiSttProvider s = new GeminiSttProvider(props("gemini", "k"));
    for (String code : List.of("pa", "hi", "bn", "mr", "ta", "te", "gu", "kn", "ml", "ur")) {
      assertTrue(s.supportedLanguages().contains(code), code + " must be supported");
    }
  }

  @Test
  @DisplayName("stt: mime type sanitization strips codec parameters")
  void sanitizeMimeType() {
    assertEquals("audio/webm", GeminiSttProvider.sanitizeMimeType("audio/webm;codecs=opus"));
    assertEquals("audio/wav", GeminiSttProvider.sanitizeMimeType("audio/wav"));
    assertEquals("audio/webm", GeminiSttProvider.sanitizeMimeType(""));
    assertEquals("audio/webm", GeminiSttProvider.sanitizeMimeType(null));
  }

  @Test
  @DisplayName("stt: language description provides informative regional prompt")
  void languageDescription() {
    assertTrue(GeminiSttProvider.describeLanguage("mai-IN").contains("Maithili"));
    assertTrue(GeminiSttProvider.describeLanguage("pa-IN").contains("Punjabi"));
    assertTrue(GeminiSttProvider.describeLanguage("hi-IN").contains("Hindi"));
  }
}
