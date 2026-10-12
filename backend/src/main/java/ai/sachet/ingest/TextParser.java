package ai.sachet.ingest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Plain-text / Markdown parser. The whole file is page 1; Markdown `#`
 * headings act as chunk sections (same marker convention as the DOCX parser).
 */
@Component
public class TextParser implements DocumentParser {

  @Override
  public String[] extensions() {
    return new String[] {"txt", "md"};
  }

  @Override
  public String mimeType() {
    return "text/plain";
  }

  @Override
  public ParsedDocument parse(Path blob) throws IOException {
    String text;
    try {
      text = Files.readString(blob, StandardCharsets.UTF_8).strip();
    } catch (IOException e) {
      throw new IOException("Unreadable text file.", e);
    }
    return new ParsedDocument(List.of(
        new ParsedDocument.ParsedPage(1, text, null, text.length() < PdfParser.BLANK_THRESHOLD)));
  }
}
