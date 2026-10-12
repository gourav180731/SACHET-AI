package ai.sachet.ingest;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * PDF parser (PDFBox). Extracts text per page; pages with almost no text are
 * flagged {@code blank} (likely scanned images) for the OCR stage — never
 * silently indexed as empty. Encrypted/protected PDFs fail with a safe error.
 */
@Component
public class PdfParser implements DocumentParser {

  /** Below this many non-blank chars a page counts as scanned (needs OCR). */
  static final int BLANK_THRESHOLD = 25;
  /** Hard page cap: bounds CPU/memory per document (DoS guard). */
  static final int MAX_PAGES = 2000;

  @Override
  public String[] extensions() {
    return new String[] {"pdf"};
  }

  @Override
  public String mimeType() {
    return "application/pdf";
  }

  @Override
  public boolean ocrCapable() {
    return true; // scanned-image PDFs are the OCR case
  }

  @Override
  public ParsedDocument parse(Path blob) throws IOException {
    final PDDocument doc;
    try {
      doc = Loader.loadPDF(blob.toFile());
    } catch (IOException e) {
      throw new IOException("Unreadable or protected PDF.", e);
    }
    try (doc) {
      int pages = doc.getNumberOfPages();
      if (pages > MAX_PAGES) {
        throw new IOException("PDF exceeds page limit.");
      }
      PDFTextStripper stripper = new PDFTextStripper();
      List<ParsedDocument.ParsedPage> out = new ArrayList<>(pages);
      for (int i = 1; i <= pages; i++) {
        stripper.setStartPage(i);
        stripper.setEndPage(i);
        String text = stripper.getText(doc).strip();
        out.add(new ParsedDocument.ParsedPage(i, text, null, text.length() < BLANK_THRESHOLD));
      }
      return new ParsedDocument(List.copyOf(out));
    }
  }

  /** Lowercase filename extension, or "" when absent. */
  static String extensionOf(String filename) {
    if (filename == null) {
      return "";
    }
    int dot = filename.lastIndexOf('.');
    return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
  }
}
