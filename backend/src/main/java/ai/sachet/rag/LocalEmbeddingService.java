package ai.sachet.rag;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.sachet.config.SachetProperties;
import jakarta.annotation.PreDestroy;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Local multilingual embeddings (MiniLM-L12, 384 dims): exact Hugging Face
 * tokenization (rust tokenizers) + pure-Java transformer inference over the
 * safetensors weights — zero native ML libraries, strict Java-only runtime.
 *
 * Privacy: inference is 100% local. The WEIGHT files download once from
 * Hugging Face (pinned revision); no document or query text is ever sent
 * anywhere. Loading is lazy (first embed) so a missing model never breaks
 * unrelated endpoints — embedding-dependent calls fail with 503 instead.
 */
@Service
public class LocalEmbeddingService implements EmbeddingService {

  private static final Logger log = LoggerFactory.getLogger(LocalEmbeddingService.class);

  /** Pinned model revision (reproducible downloads). */
  private static final String MODEL_REVISION = "e8f8c211226b894fcb81acc59f3b34ba3efd5f42";
  private static final String WEIGHTS_FILE = "model.safetensors";
  private static final String TOKENIZER_FILE = "tokenizer.json";

  private final SachetProperties props;

  private volatile boolean loaded = false;
  private SafetensorsReader reader;
  private MiniLmEncoder encoder;
  private HuggingFaceTokenizer tokenizer;

  public LocalEmbeddingService(SachetProperties props) {
    this.props = props;
  }

  @Override
  public String modelId() {
    return props.getEmbeddingModelId();
  }

  @Override
  public int dimensions() {
    return props.getEmbeddingDims();
  }

  @Override
  public synchronized float[] embed(String text) {
    ensureLoaded();
    String input = text == null ? "" : text;
    if (input.length() > 20000) {
      input = input.substring(0, 20000); // bound tokenizer work per call
    }
    try {
      Encoding enc = tokenizer.encode(input);
      long[] ids = truncate(enc.getIds());
      return encoder.encode(ids);
    } catch (EmbeddingUnavailableException e) {
      throw e;
    } catch (Exception e) {
      throw new EmbeddingUnavailableException("Embedding failed.", e);
    }
  }

  /** Keeps [CLS] … [SEP]: truncates the middle, never the specials. */
  private long[] truncate(long[] ids) {
    int max = props.getEmbeddingMaxTokens();
    if (ids.length <= max) {
      return ids;
    }
    long[] out = new long[max];
    out[0] = ids[0];
    System.arraycopy(ids, 1, out, 1, max - 2);
    out[max - 1] = ids[ids.length - 1];
    return out;
  }

  private void ensureLoaded() {
    if (loaded) {
      return;
    }
    try {
      Path dir = Path.of(props.getEmbeddingModelDir()).toAbsolutePath();
      Files.createDirectories(dir);
      Path weightsFile = dir.resolve(WEIGHTS_FILE);
      Path tokFile = dir.resolve(TOKENIZER_FILE);
      if ((!Files.isRegularFile(weightsFile) || !Files.isRegularFile(tokFile))
          && props.isEmbeddingAutoDownload()) {
        String base = "https://huggingface.co/" + props.getEmbeddingModelId()
            + "/resolve/" + MODEL_REVISION + "/";
        ModelDownloader.ensureFile(weightsFile, base + WEIGHTS_FILE, 400_000_000L);
        ModelDownloader.ensureFile(tokFile, base + TOKENIZER_FILE, 100_000L);
      }
      if (!Files.isRegularFile(weightsFile) || !Files.isRegularFile(tokFile)) {
        throw new EmbeddingUnavailableException(
            "Embedding model missing in " + dir + " (need model.safetensors + tokenizer.json).");
      }
      tokenizer = HuggingFaceTokenizer.newInstance(tokFile);
      reader = new SafetensorsReader(weightsFile);
      encoder = new MiniLmEncoder(reader);
      if (encoder.hidden() != props.getEmbeddingDims()) {
        throw new EmbeddingUnavailableException("Model hidden " + encoder.hidden()
            + " != configured " + props.getEmbeddingDims());
      }
      // Smoke probe: a real forward pass before serving traffic.
      encoder.encode(new long[] {101, 102});
      loaded = true;
      log.info("embeddings ready model={} dims={} (pure-Java inference)",
          props.getEmbeddingModelId(), encoder.hidden());
    } catch (EmbeddingUnavailableException e) {
      throw e;
    } catch (Exception e) {
      throw new EmbeddingUnavailableException("Embedding engine failed to start.", e);
    }
  }

  @PreDestroy
  void close() {
    if (reader != null) {
      reader.close();
    }
  }

  /** Test hook: has the engine loaded (to distinguish "slow" from "broken")? */
  boolean isLoaded() {
    return loaded;
  }
}
