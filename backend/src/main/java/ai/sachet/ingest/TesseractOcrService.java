package ai.sachet.ingest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Tesseract OCR via the {@code tesseract} binary (stdout, no shell).
 * Availability is probed once at startup and logged; when the binary is
 * absent {@link #available()} is false and scanned pages fail cleanly.
 * Argument-injection safe: no shell, fixed argv, strict language allowlist.
 */
@Service
public class TesseractOcrService implements OcrService {

  private static final Logger log = LoggerFactory.getLogger(TesseractOcrService.class);
  private static final Pattern LANG = Pattern.compile("[a-z]{2,3}(\\+[a-z]{2,3})*");

  private final boolean present;

  public TesseractOcrService() {
    this.present = probe();
    log.info("tesseract OCR available={}", present);
  }

  private static boolean probe() {
    try {
      Process p = new ProcessBuilder("tesseract", "--version")
          .redirectErrorStream(true).start();
      boolean done = p.waitFor(10, TimeUnit.SECONDS);
      return done && p.exitValue() == 0;
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public boolean available() {
    return present;
  }

  @Override
  public List<String> ocrImages(List<Path> images, String language) throws IOException {
    String lang = language == null ? "eng" : language;
    if (!LANG.matcher(lang).matches()) {
      throw new IllegalArgumentException("Invalid OCR language.");
    }
    if (!present) {
      throw new IOException("OCR unavailable: tesseract binary not installed.");
    }
    List<String> out = new ArrayList<>(images.size());
    for (Path image : images) {
      Process p = new ProcessBuilder(
          "tesseract", image.toAbsolutePath().toString(), "stdout", "-l", lang)
          .redirectErrorStream(false).start();
      try {
        boolean done = p.waitFor(120, TimeUnit.SECONDS);
        if (!done) {
          p.destroyForcibly();
          throw new IOException("OCR timed out.");
        }
        if (p.exitValue() != 0) {
          String err = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
          log.warn("tesseract failed: {}", err.strip().lines().findFirst().orElse("unknown"));
          throw new IOException("OCR failed.");
        }
        out.add(new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IOException("OCR interrupted.", e);
      }
    }
    return out;
  }
}
