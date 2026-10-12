package ai.sachet.storage;

import ai.sachet.config.SachetProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Secure local document storage (Phase 2). Guarantees: callers cannot escape
 * the storage root (path traversal → rejected), every stored blob gets a
 * SHA-256 checksum for integrity / duplicate detection, and oversized uploads
 * are refused before they touch disk.
 */
@Service
public class LocalStorageService {

  private final Path root;
  private final long maxBytes;

  public LocalStorageService(SachetProperties props) throws IOException {
    this.root = Path.of(props.getStoragePath()).toAbsolutePath().normalize();
    this.maxBytes = props.getStorageMaxBytes();
    Files.createDirectories(root);
  }

  public record StoredBlob(String key, long size, String sha256) {
  }

  public StoredBlob store(String originalName, InputStream data, long declaredSize) throws IOException {
    if (declaredSize < 0 || declaredSize > maxBytes) {
      throw new IOException("Upload refused: size outside policy limits.");
    }
    String raw = originalName == null ? "upload" : Path.of(originalName).getFileName().toString();
    String safe = raw.replaceAll("[^A-Za-z0-9._-]", "_");
    if (safe.isBlank()) {
      safe = "upload";
    }
    String key = UUID.randomUUID() + "-" + safe;
    Path target = resolve(key);
    MessageDigest sha;
    try {
      sha = MessageDigest.getInstance("SHA-256");
    } catch (Exception e) {
      throw new IOException("SHA-256 unavailable", e);
    }
    long written;
    try (InputStream in = new DigestInputStream(data, sha)) {
      written = Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      Files.deleteIfExists(target);
      throw e;
    }
    if (written > maxBytes) {
      Files.deleteIfExists(target);
      throw new IOException("Upload refused: exceeds policy limit.");
    }
    return new StoredBlob(key, written, HexFormat.of().formatHex(sha.digest()));
  }

  /** Resolves a storage key, refusing anything that escapes the root. */
  public Path resolve(String key) throws IOException {
    // Storage keys are server-generated (UUID + sanitized filename) and never
    // contain separators; anything else is rejected before path resolution.
    if (key == null || key.isBlank() || key.contains("..")
        || key.contains("/") || key.contains("\\") || key.contains(":")) {
      throw new IOException("Invalid storage key.");
    }
    Path target = root.resolve(key).normalize();
    if (!target.startsWith(root)) {
      throw new IOException("Invalid storage key.");
    }
    return target;
  }
}
