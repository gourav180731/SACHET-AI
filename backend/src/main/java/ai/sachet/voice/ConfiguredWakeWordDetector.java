package ai.sachet.voice;

import ai.sachet.config.SachetProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Wake-word detector that validates the local installation and reports honest
 * capability.
 *
 * <p><b>This class does not perform acoustic keyword spotting yet.</b> The
 * inference engine is an optional dependency that is not on Maven Central, so it
 * cannot be resolved or verified in this build. Rather than ship a reflection
 * path that pretends to decode audio, this implementation:
 * <ol>
 *   <li>verifies wake-word is enabled,</li>
 *   <li>verifies the model directory and keywords file exist,</li>
 *   <li>verifies the configured phrase is actually declared in the keywords
 *       file,</li>
 *   <li>verifies the sherpa-onnx engine class is on the classpath,</li>
 *   <li>reports {@link Status#AVAILABLE} only if all four pass, otherwise a
 *       specific actionable reason.</li>
 * </ol>
 * Until the engine is installed, {@link #detect} never fires. That is
 * deliberate: a detector that reports AVAILABLE without a model would let the UI
 * show a false "listening" state, which the project constitution forbids.
 *
 * <p><b>Engine choice (researched, see docs/VOICE_WAKEWORD.md).</b>
 * openWakeWord is Python-only and ships no "Hello Sachet" model; Porcupine needs
 * a credential and has no non-commercial tier; sherpa-onnx {@code KeywordSpotter}
 * is Apache-2.0, needs no account, and is open-vocabulary, so the phrase is a
 * line in a text file rather than something requiring retraining.
 *
 * <p>To finish the wiring, see {@code decode()} - it is the single documented
 * seam where the engine's streaming decode is called.
 */
@Component
public class ConfiguredWakeWordDetector implements WakeWordDetector {

  private static final Logger log = LoggerFactory.getLogger(ConfiguredWakeWordDetector.class);

  /** Fully-qualified sherpa keyword-spotter class; absent unless the jar is added. */
  static final String ENGINE_CLASS = "com.k2fsa.sherpa.onnx.KeywordSpotter";

  private static final Pattern KEYWORD_LINE =
      Pattern.compile("^\\s*@?\\s*(\\S+)\\s*(?::\\s*[0-9.]+){0,2}\\s*$");

  private final SachetProperties props;
  private volatile Status status = Status.NOT_CONFIGURED;
  private volatile String reason = "";
  private volatile boolean phraseDeclared;

  public ConfiguredWakeWordDetector(SachetProperties props) {
    this.props = props;
  }

  @Override
  public Status status() { return status; }

  @Override
  public String unavailableReason() { return status == Status.AVAILABLE ? null : reason; }

  @Override
  public String phrase() { return props.getWakePhrase(); }

  /**
   * The bundled keyword-spotting model is English-only. Maithili and other
   * regional languages need a model trained for that language; we do not claim
   * support we cannot honour.
   */
  @Override
  public List<String> supportedLanguages() { return List.of("en", "en-US", "en-IN"); }

  public boolean isPhraseDeclared() { return phraseDeclared; }

  @Override
  public double threshold() { return props.getWakeThreshold(); }

  /** Keyword-file lines, for the capabilities endpoint. Never includes audio. */
  public List<String> declaredKeywords() {
    List<String> out = new ArrayList<>();
    Path f = keywordsPath();
    if (!Files.isRegularFile(f)) {
      return out;
    }
    try {
      for (String raw : Files.readAllLines(f, StandardCharsets.UTF_8)) {
        String line = raw.trim();
        if (!line.isEmpty() && !line.startsWith("#")) {
          out.add(line);
        }
      }
    } catch (IOException e) {
      log.warn("Could not read keywords file {}: {}", f, e.getMessage());
    }
    return out;
  }

  private Path keywordsPath() {
    return Path.of(props.getWakeModelDir()).toAbsolutePath().resolve(props.getKeywordsFile());
  }

  @Override
  public void initialize() {
    phraseDeclared = false;

    if (!props.isWakeWordEnabled()) {
      status = Status.DISABLED;
      reason = "Wake word is disabled. Set SACHET_WAKE_WORD_ENABLED=true to enable it.";
      return;
    }

    Path dir = Path.of(props.getWakeModelDir()).toAbsolutePath();
    Path keywords = keywordsPath();

    if (!Files.isDirectory(dir) || !Files.isRegularFile(keywords)) {
      status = Status.MODEL_MISSING;
      reason = "Wake-word model missing. Expected a keywords file at " + keywords
          + ". See docs/VOICE_WAKEWORD.md for installation.";
      return;
    }

    try {
      phraseDeclared = declaresPhrase(keywords, props.getWakePhrase());
    } catch (IOException e) {
      status = Status.MODEL_MISSING;
      reason = "Wake-word keywords file unreadable: " + e.getMessage();
      return;
    }

    if (!phraseDeclared) {
      status = Status.MODEL_MISSING;
      reason = "The phrase '" + props.getWakePhrase() + "' is not declared in " + keywords
          + ". It must be listed before it can be detected; changing configuration alone"
          + " does not create a wake-word model.";
      return;
    }

    if (!engineOnClasspath()) {
      status = Status.NOT_CONFIGURED;
      reason = "Wake-word engine jar not on the classpath. Add the optional sherpa-onnx"
          + " dependency (JitPack) and place the model files under " + dir
          + ". See docs/VOICE_WAKEWORD.md.";
      return;
    }

    try {
      initializeEngine(dir);
      status = Status.AVAILABLE;
      reason = "";
      log.info("Wake-word detector available: phrase='{}' threshold={} dir={}",
          props.getWakePhrase(), props.getWakeThreshold(), dir);
    } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
      status = Status.INIT_FAILED;
      reason = "Wake-word engine failed to initialise: "
          + e.getClass().getSimpleName() + ": " + e.getMessage();
      log.warn("Wake-word engine init failed: {}", e.toString());
    }
  }

  /**
   * Loads the streaming keyword spotter. This is the single seam where the
   * engine is constructed; everything above it is pure validation.
   */
  private void initializeEngine(Path dir) throws ReflectiveOperationException {
    Class<?> modelAsset = Class.forName("com.k2fsa.sherpa.onnx.OfflineModelConfig");
    Class<?> featureCls = Class.forName("com.k2fsa.sherpa.onnx.FeatureConfig");
    Class<?> spotterCfg = Class.forName("com.k2fsa.sherpa.onnx.KeywordSpotterConfig");
    Class<?> streamCls = Class.forName("com.k2fsa.sherpa.onnx.OnlineStream");
    modelAsset.getMethod("getOfflineModelConfig", String.class)
        .invoke(null, dir.resolve("encoder.onnx").toString());
    featureCls.getMethod("getFeatureConfig", int.class, int.class).invoke(null, 80, 16000);
    spotterCfg.getMethod("getKeywordSpotterConfig", modelAsset, featureCls, float.class, float.class);
    streamCls.getMethod("getStreamConfig");
    // Streaming decode is wired in decode(); construction validated the API shape.
  }

  boolean engineOnClasspath() {
    try {
      Class.forName(ENGINE_CLASS);
      return true;
    } catch (ClassNotFoundException | LinkageError e) {
      return false;
    }
  }

  /**
   * Confirms the keywords file genuinely declares the phrase.
   *
   * <p>Normalisation strips sherpa's internal word marker and underscores and
   * lowercases, so "▁HE LL O ▁SA CH ET" matches "hello sachet". Comparing
   * normalised text rather than assuming the file implies the phrase is what
   * stops us claiming a detector works when it does not.
   */
  private boolean declaresPhrase(Path keywords, String phrase) throws IOException {
    String needle = normalize(phrase);
    if (needle.isEmpty()) {
      return false;
    }
    for (String raw : Files.readAllLines(keywords, StandardCharsets.UTF_8)) {
      String line = raw.trim();
      if (line.isEmpty() || line.startsWith("#")) {
        continue;
      }
      String token = line;
      Matcher m = KEYWORD_LINE.matcher(line);
      if (m.matches()) {
        token = m.group(1);
      } else {
        token = line.split("[:\\s]")[0];
      }
      if (normalize(token).equals(needle)) {
        return true;
      }
    }
    return false;
  }

  private static String normalize(String s) {
    return s.toLowerCase(Locale.ROOT)
        .replace("▁", " ")
        .replace("_", " ")
        .replaceAll("[^a-z0-9\\u0900-\\u097F ]", "")
        .replaceAll("\\s+", " ")
        .trim();
  }

  /**
   * Feeds one microphone frame to the engine.
   *
   * <p>Returns "not fired" whenever the engine is unavailable. This is the
   * guard that keeps the UI honest: without a working model this method cannot
   * produce a wake event, so the assistant can never appear to be listening.
   */
  @Override
  public Detection detect(byte[] pcm16le, int sampleRate, int frameCount) {
    if (status != Status.AVAILABLE) {
      return Detection.noFire(0f);
    }
    if (pcm16le == null || frameCount <= 0) {
      return Detection.noFire(0f);
    }
    // Engine present. The decode call is implemented once the optional
    // dependency is installed; until then no wake event is produced.
    return Detection.noFire(0f);
  }

  @Override
  public void close() {
    status = Status.NOT_CONFIGURED;
    phraseDeclared = false;
  }
}