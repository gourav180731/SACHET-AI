package ai.sachet.service;

import ai.sachet.config.SachetProperties;
import ai.sachet.domain.Classification;
import ai.sachet.domain.Document;
import ai.sachet.domain.User;
import ai.sachet.repo.DocumentRepository;
import ai.sachet.repo.UserRepository;
import ai.sachet.security.SachetPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Default implementation of VoiceGateway (Phase 7).
 * 
 * Security: voice must obey the same authorization model as text.
 * Voice must NOT bypass: authentication, document permissions,
 * export restrictions, tool permissions.
 */
@Service
public class VoiceGatewayImpl implements VoiceGateway {

  private static final Logger log = LoggerFactory.getLogger(VoiceGatewayImpl.class);

  private final SachetProperties props;
  private final DocumentRepository docRepo;
  private final UserRepository userRepo;

  public VoiceGatewayImpl(SachetProperties props,
      DocumentRepository docRepo,
      UserRepository userRepo) {
    this.props = props;
    this.docRepo = docRepo;
    this.userRepo = userRepo;
  }

  @Override
  @Transactional
  public VoiceSession startSession(SachetPrincipal principal, String preferredLanguage) {
    // Security: check if voice is enabled for principal's clearance
    String clearance = principal.getClearance();
    if (clearance == null) {
      log.warn("Voice start denied: no clearance for user {}", principal.getUserId());
      throw new SecurityException("Voice not authorized - no clearance assigned.");
    }

    // Check if voice is enabled for this clearance level
    boolean enabled = switch (clearance.toUpperCase()) {
      case "SECRET", "CONFIDENTIAL", "RESTRICTED" -> true;
      case "INTERNAL" -> true;
      case "PUBLIC" -> true;
      default -> false;
    };

    if (!enabled) {
      log.warn("Voice disabled for user {} with clearance {}", principal.getUserId(), principal.getClearance());
      throw new SecurityException("Voice not authorized for your clearance level.");
    }

    // Build accessible clearance bands
    List<String> accessibleBands = new ArrayList<>();
    switch (clearance.toUpperCase()) {
      case "SECRET":
        accessibleBands.addAll(Arrays.asList("PUBLIC", "INTERNAL", "RESTRICTED", "CONFIDENTIAL", "SECRET"));
        break;
      case "CONFIDENTIAL":
        accessibleBands.addAll(Arrays.asList("PUBLIC", "INTERNAL", "RESTRICTED", "CONFIDENTIAL"));
        break;
      case "RESTRICTED":
        accessibleBands.addAll(Arrays.asList("PUBLIC", "INTERNAL", "RESTRICTED"));
        break;
      case "INTERNAL":
        accessibleBands.addAll(Arrays.asList("PUBLIC", "INTERNAL"));
        break;
      case "PUBLIC":
        accessibleBands.add("PUBLIC");
        break;
    }

    VoiceSession session = new VoiceSession(
        "voice-" + System.currentTimeMillis(),
        principal,
        preferredLanguage != null ? preferredLanguage : "INTERNAL",
        false,  // wake word enabled if user account is enabled
        System.currentTimeMillis(),
        principal.getScopes() != null ? principal.getScopes() : new ArrayList<>(),
        accessibleBands);

    log.info("Voice session started for user {} with clearance {}", principal.getUserId(), principal.getClearance());
    return session;
  }

  @Override
  @Transactional
  public void endSession(VoiceSession session) {
    log.info("Voice session ended for user {} after {} seconds",
        session.principal().getUserId(),
        (System.currentTimeMillis() - session.sessionStart()) / 1000);
    // In a full implementation, would clean up resources, persist session summary, etc.
  }

  @Override
  @Transactional
  public boolean isVoiceEnabled(SachetPrincipal principal, String classification) {
    // Security: check if voice is enabled for principal's clearance
    String clearance = principal.getClearance();
    if (clearance == null) return false;
    return switch (clearance.toUpperCase()) {
      case "SECRET", "CONFIDENTIAL", "RESTRICTED" -> true;
      case "INTERNAL" -> true;
      case "PUBLIC" -> true;
      default -> false;
    };
  }

  @Override
  public List<String> getSupportedSttLanguages() {
    return Arrays.asList(
        "en-IN", "hi-IN", "bn-IN", "te-IN", "mr-IN", "ta-IN",
        "gu-IN", "kn-IN", "ml-IN", "pa-IN", "ur-IN");
  }

  @Override
  public List<String> getSupportedTtsLanguages() {
    return Arrays.asList(
        "en-IN", "hi-IN", "bn-IN", "te-IN", "mr-IN", "ta-IN",
        "gu-IN", "kn-IN", "ml-IN", "pa-IN", "ur-IN", "ar-IN");
  }

  @Override
  public VoiceSessionStatus getSessionStatus(VoiceSession session) {
    VoiceSessionStatus status = new VoiceSessionStatus();
    status.isActive = true;
    status.sessionId = session.sessionId();
    status.startedAt = "recent";
    status.lastHeardAt = "never";
    status.currentLanguage = session.language();
    status.consecutiveSilenceSeconds = 0;
    status.wakeWordDetected = session.wakeWordEnabled();
    return status;
  }
}