package ai.sachet.service;

import ai.sachet.security.SachetPrincipal;
import java.util.List;

/** Voice Gateway abstraction layer (Phase 7).
 * 
 * Security property: Voice must obey the same authorization model as text.
 * Voice must NOT bypass:
 *   - authentication
 *   - document permissions
 *   - export restrictions
 *   - tool permissions
 * 
 * Provider abstraction: rest of SACHET communicates with the gateway rather
 * than directly with a provider. Allows organization to change models without
 * rewriting the application.
 */
public interface VoiceGateway {

  /** Start voice session with authentication and policy check. */
  VoiceSession startSession(SachetPrincipal principal, String preferredLanguage);

  /** End voice session. */
  void endSession(VoiceSession session);

  /** Check if voice is enabled for the given principal and classification. */
  boolean isVoiceEnabled(SachetPrincipal principal, String classification);

  /** Get supported languages for STT. */
  List<String> getSupportedSttLanguages();

  /** Get supported languages for TTS. */
  List<String> getSupportedTtsLanguages();

  /** Get current voice session status. */
  VoiceSessionStatus getSessionStatus(VoiceSession session);
}

/** Voice session state. */
class VoiceSessionStatus {
  boolean isActive;
  String sessionId;
  String startedAt;
  String lastHeardAt;
  String currentLanguage;
  int consecutiveSilenceSeconds;
  boolean wakeWordDetected;

  boolean isActive() { return isActive; }
}

/** Voice session carrying security context. */
class VoiceSession {
  String sessionId;
  SachetPrincipal principal;
  String language;
  boolean wakeWordEnabled;
  long sessionStart;
  List<String> activePermissions;
  List<String> activeClearanceBands;

  // No-args constructor for serialization/test reflection
  VoiceSession() {
  }

  VoiceSession(String sessionId, SachetPrincipal principal, String language,
      boolean wakeWordEnabled, long sessionStart, List<String> activePermissions,
      List<String> activeClearanceBands) {
    this.sessionId = sessionId;
    this.principal = principal;
    this.language = language;
    this.wakeWordEnabled = wakeWordEnabled;
    this.sessionStart = sessionStart;
    this.activePermissions = activePermissions;
    this.activeClearanceBands = activeClearanceBands;
  }

  String sessionId() { return sessionId; }
  SachetPrincipal principal() { return principal; }
  String language() { return language; }
  boolean wakeWordEnabled() { return wakeWordEnabled; }
  long sessionStart() { return sessionStart; }
  List<String> activePermissions() { return activePermissions; }
  List<String> activeClearanceBands() { return activeClearanceBands; }

  /** Check if a document operation is permitted for this session. */
  boolean canAccessDocument(String documentId, String requiredClassification) {
    // Security: intersect session's accessible clearance bands with required classification
    if (activeClearanceBands == null || requiredClassification == null) {
      return false;
    }
    // Simple check: if required classification is in session's accessible bands
    return activeClearanceBands.contains(requiredClassification.toUpperCase());
  }
}

/** STT (Speech-to-Text) provider interface. */
interface SttProvider {

  /** Transcribe audio bytes to text. */
  String transcribe(byte[] audio, String language) throws STTException;

  /** Get supported languages. */
  List<String> getSupportedLanguages();

  /** Get language quality status. */
  LanguageQualityStatus getQualityStatus(String language);
}

class STTException extends Exception {
  private static final long serialVersionUID = 1L;
}

/** Language quality status for STT/TTS. */
enum LanguageQualityStatus {
  EXCELLENT,
  GOOD,
  FAIR,
  POOR,
  UNSUPPORTED
}

/** TTS (Text-to-Speech) provider interface. */
interface TtsProvider {

  /** Synthesize text to audio bytes. */
  byte[] synthesize(String text, String language) throws TTSException;

  /** Get supported languages. */
  List<String> getSupportedLanguages();

  /** Get language quality status. */
  LanguageQualityStatus getQualityStatus(String language);
}

class TTSException extends Exception {
  private static final long serialVersionUID = 1L;
}