package ai.sachet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.sachet.config.SachetProperties;
import ai.sachet.ingest.FileValidationService;
import ai.sachet.ingest.NoOpMalwareScanner;
import ai.sachet.ingest.MalwareScanner;
import ai.sachet.ingest.ValidationException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Upload validation: allowlist, size, magic bytes, text heuristic, scan hook default. */
class FileValidationServiceTest {

  @TempDir Path tmp;

  private FileValidationService service(long maxBytes) {
    SachetProperties p = new SachetProperties();
    p.setIngestionMaxFileBytes(maxBytes);
    return new FileValidationService(p);
  }

  private Path write(String name, byte[] bytes) throws Exception {
    Path p = tmp.resolve(name);
    Files.write(p, bytes);
    return p;
  }

  @Test
  void validPdfPasses() throws Exception {
    Path f = write("a.pdf", "%PDF-1.4 hello".getBytes(StandardCharsets.UTF_8));
    var v = service(1024).validate(f, "a.pdf", Files.size(f));
    assertEquals("pdf", v.extension());
    assertEquals(64, v.sha256().length());
  }

  @Test
  void validTextPasses() throws Exception {
    Path f = write("a.txt", "plain english text here".getBytes(StandardCharsets.UTF_8));
    service(1024).validate(f, "a.txt", Files.size(f));
  }

  @Test
  void extensionAllowlistEnforced() throws Exception {
    // NOTE: uses .xyz (not .exe): Windows AV locks fresh MZ-header .exe files,
    // which flaked @TempDir cleanup. Allowlist logic is identical either way.
    Path f = write("a.xyz", new byte[] {0x4D, 0x5A, 0x00, 0x01});
    ValidationException e = assertThrows(ValidationException.class,
        () -> service(1024).validate(f, "a.xyz", 4));
    assertEquals("VALIDATION_FAILED", e.getCode());
  }

  @Test
  void magicMismatchRejected() throws Exception {
    Path f = write("fake.pdf", "just text pretending".getBytes(StandardCharsets.UTF_8));
    assertThrows(ValidationException.class, () -> service(1024).validate(f, "fake.pdf", Files.size(f)));
  }

  @Test
  void emptyFileRejected() throws Exception {
    Path f = write("e.txt", new byte[0]);
    assertThrows(ValidationException.class, () -> service(1024).validate(f, "e.txt", 0));
  }

  @Test
  void oversizeRejectedWithLimitCode() throws Exception {
    byte[] data = "0123456789123456789".getBytes(StandardCharsets.UTF_8);
    Path f = write("big.txt", data);
    ValidationException e = assertThrows(ValidationException.class,
        () -> service(10).validate(f, "big.txt", data.length));
    assertEquals("LIMIT_EXCEEDED", e.getCode());
  }

  @Test
  void binaryAsTextRejected() throws Exception {
    byte[] data = new byte[200];
    for (int i = 0; i < data.length; i++) {
      data[i] = (byte) (i % 8); // control bytes: not printable text
    }
    Path f = write("b.txt", data);
    assertThrows(ValidationException.class,
        () -> service(1024).validate(f, "b.txt", data.length));
  }

  @Test
  void sizeMismatchRejected() throws Exception {
    Path f = write("c.txt", "hello".getBytes(StandardCharsets.UTF_8));
    assertThrows(ValidationException.class, () -> service(1024).validate(f, "c.txt", 999));
  }

  @Test
  void validDocPasses() throws Exception {
    byte[] ole2Header = new byte[] {(byte) 0xD0, (byte) 0xCF, (byte) 0x11, (byte) 0xE0, 0x00, 0x00, 0x00, 0x00};
    Path f = write("sample.doc", ole2Header);
    var v = service(1024).validate(f, "sample.doc", ole2Header.length);
    assertEquals("doc", v.extension());
    assertEquals("application/msword", v.mimeType());
  }

  @Test
  void validPptPasses() throws Exception {
    byte[] ole2Header = new byte[] {(byte) 0xD0, (byte) 0xCF, (byte) 0x11, (byte) 0xE0, 0x00, 0x00, 0x00, 0x00};
    Path f = write("sample.ppt", ole2Header);
    var v = service(1024).validate(f, "sample.ppt", ole2Header.length);
    assertEquals("ppt", v.extension());
    assertEquals("application/vnd.ms-powerpoint", v.mimeType());
  }

  @Test
  void validCsvPasses() throws Exception {
    Path f = write("table.csv", "id,name,role\n1,alice,admin\n".getBytes(StandardCharsets.UTF_8));
    var v = service(1024).validate(f, "table.csv", Files.size(f));
    assertEquals("csv", v.extension());
    assertEquals("text/csv", v.mimeType());
  }

  @Test
  void noOpScannerReportsNotScannedAndNotRequiredByDefault() {
    assertEquals(MalwareScanner.Verdict.NOT_SCANNED,
        new NoOpMalwareScanner().scan(tmp));
    assertFalse(new SachetProperties().isRequireMalwareScan());
  }
}
