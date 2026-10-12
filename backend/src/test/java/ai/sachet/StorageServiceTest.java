package ai.sachet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.sachet.config.SachetProperties;
import ai.sachet.storage.LocalStorageService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Secure storage: checksums, traversal refusal, size limits. */
class StorageServiceTest {

  @TempDir java.nio.file.Path tmp;

  private LocalStorageService storage(long max) throws Exception {
    SachetProperties p = new SachetProperties();
    p.setStoragePath(tmp.toString());
    p.setStorageMaxBytes(max);
    return new LocalStorageService(p);
  }

  @Test
  void storeRoundtripWithChecksum() throws Exception {
    byte[] data = "circular-content".getBytes(StandardCharsets.UTF_8);
    var stored = storage(1024).store("note.txt",
        new ByteArrayInputStream(data), data.length);
    assertTrue(Files.exists(tmp.resolve(stored.key())));
    assertEquals(data.length, stored.size());
    assertEquals(64, stored.sha256().length());
  }

  @Test
  void traversalKeyRejected() throws Exception {
    var s = storage(1024);
    assertThrows(java.io.IOException.class, () -> s.resolve("../evil.txt"));
    assertThrows(java.io.IOException.class, () -> s.resolve(".."));
    assertThrows(java.io.IOException.class, () -> s.resolve("/etc/passwd"));
  }

  @Test
  void oversizeRefused() throws Exception {
    byte[] data = "too-big".getBytes(StandardCharsets.UTF_8);
    assertThrows(java.io.IOException.class,
        () -> storage(2).store("b.txt", new ByteArrayInputStream(data), data.length));
  }
}
