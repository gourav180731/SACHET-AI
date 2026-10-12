package ai.sachet.ingest;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Single-format document parser. Implementations are pure functions of the
 * stored blob: no network, no model calls, no authorization (callers enforce).
 */
public interface DocumentParser {

  /** Lowercase extensions handled, e.g. {@code ["pdf"]}. */
  String[] extensions();

  /** Canonical mime for the format. */
  String mimeType();

  /** Extracts pages with provenance. Throws {@link IOException} on corrupt/protected files. */
  ParsedDocument parse(Path blob) throws IOException;

  /**
   * Whether blank pages may be scanned images needing OCR. True for PDF only:
   * DOCX/PPTX/TXT are inherently digital, so short text ingests as-is instead
   * of failing when no OCR engine exists.
   */
  default boolean ocrCapable() {
    return false;
  }
}
