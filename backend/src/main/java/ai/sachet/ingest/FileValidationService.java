package ai.sachet.ingest;

import ai.sachet.config.SachetProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Cheap synchronous upload validation: extension allowlist, size caps, and
 * magic-byte sniffing (the extension must match the bytes — a renamed .exe
 * fails here, before any parser touches it). Deep parsing happens async.
 */
@Service
public class FileValidationService {

  private static final Set<String> ALLOWED = Set.of("pdf", "docx", "pptx", "txt", "md");
  private static final long MIN_BYTES = 1;

  private final SachetProperties props;

  public FileValidationService(SachetProperties props) {
    this.props = props;
  }

  public record ValidatedFile(String extension, String mimeType, String sha256, long size) {
  }

  public ValidatedFile validate(Path blob, String originalName, long declaredSize) {
    String ext = PdfParser.extensionOf(originalName);
    if (!ALLOWED.contains(ext)) {
      throw ValidationException.failed(
          "Unsupported file type. Allowed: pdf, docx, pptx, txt, md.");
    }
    long size;
    try {
      size = Files.size(blob);
    } catch (IOException e) {
      throw ValidationException.failed("Could not read upload.");
    }
    if (size < MIN_BYTES) {
      throw ValidationException.failed("Empty file.");
    }
    if (declaredSize >= 0 && declaredSize != size) {
      throw ValidationException.failed("Upload size mismatch.");
    }
    if (size > props.getIngestionMaxFileBytes()) {
      throw ValidationException.tooBig("File exceeds the configured size limit.");
    }
    byte[] head = readHead(blob);
    if (!magicMatches(ext, head, blob)) {
      throw ValidationException.failed("File content does not match its extension.");
    }
    return new ValidatedFile(ext, mimeFor(ext), sha256(blob), size);
  }

  private static byte[] readHead(Path blob) {
    try (InputStream in = Files.newInputStream(blob)) {
      return in.readNBytes(16);
    } catch (IOException e) {
      throw ValidationException.failed("Could not read upload.");
    }
  }

  private static boolean magicMatches(String ext, byte[] head, Path blob) {
    return switch (ext) {
      case "pdf" -> startsWith(head, "%PDF-");
      case "docx", "pptx" -> head.length >= 4 && head[0] == 0x50 && head[1] == 0x4B
          && head[2] == 0x03 && head[3] == 0x04; // ZIP container; POI validates OOXML async
      case "txt", "md" -> looksLikeText(blob);
      default -> false;
    };
  }

  private static boolean startsWith(byte[] head, String ascii) {
    byte[] want = ascii.getBytes(StandardCharsets.US_ASCII);
    if (head.length < want.length) {
      return false;
    }
    for (int i = 0; i < want.length; i++) {
      if (head[i] != want[i]) {
        return false;
      }
    }
    return true;
  }

  /** Decodes a sample as UTF-8 and requires mostly printable content. */
  static boolean looksLikeText(Path blob) {
    byte[] sample;
    try (InputStream in = Files.newInputStream(blob)) {
      sample = in.readNBytes(8192);
    } catch (IOException e) {
      return false;
    }
    if (sample.length == 0) {
      return false;
    }
    String s = new String(sample, StandardCharsets.UTF_8);
    if (s.contains("�")) {
      return false; // undecodable bytes: almost certainly binary
    }
    int printable = 0;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '\n' || c == '\r' || c == '\t' || (c >= 0x20 && c != 0x7F)) {
        printable++;
      }
    }
    return (double) printable / s.length() > 0.90;
  }

  static String sha256(Path blob) {
    try (InputStream in = Files.newInputStream(blob)) {
      var digest = java.security.MessageDigest.getInstance("SHA-256");
      byte[] buf = new byte[8192];
      int n;
      while ((n = in.read(buf)) > 0) {
        digest.update(buf, 0, n);
      }
      return java.util.HexFormat.of().formatHex(digest.digest());
    } catch (Exception e) {
      throw ValidationException.failed("Could not hash upload.");
    }
  }

  private static String mimeFor(String ext) {
    return switch (ext) {
      case "pdf" -> "application/pdf";
      case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
      case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
      case "md" -> "text/markdown";
      default -> "text/plain";
    };
  }
}
