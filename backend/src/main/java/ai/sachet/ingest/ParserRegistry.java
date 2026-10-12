package ai.sachet.ingest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Resolves a filename to its parser. Unknown extensions are rejected (never guessed). */
@Component
public class ParserRegistry {

  private final Map<String, DocumentParser> byExtension = new HashMap<>();

  public ParserRegistry(List<DocumentParser> parsers) {
    for (DocumentParser p : parsers) {
      for (String ext : p.extensions()) {
        byExtension.put(ext, p);
      }
    }
  }

  /** Returns the parser for {@code filename}, or null when unsupported. */
  public DocumentParser resolve(String filename) {
    return byExtension.get(PdfParser.extensionOf(filename));
  }

  public String supportedExtensions() {
    return String.join(",", byExtension.keySet().stream().sorted().toList());
  }
}
