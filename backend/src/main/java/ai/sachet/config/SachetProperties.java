package ai.sachet.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Centralized SACHET configuration (bound from {@code sachet.*} properties / env).
 * Secure defaults: external models OFF, cross-tenant retrieval OFF,
 * verbose sensitive logging OFF, web search OFF.
 */
@ConfigurationProperties(prefix = "sachet")
public class SachetProperties {

  /** Placeholder that must never run outside local dev. */
  public static final String DEV_JWT_SENTINEL = "CHANGE-ME-PLACEHOLDER";
  /** Prefix marking values that are dev-only by convention (see .env.example). */
  public static final String DEV_ONLY_PREFIX = "dev-only-";

  private String appEnv = "dev";
  private String appVersion = "0.2.0";
  private String apiPrefix = "/api/v1";
  private List<String> corsOrigins = List.of("http://localhost:3000");

  private String jwtSecret = DEV_JWT_SENTINEL;
  private int jwtExpireMin = 30;

  private boolean sensitiveMode = true;
  private boolean allowExternalModels = false;
  private boolean featureWebSearch = false;
  private boolean featureCrossTenantRetrieval = false;
  private boolean featureVerboseLogging = false;

  private String storagePath = "./storage";
  private long storageMaxBytes = 10L * 1024 * 1024 * 1024; // 10 GiB quota guard
  /** Per-file upload cap (100 MiB). Enforced before bytes touch the pipeline. */
  private long ingestionMaxFileBytes = 100L * 1024 * 1024;
  /**
   * When true, uploads REQUIRE a successful malware scan; a missing/unavailable
   * scanner fails the job instead of passing silently. Default false (dev);
   * production deployments handling untrusted files must set it true.
   */
  private boolean requireMalwareScan = false;

  // --- Phase 4: local embeddings ---
  /** Local model directory (model.onnx + tokenizer.json; auto-downloaded once). */
  private String embeddingModelDir = "./models/paraphrase-multilingual-minilm-l12-v2";
  private String embeddingModelId = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2";
  private int embeddingDims = 384;
  private int embeddingMaxTokens = 256;
  private boolean embeddingAutoDownload = true;
  /** jvm (default, always works) or pgvector (EXPERIMENTAL, needs vector ext + DDL). */
  private String vectorStore = "jvm";

  // --- Phase 4: retrieval tuning (all documented heuristics, no fake confidence) ---
  private int retrievalTopK = 10;
  private int retrievalMaxTopK = 50;
  private int retrievalCandidateCap = 2000;
  private double evidenceStrong = 0.70;
  private double evidenceModerate = 0.50;
  private double evidenceLimited = 0.30;

  // --- Phase 7: Gemini / LLM Gateway ---
  private String geminiApiKey = "";
  private String geminiModel = "gemini-3.8-flash";
  private double geminiTemperature = 0.2;
  private int geminiMaxTokens = 8192;
  private List<String> ragResponseModes = List.of("detailed", "short", "bullets", "custom");
  private String defaultRagMode = "detailed";

  // --- Voice / wake-word (Phase 7) ---
  private boolean wakeWordEnabled = false;
  private String wakePhrase = "hello sachet";
  private double wakeThreshold = 0.5;
  private int wakeCooldownMs = 1500;
  private int inactivityTimeoutSeconds = 120;
  private int listenTimeoutSeconds = 15;
  private String microphoneDevice = "default";
  private String sttProvider = "none";
  private String sttModel = "gemini-3.8-flash";
  private String ttsProvider = "none";
  private String ttsModel = "gemini-3.8-flash-tts";
  private String wakeModelDir = "./models/wake";
  private String keywordsFile = "keywords.txt";
  private int audioSampleRate = 16000;
  private int audioFrameMillis = 100;

  public boolean isWakeWordEnabled() { return wakeWordEnabled; }
  public void setWakeWordEnabled(boolean v) { this.wakeWordEnabled = v; }
  public String getWakePhrase() { return wakePhrase; }
  public void setWakePhrase(String v) { this.wakePhrase = v; }
  public double getWakeThreshold() { return wakeThreshold; }
  public void setWakeThreshold(double v) { this.wakeThreshold = v; }
  public int getWakeCooldownMs() { return wakeCooldownMs; }
  public void setWakeCooldownMs(int v) { this.wakeCooldownMs = v; }
  public int getInactivityTimeoutSeconds() { return inactivityTimeoutSeconds; }
  public void setInactivityTimeoutSeconds(int v) { this.inactivityTimeoutSeconds = v; }
  public int getListenTimeoutSeconds() { return listenTimeoutSeconds; }
  public void setListenTimeoutSeconds(int v) { this.listenTimeoutSeconds = v; }
  public String getMicrophoneDevice() { return microphoneDevice; }
  public void setMicrophoneDevice(String v) { this.microphoneDevice = v; }
  public String getSttProvider() { return sttProvider; }
  public void setSttProvider(String v) { this.sttProvider = v; }
  public String getSttModel() { return sttModel; }
  public void setSttModel(String v) { this.sttModel = v; }
  public String getTtsProvider() { return ttsProvider; }
  public void setTtsProvider(String v) { this.ttsProvider = v; }
  public String getTtsModel() { return ttsModel; }
  public void setTtsModel(String v) { this.ttsModel = v; }
  public String getWakeModelDir() { return wakeModelDir; }
  public void setWakeModelDir(String v) { this.wakeModelDir = v; }
  public String getKeywordsFile() { return keywordsFile; }
  public void setKeywordsFile(String v) { this.keywordsFile = v; }
  public int getAudioSampleRate() { return audioSampleRate; }
  public void setAudioSampleRate(int v) { this.audioSampleRate = v; }
  public int getAudioFrameMillis() { return audioFrameMillis; }
  public void setAudioFrameMillis(int v) { this.audioFrameMillis = v; }

