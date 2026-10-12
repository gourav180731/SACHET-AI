package ai.sachet.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Fail-fast configuration guard. Runs at startup; any violation aborts the boot
 * with an explicit message instead of running insecurely. Pure logic lives in
 * {@link #validate(SachetProperties)} so it is unit-testable without a context.
 */
@Component
public class SachetPropertiesValidator {

  private static final Logger log = LoggerFactory.getLogger(SachetPropertiesValidator.class);

  private final SachetProperties props;

  public SachetPropertiesValidator(SachetProperties props) {
    this.props = props;
  }

  @PostConstruct
  void checkOnStartup() {
    validate(props);
    log.info("SACHET configuration validated (env={}, sensitiveMode={})", props.getAppEnv(),
        props.isSensitiveMode());
  }

  public static void validate(SachetProperties p) {
    if (p.getJwtSecret() == null || p.getJwtSecret().length() < 32) {
      throw new IllegalStateException("SACHET_JWT_SECRET must be at least 32 characters");
    }
    if (!"dev".equals(p.getAppEnv()) && (SachetProperties.DEV_JWT_SENTINEL.equals(p.getJwtSecret())
        || p.getJwtSecret().startsWith(SachetProperties.DEV_ONLY_PREFIX))) {
      throw new IllegalStateException("Dev-only JWT secret is forbidden when APP_ENV != 'dev'");
    }
    if (p.isSensitiveMode() && p.isAllowExternalModels()) {
      throw new IllegalStateException("ALLOW_EXTERNAL_MODELS=true is forbidden while SENSITIVE_MODE=true");
    }
    if (p.getJwtExpireMin() <= 0 || p.getJwtExpireMin() > 24 * 60) {
      throw new IllegalStateException("JWT_EXPIRE_MIN must be within 1..1440");
    }
    if (p.getIngestionMaxFileBytes() <= 0) {
      throw new IllegalStateException("INGESTION_MAX_FILE_BYTES must be positive");
    }
    if (p.getEmbeddingDims() <= 0 || p.getEmbeddingDims() > 4096) {
      throw new IllegalStateException("EMBEDDING_DIMS must be within 1..4096");
    }
    if (p.getEmbeddingMaxTokens() < 16 || p.getEmbeddingMaxTokens() > 1024) {
      throw new IllegalStateException("EMBEDDING_MAX_TOKENS must be within 16..1024");
    }
    if (!p.getVectorStore().equals("jvm") && !p.getVectorStore().equals("pgvector")) {
      throw new IllegalStateException("VECTOR_STORE must be 'jvm' or 'pgvector'");
    }
    if (p.getRetrievalTopK() <= 0 || p.getRetrievalMaxTopK() < p.getRetrievalTopK()) {
      throw new IllegalStateException("Invalid retrieval topK bounds");
    }
    if (!(p.getEvidenceStrong() > p.getEvidenceModerate()
        && p.getEvidenceModerate() > p.getEvidenceLimited())) {
      throw new IllegalStateException("Evidence thresholds must be strictly descending");
    }
    // --- Voice / wake-word ---
    if (p.getWakeThreshold() <= 0.0 || p.getWakeThreshold() >= 1.0) {
      throw new IllegalStateException("WAKE_THRESHOLD must be within (0,1) exclusive");
    }
    if (p.getWakePhrase() == null || p.getWakePhrase().isBlank()) {
      throw new IllegalStateException("WAKE_PHRASE must not be blank");
    }
    if (p.getWakeCooldownMs() < 0 || p.getWakeCooldownMs() > 60_000) {
      throw new IllegalStateException("WAKE_COOLDOWN_MS must be within 0..60000");
    }
    if (p.getInactivityTimeoutSeconds() < 5 || p.getInactivityTimeoutSeconds() > 3600) {
      throw new IllegalStateException("INACTIVITY_TIMEOUT_SECONDS must be within 5..3600");
    }
    if (p.getListenTimeoutSeconds() < 1 || p.getListenTimeoutSeconds() > 300) {
      throw new IllegalStateException("LISTEN_TIMEOUT_SECONDS must be within 1..300");
    }
    // KWS engines are trained at 16 kHz; anything else silently degrades accuracy.
    if (p.getAudioSampleRate() != 8000 && p.getAudioSampleRate() != 16000) {
      throw new IllegalStateException("AUDIO_SAMPLE_RATE must be 8000 or 16000");
    }
    if (p.getAudioFrameMillis() < 20 || p.getAudioFrameMillis() > 1000) {
      throw new IllegalStateException("AUDIO_FRAME_MILLIS must be within 20..1000");
    }
  }
}
