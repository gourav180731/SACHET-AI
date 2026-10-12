package ai.sachet.ingest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.springframework.stereotype.Component;

/**
 * PPTX parser (POI). Each slide maps to one page (slide N → page N) so
 * citations can point at slides. Slide titles become chunk sections.
 */
@Component
public class PptxParser implements DocumentParser {

  @Override
  public String[] extensions() {
    return new String[] {"pptx"};
  }

  @Override
  public String mimeType() {
    return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
  }

  @Override
  public ParsedDocument parse(Path blob) throws IOException {
    final XMLSlideShow show;
    try (var in = Files.newInputStream(blob)) {
      show = new XMLSlideShow(in);
    } catch (Exception e) {
      throw new IOException("Unreadable PPTX.", e);
    }
    try (show) {
      List<XSLFSlide> slides = show.getSlides();
      List<ParsedDocument.ParsedPage> out = new ArrayList<>(slides.size());
      int n = 0;
      for (XSLFSlide slide : slides) {
        n++;
        StringBuilder text = new StringBuilder();
        String title = null;
        for (XSLFShape shape : slide.getShapes()) {
          if (shape instanceof XSLFTextShape ts) {
            String t = ts.getText();
            if (t == null || t.isBlank()) {
              continue;
            }
            if (title == null && shape.getShapeName() != null
                && shape.getShapeName().toLowerCase(java.util.Locale.ROOT).contains("title")) {
              title = t.strip();
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