  public String getAppEnv() { return appEnv; }
  public void setAppEnv(String appEnv) { this.appEnv = appEnv; }
  public String getAppVersion() { return appVersion; }
  public void setAppVersion(String appVersion) { this.appVersion = appVersion; }
  public String getApiPrefix() { return apiPrefix; }
  public void setApiPrefix(String apiPrefix) { this.apiPrefix = apiPrefix; }
  public List<String> getCorsOrigins() { return corsOrigins; }
  public void setCorsOrigins(List<String> corsOrigins) { this.corsOrigins = corsOrigins; }
  public String getJwtSecret() { return jwtSecret; }
  public void setJwtSecret(String jwtSecret) { this.jwtSecret = jwtSecret; }
  public int getJwtExpireMin() { return jwtExpireMin; }
  public void setJwtExpireMin(int jwtExpireMin) { this.jwtExpireMin = jwtExpireMin; }
  public boolean isSensitiveMode() { return sensitiveMode; }
  public void setSensitiveMode(boolean sensitiveMode) { this.sensitiveMode = sensitiveMode; }
  public boolean isAllowExternalModels() { return allowExternalModels; }
  public void setAllowExternalModels(boolean allowExternalModels) { this.allowExternalModels = allowExternalModels; }
  public boolean isFeatureWebSearch() { return featureWebSearch; }
  public void setFeatureWebSearch(boolean featureWebSearch) { this.featureWebSearch = featureWebSearch; }
  public boolean isFeatureCrossTenantRetrieval() { return featureCrossTenantRetrieval; }
  public void setFeatureCrossTenantRetrieval(boolean v) { this.featureCrossTenantRetrieval = v; }
  public boolean isFeatureVerboseLogging() { return featureVerboseLogging; }
  public void setFeatureVerboseLogging(boolean v) { this.featureVerboseLogging = v; }
  public String getStoragePath() { return storagePath; }
  public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
  public long getStorageMaxBytes() { return storageMaxBytes; }
  public void setStorageMaxBytes(long storageMaxBytes) { this.storageMaxBytes = storageMaxBytes; }
  public long getIngestionMaxFileBytes() { return ingestionMaxFileBytes; }
  public void setIngestionMaxFileBytes(long v) { this.ingestionMaxFileBytes = v; }
  public boolean isRequireMalwareScan() { return requireMalwareScan; }
  public void setRequireMalwareScan(boolean v) { this.requireMalwareScan = v; }
  public String getEmbeddingModelDir() { return embeddingModelDir; }
  public void setEmbeddingModelDir(String v) { this.embeddingModelDir = v; }
  public String getEmbeddingModelId() { return embeddingModelId; }
  public void setEmbeddingModelId(String v) { this.embeddingModelId = v; }
  public int getEmbeddingDims() { return embeddingDims; }
  public void setEmbeddingDims(int v) { this.embeddingDims = v; }
  public int getEmbeddingMaxTokens() { return embeddingMaxTokens; }
  public void setEmbeddingMaxTokens(int v) { this.embeddingMaxTokens = v; }
  public boolean isEmbeddingAutoDownload() { return embeddingAutoDownload; }
  public void setEmbeddingAutoDownload(boolean v) { this.embeddingAutoDownload = v; }
  public String getVectorStore() { return vectorStore; }
  public void setVectorStore(String v) { this.vectorStore = v; }
  public int getRetrievalTopK() { return retrievalTopK; }
  public void setRetrievalTopK(int v) { this.retrievalTopK = v; }
  public int getRetrievalMaxTopK() { return retrievalMaxTopK; }
  public void setRetrievalMaxTopK(int v) { this.retrievalMaxTopK = v; }
  public int getRetrievalCandidateCap() { return retrievalCandidateCap; }
  public void setRetrievalCandidateCap(int v) { this.retrievalCandidateCap = v; }
  public double getEvidenceStrong() { return evidenceStrong; }
  public void setEvidenceStrong(double v) { this.evidenceStrong = v; }
  public double getEvidenceModerate() { return evidenceModerate; }
  public void setEvidenceModerate(double v) { this.evidenceModerate = v; }
  public double getEvidenceLimited() { return evidenceLimited; }
  public void setEvidenceLimited(double v) { this.evidenceLimited = v; }

  // --- Gemini / LLM Gateway ---
  public String getGeminiApiKey() { return geminiApiKey; }
  public void setGeminiApiKey(String v) { this.geminiApiKey = v; }
  public String getGeminiModel() { return geminiModel; }
  public void setGeminiModel(String v) { this.geminiModel = v; }
  public double getGeminiTemperature() { return geminiTemperature; }
  public void setGeminiTemperature(double v) { this.geminiTemperature = v; }
  public int getGeminiMaxTokens() { return geminiMaxTokens; }
  public void setGeminiMaxTokens(int v) { this.geminiMaxTokens = v; }
  public List<String> getRagResponseModes() { return ragResponseModes; }
  public void setRagResponseModes(List<String> v) { this.ragResponseModes = v; }
  public String getDefaultRagMode() { return defaultRagMode; }
  public void setDefaultRagMode(String v) { this.defaultRagMode = v; }
}
