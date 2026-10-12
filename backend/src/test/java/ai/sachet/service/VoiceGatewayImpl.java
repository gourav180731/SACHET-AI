package ai.sachet.service;

import ai.sachet.security.SachetPrincipal;
import java.util.List;
import java.util.Arrays;

/** Simple VoiceGateway implementation for testing. */
@org.springframework.stereotype.Service
public class VoiceGatewayImpl implements VoiceGateway {

  @Override
  public VoiceSession startSession(SachetPrincipal principal, String preferredLanguage) {
    VoiceSession session = new VoiceSession();
    session.sessionId = "test-session-" + System.currentTimeMillis();
    session.principal = principal;
    session.language = preferredLanguage != null ? preferredLanguage : "INTERNAL";
    session.wakeWordEnabled = true;
    session.sessionStart = System.currentTimeMillis();
    session.activePermissions = principal.getScopes();
    session.activeClearanceBands = getAccessibleClearanceBands(principal);
    return session;
  }

  @Override
  public void endSession(VoiceSession session) {
    // No-op for testing
  }

  @Override
  public boolean isVoiceEnabled(SachetPrincipal principal, String classification) {
    // For testing: enable voice for INTERNAL and below
    return classification != null && (classification.equals("INTERNAL") || classification.equals("PUBLIC"));
  }

  @Override
  public List<String> getSupportedSttLanguages() {
    return List.of("en-IN", "hi-IN", "bn-IN", "te-IN", "mr-IN", "ta-IN", "gu-IN", "kn-IN", "ml-IN", "pa-IN", "ur-IN");
  }

  @Override
  public List<String> getSupportedTtsLanguages() {
    return List.of("en-IN", "hi-IN", "bn-IN", "te-IN", "mr-IN", "ta-IN", "gu-IN", "kn-IN", "ml-IN", "pa-IN", "ur-IN", "ar-IN");
  }

  @Override
  public VoiceSessionStatus getSessionStatus(VoiceSession session) {
    VoiceSessionStatus status = new VoiceSessionStatus();
    status.isActive = true;
    status.sessionId = session.sessionId();
    status.startedAt = Long.toString(session.sessionStart());
    status.lastHeardAt = Long.toString(session.sessionStart());
    status.currentLanguage = session.language();
    status.consecutiveSilenceSeconds = 0;
    status.wakeWordDetected = session.wakeWordEnabled();
    return status;
  }

  private java.util.List<String> getAccessibleClearanceBands(SachetPrincipal principal) {
    String clearance = principal.getClearance();
    java.util.List<String> bands = new java.util.ArrayList<>();
    if (clearance.equals("SECRET")) {
      bands.addAll(java.util.Arrays.asList("PUBLIC", "INTERNAL", "RESTRICTED", "CONFIDENTIAL", "SECRET"));
    } else if (clearance.equals("CONFIDENTIAL")) {
      bands.addAll(java.util.Arrays.asList("PUBLIC", "INTERNAL", "RESTRICTED", "CONFIDENTIAL"));
    } else if (clearance.equals("RESTRICTED")) {
      bands.addAll(java.util.Arrays.asList("PUBLIC", "INTERNAL", "RESTRICTED"));
    } else if (clearance.equals("INTERNAL")) {
      bands.addAll(java.util.Arrays.asList("PUBLIC", "INTERNAL"));
    } else {
      bands.add("PUBLIC");
    }
    return bands;
  }
}