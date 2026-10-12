package ai.sachet.ingest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.hslf.usermodel.HSLFShape;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTextShape;
import org.springframework.stereotype.Component;

/**
 * Legacy PowerPoint (.ppt) parser using Apache POI HSLF.
 * Each slide maps to one page (slide N → page N) so citations can point at slides.
 */
@Component
public class PptParser implements DocumentParser {

  @Override
  public String[] extensions() {
    return new String[] {"ppt"};
  }

  @Override
  public String mimeType() {
    return "application/vnd.ms-powerpoint";
  }

  @Override
  public ParsedDocument parse(Path blob) throws IOException {
    final HSLFSlideShow show;
    try (var in = Files.newInputStream(blob)) {
      show = new HSLFSlideShow(in);
    } catch (Exception e) {
      throw new IOException("Unreadable PPT: " + e.getMessage(), e);
    }
    try (show) {
      List<HSLFSlide> slides = show.getSlides();
      List<ParsedDocument.ParsedPage> out = new ArrayList<>(slides.size());
      int n = 0;
      for (HSLFSlide slide : slides) {
        n++;
        StringBuilder text = new StringBuilder();
        String title = slide.getTitle();
        for (HSLFShape shape : slide.getShapes()) {
          if (shape instanceof HSLFTextShape ts) {
            String t = ts.getText();
            if (t == null || t.isBlank()) {
              continue;
            }
            text.append(t.strip()).append("\n\n");
          }
        }
        String body = text.toString().strip();
        out.add(new ParsedDocument.ParsedPage(n, body, title, body.length() < PdfParser.BLANK_THRESHOLD));
      }
      return new ParsedDocument(List.copyOf(out));
    }
  }
}
