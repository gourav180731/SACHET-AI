package ai.sachet;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ai.sachet.ingest.TesseractOcrService;
import java.util.List;
import org.junit.jupiter.api.Test;

/** OCR service contract: strict language gate, honest availability. */
class TesseractLangTest {

  @Test
  void invalidLanguageRejectedBeforeBinary() {
    var ocr = new TesseractOcrService();
    assertThrows(IllegalArgumentException.class,
        () -> ocr.ocrImages(List.of(), "eng; rm -rf /"));
    assertThrows(IllegalArgumentException.class,
        () -> ocr.ocrImages(List.of(), "ENGLISH"));
    assertThrows(IllegalArgumentException.class,
        () -> ocr.ocrImages(List.of(), ""));
  }

  @Test
  void availabilityProbeNeverThrows() {
    assertDoesNotThrow(() -> new TesseractOcrService().available());
  }
}
