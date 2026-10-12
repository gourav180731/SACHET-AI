package ai.sachet.ingest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.springframework.stereotype.Component;

/**
 * Legacy Word (.doc) parser using Apache POI HWPF.
 * Extracts paragraphs and preserves document content for RAG indexing.
 */
@Component
public class DocParser implements DocumentParser {

  @Override
  public String[] extensions() {
    return new String[] {"doc"};
  }

  @Override
  public String mimeType() {
    return "application/msword";
  }

  @Override
  public ParsedDocument parse(Path blob) throws IOException {
    try (var in = Files.newInputStream(blob);
         var doc = new HWPFDocument(in);
         var extractor = new WordExtractor(doc)) {
      String text = extractor.getText();
      String full = text == null ? "" : text.strip();
      return new ParsedDocument(List.of(
          new ParsedDocument.ParsedPage(1, full, null, full.length() < PdfParser.BLANK_THRESHOLD)));
    } catch (Exception e) {
      // Fallback: try raw stream extraction if standard HWPF encounters complex OLE2 stream
      try (var in = Files.newInputStream(blob)) {
        String raw = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1);
        String cleaned = raw.replaceAll("[^\\x20-\\x7E\\n\\r\\t]", " ").replaceAll("\\s+", " ").strip();
        if (cleaned.length() > 20) {
          return new ParsedDocument(List.of(
              new ParsedDocument.ParsedPage(1, cleaned, null, false)));
        }
      } catch (Exception ignored) {
      }
      throw new IOException("Unreadable or corrupted DOC file: " + e.getMessage(), e);
    }
  }
}
