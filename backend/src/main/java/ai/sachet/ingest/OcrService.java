package ai.sachet.ingest;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * OCR abstraction. Only invoked for pages with no extractable text; when no
 * implementation is available the job fails with OCR_UNAVAILABLE — scanned
 * content is never silently indexed as empty.
 */
public interface OcrService {

  /** True when OCR can actually run in this environment. */
  boolean available();

  /**
   * OCRs one rendered page image per list entry (PNG). Returns extracted text
   * in the same order. {@code language} must match {@code [a-z]{2,3}(+[a-z]{2,3})*}.
   */
  List<String> ocrImages(List<Path> images, String language) throws IOException;
}
