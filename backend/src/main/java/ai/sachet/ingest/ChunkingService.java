package ai.sachet.ingest;

import ai.sachet.domain.DocumentChunk;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Provenance-preserving chunker. Rules:
 * <ul>
 *   <li>Chunks never cross page boundaries (page citations stay exact).</li>
 *   <li>Target ~4000 chars with ~400 chars of paragraph-aware overlap.</li>
 *   <li>Markdown-style {@code #} headings update the running section, which is
 *   carried across pages until the next heading.</li>
 * </ul>
 */
@Service
public class ChunkingService {

  static final int TARGET = 4000;
  static final int OVERLAP = 400;

  public record Chunk(int pageNumber, String section, String text) {
  }

  public List<Chunk> split(ParsedDocument parsed) {
    List<Chunk> out = new ArrayList<>();
    String runningSection = null;
    for (ParsedDocument.ParsedPage page : parsed.pages()) {
      List<String> units = paragraphs(page.text());
      String pageSection = runningSection;
      // Track sections within the page first so each chunk gets the right one.
      List<String> chunkTexts = new ArrayList<>();
      StringBuilder current = new StringBuilder();
      String currentSection = pageSection;
      List<String> chunkSections = new ArrayList<>();
      for (String unit : units) {
        String heading = headingOf(unit);
        if (heading != null) {
          // Headings start a fresh chunk so earlier text is never
          // retroactively attributed to the new section.
          if (!current.toString().isBlank()) {
            chunkTexts.add(current.toString().strip());
            chunkSections.add(currentSection);
            current = new StringBuilder();
          }
          runningSection = heading;
          currentSection = heading;
        }
        if (current.length() + unit.length() + 2 > TARGET && current.length() > 0) {
          chunkTexts.add(current.toString().strip());
          chunkSections.add(currentSection);
          current = new StringBuilder(overlapOf(current.toString()));
        }
        current.append(unit).append("\n\n");
      }
      if (!current.toString().isBlank()) {
        chunkTexts.add(current.toString().strip());
        chunkSections.add(currentSection);
      }
      for (int i = 0; i < chunkTexts.size(); i++) {
        out.add(new Chunk(page.pageNumber(), chunkSections.get(i), chunkTexts.get(i)));
      }
    }
    return out;
  }

  /** Splits text into paragraph units (blank-line separated; long lines split at sentences/words). */
  static List<String> paragraphs(String text) {
    List<String> units = new ArrayList<>();
    if (text == null || text.isBlank()) {
      return units;
    }
    for (String para : text.split("\\n\\s*\\n|\\r\\n\\s*\\r\\n")) {
      String p = para.strip();
      if (p.isEmpty()) {
        continue;
      }
      while (p.length() > TARGET) {
        int cut = splitPoint(p);
        units.add(p.substring(0, cut).strip());
        p = p.substring(cut).strip();
      }
      if (!p.isEmpty()) {
        units.add(p);
      }
    }
    return units;
  }

  /** Prefers sentence end, then word boundary, for the hard-split point. */
  private static int splitPoint(String p) {
    int sentence = Math.max(p.lastIndexOf(". ", TARGET), p.lastIndexOf(".\n", TARGET));
    if (sentence > TARGET / 2) {
      return sentence + 1;
    }
    int space = p.lastIndexOf(' ', TARGET);
    if (space > TARGET / 2) {
      return space;
    }
    return TARGET;
  }

  /** Trailing OVERLAP chars, backed to a word boundary. */
  static String overlapOf(String chunk) {
    if (chunk.length() <= OVERLAP) {
      return chunk;
    }
    String tail = chunk.substring(chunk.length() - OVERLAP);
    int space = tail.indexOf(' ');
    return space < 0 ? tail : tail.substring(space + 1);
  }

  /** Returns heading text for "# ..." lines, else null. */
  static String headingOf(String unit) {
    String first = unit.lines().findFirst().orElse("").strip();
    if (first.startsWith("#")) {
      String h = first.replaceFirst("^#+\\s*", "").strip();
      return h.isEmpty() ? null : h;
    }
    return null;
  }

  /** Convenience: builds persistent entities with global ordering. */
  public List<DocumentChunk> toEntities(String documentId, ParsedDocument parsed) {
    List<Chunk> chunks = split(parsed);
    List<DocumentChunk> entities = new ArrayList<>(chunks.size());
    for (int i = 0; i < chunks.size(); i++) {
      Chunk c = chunks.get(i);
      entities.add(new DocumentChunk(documentId, i, c.pageNumber(), c.section(), c.text()));
    }
    return entities;
  }
}
