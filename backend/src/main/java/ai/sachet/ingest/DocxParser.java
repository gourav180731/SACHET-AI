package ai.sachet.ingest;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;

/**
 * DOCX parser (POI). Paragraphs become one page-1 flow; Heading styles become
 * chunk sections so provenance survives into retrieval.
 */
@Component
public class DocxParser implements DocumentParser {

  @Override
  public String[] extensions() {
    return new String[] {"docx"};
  }

  @Override
  public String mimeType() {
    return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
  }

  @Override
  public ParsedDocument parse(Path blob) throws IOException {
    final XWPFDocument doc;
    try (var in = java.nio.file.Files.newInputStream(blob)) {
      doc = new XWPFDocument(in);
    } catch (Exception e) {
      throw new IOException("Unreadable DOCX.", e);
    }
    try (doc) {
      // Headings are emitted as "# ..." marker lines so the chunker can track
      // sections uniformly across DOCX/MD sources (markers stay in the text).
      StringBuilder body = new StringBuilder();
      for (XWPFParagraph p : doc.getParagraphs()) {
        String text = p.getText();
        if (text == null || text.isBlank()) {
          continue;
        }
        if (isHeading(p)) {
          body.append("\n\n# ").append(text.strip()).append("\n\n");
        } else {
          body.append(text.strip()).append("\n\n");
        }
      }
      String full = body.toString().strip();
      // Single page; per-paragraph sections are derived by the chunker from markers.
      return new ParsedDocument(List.of(
          new ParsedDocument.ParsedPage(1, full, null, full.length() < PdfParser.BLANK_THRESHOLD)));
    }
  }

  private boolean isHeading(XWPFParagraph p) {
    String style = p.getStyle();
    return style != null && style.toLowerCase(java.util.Locale.ROOT).startsWith("heading");
  }
}
