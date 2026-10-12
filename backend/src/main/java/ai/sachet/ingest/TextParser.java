package ai.sachet.ingest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.swing.text.rtf.RTFEditorKit;
import org.springframework.stereotype.Component;

/**
 * Text and structured text parser for txt, md, csv, json, xml, rtf, html, htm.
 * Plain-text and markup are extracted as clean text for semantic indexing.
 */
@Component
public class TextParser implements DocumentParser {

  @Override
  public String[] extensions() {
    return new String[] {"txt", "md", "csv", "rtf", "html", "htm", "json", "xml"};
  }

  @Override
  public String mimeType() {
    return "text/plain";
  }

  @Override
  public ParsedDocument parse(Path blob) throws IOException {
    String ext = PdfParser.extensionOf(blob.getFileName().toString());
    String text;

    if ("rtf".equals(ext)) {
      text = parseRtf(blob);
    } else if ("html".equals(ext) || "htm".equals(ext)) {
      text = parseHtml(blob);
    } else {
      try {
        text = Files.readString(blob, StandardCharsets.UTF_8).strip();
      } catch (Exception e) {
        // Fallback to ISO_8859_1 if file is not UTF-8
        text = Files.readString(blob, StandardCharsets.ISO_8859_1).strip();
      }
    }

    return new ParsedDocument(List.of(
        new ParsedDocument.ParsedPage(1, text, null, text.length() < PdfParser.BLANK_THRESHOLD)));
  }

  private String parseRtf(Path blob) {
    try (InputStream in = Files.newInputStream(blob)) {
      RTFEditorKit rtf = new RTFEditorKit();
      javax.swing.text.Document doc = rtf.createDefaultDocument();
      rtf.read(in, doc, 0);
      return doc.getText(0, doc.getLength()).strip();
    } catch (Exception e) {
      try {
        String raw = Files.readString(blob, StandardCharsets.ISO_8859_1);
        return raw.replaceAll("\\\\[a-zA-Z0-9]+", " ")
                  .replaceAll("[{}]", "")
                  .replaceAll("\\s+", " ")
                  .strip();
      } catch (Exception ignored) {
        return "";
      }
    }
  }

  private String parseHtml(Path blob) throws IOException {
    String raw;
    try {
      raw = Files.readString(blob, StandardCharsets.UTF_8);
    } catch (Exception e) {
      raw = Files.readString(blob, StandardCharsets.ISO_8859_1);
    }
    return raw.replaceAll("(?is)<script.*?</script>", " ")
              .replaceAll("(?is)<style.*?</style>", " ")
              .replaceAll("<[^>]+>", " ")
              .replace("&nbsp;", " ")
              .replace("&amp;", "&")
              .replace("&lt;", "<")
              .replace("&gt;", ">")
              .replace("&quot;", "\"")
              .replace("&#39;", "'")
              .replaceAll("\\s+", " ")
              .strip();
  }
}
