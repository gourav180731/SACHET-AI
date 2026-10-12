package ai.sachet.rag;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One-time model fetch from Hugging Face (pinned revision). Atomic (temp file
 * + move), size-guarded, with explicit errors — a half-downloaded model can
 * never poison the directory. Only used when the model dir is incomplete.
 */
final class ModelDownloader {

  private static final Logger log = LoggerFactory.getLogger(ModelDownloader.class);

  private ModelDownloader() {
  }

  static void ensureFile(Path target, String url, long minBytes) {
    if (Files.isRegularFile(target)) {
      try {
        if (Files.size(target) >= minBytes) {
          return;
        }
        log.warn("model file too small, re-downloading: {}", target.getFileName());
      } catch (IOException e) {
        // fall through to download
      }
    }
    log.info("downloading {} ({}+ bytes expected)", target.getFileName(), minBytes);
    Path tmp = target.resolveSibling(target.getFileName() + ".part");
    try {
      HttpClient client = HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(30))
          .followRedirects(HttpClient.Redirect.ALWAYS)
          .build();
      HttpRequest req = HttpRequest.newBuilder(URI.create(url))
          .timeout(Duration.ofMinutes(30)).GET().build();
      HttpResponse<InputStream> res = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
      if (res.statusCode() != 200) {
        throw new IOException("HTTP " + res.statusCode());
      }
      try (InputStream in = res.body(); OutputStream out = Files.newOutputStream(tmp)) {
        in.transferTo(out);
      }
      if (Files.size(tmp) < minBytes) {
        throw new IOException("download truncated");
      }
      Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      log.info("downloaded {}", target.getFileName());
    } catch (IOException | InterruptedException e) {
      try {
        Files.deleteIfExists(tmp);
      } catch (IOException ignored) {
        // best effort
      }
      if (e instanceof InterruptedException ie) {
        Thread.currentThread().interrupt();
        throw new EmbeddingUnavailableException("Model download interrupted.", ie);
      }
      throw new EmbeddingUnavailableException(
          "Could not download model file " + target.getFileName()
              + ". Place model.onnx + tokenizer.json in the model dir (see docs).",
          e);
    }
  }
}
