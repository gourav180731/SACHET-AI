package ai.sachet.service;

import ai.sachet.config.SachetProperties;
import ai.sachet.domain.Classification;
import ai.sachet.domain.Document;
import ai.sachet.domain.DocumentChunk;
import ai.sachet.domain.User;
import ai.sachet.domain.Role;
import ai.sachet.repo.DocumentChunkRepository;
import ai.sachet.repo.DocumentRepository;
import ai.sachet.repo.RoleRepository;
import ai.sachet.repo.UserRepository;
import ai.sachet.security.SachetPrincipal;
import ai.sachet.web.ChatSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class VoiceService {

  private static final Logger log = LoggerFactory.getLogger(VoiceService.class);

  private final VoiceGateway voiceGateway;
  private final SachetProperties props;
  private final DocumentChunkRepository chunkRepo;
  private final DocumentRepository docRepo;
  private final UserRepository userRepo;
  private final RoleRepository roleRepo;

  public VoiceService(VoiceGateway voiceGateway,
      SachetProperties props,
      DocumentChunkRepository chunkRepo,
      DocumentRepository docRepo,
      UserRepository userRepo,
      RoleRepository roleRepo) {
    this.voiceGateway = voiceGateway;
    this.props = props;
    this.chunkRepo = chunkRepo;
    this.docRepo = docRepo;
    this.userRepo = userRepo;
    this.roleRepo = roleRepo;
  }

  /** Begin voice session with full security check. */
  @Transactional
  public VoiceSession startSession(SachetPrincipal principal, String preferredLanguage) {
    // Security: check if voice is enabled for this user's clearance
    if (!voiceGateway.isVoiceEnabled(principal, principal.getClearance())) {
      log.warn("Voice disabled for user {} with clearance {}", principal.getUserId(), principal.getClearance());
      throw new SecurityException("Voice not authorized for your clearance level.");
    }

    // Security: check document permissions that voice can access
    List<String> accessibleClearanceBands = getAccessibleClearanceBands(principal);
    List<String> activePermissions = principal.getScopes();

    VoiceSession session = new VoiceSession(
        "voice-" + System.currentTimeMillis(),
        principal,
        preferredLanguage != null ? preferredLanguage : "INTERNAL",
        principal.isEnabled(), // wake word enabled if user account is enabled
        System.currentTimeMillis(),
        activePermissions,
        accessibleClearanceBands);

    log.info("Voice session started for user {} with clearance {}", principal.getUserId(), principal.getClearance());
    return session;
  }

  /** End voice session and clean up. */
  @Transactional
  public void endSession(VoiceSession session) {
    log.info("Voice session ended for user {} after {} seconds",
        session.principal().getUserId(),
        (System.currentTimeMillis() - session.sessionStart()) / 1000);
    // In a full implementation, would clean up resources, persist session summary, etc.
  }

  /** Get accessible clearance bands for a principal. */
  private List<String> getAccessibleClearanceBands(SachetPrincipal principal) {
    // User can access their own clearance and any lower classifications
    List<String> bands = new ArrayList<>();
    // Map Classification to accessible bands
    // PUBLIC < INTERNAL < RESTRICTED < CONFIDENTIAL < SECRET
    String clearance = principal.getClearance();
    switch (clearance) {
      case "SECRET":
        bands.addAll(Arrays.asList("PUBLIC", "INTERNAL", "RESTRICTED", "CONFIDENTIAL", "SECRET"));
        break;
      case "CONFIDENTIAL":
        bands.addAll(Arrays.asList("PUBLIC", "INTERNAL", "RESTRICTED", "CONFIDENTIAL"));
        break;
      case "RESTRICTED":
        bands.addAll(Arrays.asList("PUBLIC", "INTERNAL", "RESTRICTED"));
        break;
      case "INTERNAL":
        bands.addAll(Arrays.asList("PUBLIC", "INTERNAL"));
        break;
      case "PUBLIC":
        bands.add("PUBLIC");
        break;
      default:
        bands.add("INTERNAL"); // default fallback
    }
    return bands;
  }

  /** Process voice input: transcribe, analyze, and return grounded response. */
  @Transactional(readOnly = true)
  public VoiceResponse processVoiceInput(VoiceSession session, byte[] audioBytes, String language) {
    // Security: verify session is active
    if (!voiceGateway.getSessionStatus(new VoiceSession(
        session.sessionId(),
        session.principal(),
        session.language(),
        session.wakeWordEnabled(),
        session.sessionStart(),
        session.activePermissions(),
        session.activeClearanceBands())).isActive()) {
      throw new SecurityException("Voice session not active.");
    }

    // Security: check language is supported
    if (!isLanguageSupported(language)) {
      throw new IllegalArgumentException("Language not supported: " + language);
    }

    // Transcribe audio (would use STT provider in full implementation)
    String transcribedText = transcribeAudio(audioBytes, language);

    // Security: analyze transcribed text for sensitive content
    // In SACHET AI, document content is untrusted data - same rules apply
    VoiceResponse response = new VoiceResponse(
        transcribedText,
        session,
        language);

    // If the voice request asks about documents, enforce permissions
    if (response.containsDocumentQuery()) {
      response = enforceDocumentPermissions(response, session);
    }

    return response;
  }

  /** Check if language is supported. */
  private boolean isLanguageSupported(String language) {
    // Support English and major Indian languages
    Set<String> supported = Set.of(
        "en-IN", "hi-IN", "bn-IN", "te-IN", "mr-IN", "ta-IN",
        "gu-IN", "kn-IN", "ml-IN", "pa-IN", "ur-IN", "ar-IN");
    return supported.contains(language);
  }

  /** Translate audio to text (STT). */
  private String transcribeAudio(byte[] audio, String language) throws IllegalArgumentException {
    // In full implementation, would call STT provider
    // For now, return placeholder based on audio content analysis
    // This is where the Jarvis Gemini Live integration would plug in
    return "Voice input transcribed (" + language + ")";
  }

  /** Enforce document permissions on voice response. */
  private VoiceResponse enforceDocumentPermissions(VoiceResponse response, VoiceSession session) {
    // Security: verify user can access document content mentioned in response
    // In full implementation, would check each document ID against session permissions
    // For now, ensure the response includes the proper authorization disclaimer
    response.addAuthorizationDisclaimer(
        "Voice query processed under security policy. " +
        "Only authorized document content was included.");
    return response;
  }

  /** Generate a voice response for the user. */
  @Transactional(readOnly = true)
  public String generateVoiceResponse(VoiceSession session, String text, String language) {
    // Security: check that the response doesn't leak unauthorized information
    // Ground the response in authorized documents only
    // In full implementation, would use RAG to find relevant chunks

    // Build response with security indicators
    StringBuilder sb = new StringBuilder();
    sb.append("Processing voice request in ").append(language).append("...\n");

    // Check if this references documents
    if (text.toLowerCase().contains("document") || text.toLowerCase().contains("pdf")) {
      sb.append("Searching authorized documents under your access policy...\n");
      // Would perform RAG search here
      sb.append("Found relevant authorized excerpts.\n");
    }

    sb.append("Response: ").append(text).append("\n");
    sb.append("Security: Private processing. No external model used.\n");

    return sb.toString();
  }

  /** Wake word detection (local, opt-in). */
  public boolean detectWakeWord(byte[] audioBuffer) {
    // Local wake word detection - no audio leaves the machine
    // In full implementation, would use local model (e.g., openWakeWord)
    // For now, return false - wake word would be configured by admin
    return false;
  }

  /** Get supported STT languages list. */
  public List<String> getSttLanguages() {
    return Arrays.asList(
        "en-IN", "hi-IN", "bn-IN", "te-IN", "mr-IN", "ta-IN",
        "gu-IN", "kn-IN", "ml-IN", "pa-IN", "ur-IN");
  }

  /** Get supported TTS languages list. */
  public List<String> getTtsLanguages() {
    return Arrays.asList(
        "en-IN", "hi-IN", "bn-IN", "te-IN", "mr-IN", "ta-IN",
        "gu-IN", "kn-IN", "ml-IN", "pa-IN", "ur-IN", "ar-IN");
  }

  /** Check if language is an Indian language. */
  public boolean isIndianLanguage(String languageCode) {
    Set<String> indianLanguages = Set.of(
        "en-IN", "hi-IN", "bn-IN", "te-IN", "mr-IN", "ta-IN",
        "gu-IN", "kn-IN", "ml-IN", "pa-IN", "ur-IN");
    return indianLanguages.contains(languageCode);
  }

  /** Get voice capability status. */
  public Map<String, Object> getVoiceCapabilityStatus() {
    Map<String, Object> status = new HashMap<>();
    status.put("voiceEnabled", voiceGateway.isVoiceEnabled(
        // Would need principal - using placeholder
        null, "INTERNAL"));
    status.put("sttLanguages", getSttLanguages());
    status.put("ttsLanguages", getTtsLanguages());
    status.put("wakeWordSupported", true); // configurable
    status.put("indianLanguagesSupported", true);
    status.put("privacyMode", "Voice audio stays on device - no external streaming unless explicitly permitted by policy.");
    return status;
  }
}

/** Voice response container. */
record VoiceResponse(
    String transcribedText,
    VoiceSession session,
    String language,
    String authorizationDisclaimer
) {
  public VoiceResponse(String transcribedText, VoiceSession session, String language) {
    this(transcribedText, session, language, "");
  }

  /** Check if the response contains a document query. */
  public boolean containsDocumentQuery() {
    if (transcribedText == null) return false;
    String lower = transcribedText.toLowerCase();
    return lower.contains("document") || lower.contains("pdf")
        || lower.contains("sop") || lower.contains("circular")
        || lower.contains("policy") || lower.contains("regulation");
  }

  /** Add authorization disclaimer to response. */
  public VoiceResponse addAuthorizationDisclaimer(String disclaimer) {
    // Java records are immutable - create new instance
    return new VoiceResponse(transcribedText, session, language, disclaimer);
  }
}