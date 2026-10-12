package ai.sachet.ingest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

/** Renders PDF pages to PNG for OCR. Pure function of the stored blob. */
final class PdfPageRenderer {

  private PdfPageRenderer() {
  }

  /** Renders 1-based {@code pages} at 200 DPI into {@code outDir}; returns PNG paths in order. */
  static List<Path> render(Path pdf, List<Integer> pages, Path outDir) throws IOException {
    Files.createDirectories(outDir);
    try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
      PDFRenderer renderer = new PDFRenderer(doc);
      List<Path> out = new ArrayList<>(pages.size());
      for (int page : pages) {
        if (page < 1 || page > doc.getNumberOfPages()) {
          throw new IOException("Page out of range.");
        }
        Path png = outDir.resolve("ocr-p" + page + ".png");
        ImageIO.write(renderer.renderImageWithDPI(page - 1, 200), "PNG", png.toFile());
        out.add(png);
      }
      return out;
    } catch (IOException e) {
      throw new IOException("Could not render PDF pages.", e);
    }
  }
}
