package ai.sachet.ingest;

import java.util.List;

/**
 * Parser output. One {@link ParsedPage} per PDF page / PPTX slide / whole text
 * file (page 1). {@code blank} marks pages with no extractable text (scanned
 * images) so the worker can route them to OCR — or fail cleanly when OCR is
 * unavailable. Provenance ({@code pageNumber}, {@code section}) is set here
 * and carried into every chunk.
 */
public record ParsedDocument(List<ParsedPage> pages) {

  public record ParsedPage(int pageNumber, String text, String section, boolean blank) {
    public ParsedPage {
      if (text == null) {
        text = "";
      }
    }
  }

  public boolean needsOcr() {
    return pages.stream().anyMatch(ParsedPage::blank);
  }
}
