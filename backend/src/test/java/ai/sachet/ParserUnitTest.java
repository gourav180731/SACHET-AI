package ai.sachet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.sachet.ingest.DocxParser;
import ai.sachet.ingest.DocumentParser;
import ai.sachet.ingest.ParsedDocument;
import ai.sachet.ingest.ParserRegistry;
import ai.sachet.ingest.PdfParser;
import ai.sachet.ingest.PptxParser;
import ai.sachet.ingest.TextParser;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Parser unit tests with generated fixtures (PDF/DOCX/PPTX/TXT/MD + blank scan). */
class ParserUnitTest {

  @TempDir Path tmp;

  private final ParserRegistry registry = new ParserRegistry(
      List.of(new PdfParser(), new DocxParser(), new PptxParser(), new TextParser()));

  private Path write(String name, byte[] bytes) throws Exception {
    Path p = tmp.resolve(name);
    Files.write(p, bytes);
    return p;
  }

  static byte[] samplePdf(String text) throws Exception {
    try (PDDocument doc = new PDDocument();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      PDPage page = new PDPage();
      doc.addPage(page);
      try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        cs.newLineAtOffset(50, 700);
        cs.showText(text);
        cs.endText();
      }
      doc.save(out);
      return out.toByteArray();
    }
  }

  @Test
  void registryResolves() {
    assertTrue(registry.resolve("a.pdf") instanceof PdfParser);
    assertTrue(registry.resolve("a.DOCX") instanceof DocxParser);
    assertTrue(registry.resolve("a.pptx") instanceof PptxParser);
    assertTrue(registry.resolve("a.md") instanceof TextParser);
    assertNull(registry.resolve("a.exe"));
    assertNull(registry.resolve("noextension"));
  }

  @Test
  void pdfTextAndPages() throws Exception {
    DocumentParser p = registry.resolve("sop.pdf");
    ParsedDocument parsed = p.parse(write("sop.pdf", samplePdf("Hello PDF world, this is a full page of text.")));
    assertEquals(1, parsed.pages().size());
    assertEquals(1, parsed.pages().get(0).pageNumber());
    assertTrue(parsed.pages().get(0).text().contains("Hello PDF world"));
    assertFalse(parsed.needsOcr());
  }

  @Test
  void blankPdfNeedsOcr() throws Exception {
    byte[] blank;
    try (PDDocument doc = new PDDocument();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      doc.addPage(new PDPage());
      doc.save(out);
      blank = out.toByteArray();
    }
    ParsedDocument parsed = registry.resolve("scan.pdf").parse(write("scan.pdf", blank));
    assertTrue(parsed.needsOcr());
  }

  @Test
  void docxHeadingsBecomeSections() throws Exception {
    byte[] bytes;
    try (XWPFDocument doc = new XWPFDocument();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      XWPFParagraph h = doc.createParagraph();
      h.setStyle("Heading1");
      XWPFRun hr = h.createRun();
      hr.setText("My Heading");
      XWPFParagraph b = doc.createParagraph();
      b.createRun().setText("Body paragraph one.");
      doc.write(out);
      bytes = out.toByteArray();
    }
    ParsedDocument parsed = registry.resolve("r.docx").parse(write("r.docx", bytes));
    assertTrue(parsed.pages().get(0).text().contains("My Heading"));
    assertTrue(parsed.pages().get(0).text().contains("Body paragraph one."));
  }

  @Test
  void pptxSlidesMapToPages() throws Exception {
    byte[] bytes;
    try (XMLSlideShow ppt = new XMLSlideShow();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      XSLFSlide s1 = ppt.createSlide();
      XSLFTextBox box = s1.createTextBox();
      box.setText("First slide content");
      XSLFSlide s2 = ppt.createSlide();
      XSLFTextBox box2 = s2.createTextBox();
      box2.setText("Second slide content");
      ppt.write(out);
      bytes = out.toByteArray();
    }
    ParsedDocument parsed = registry.resolve("d.pptx").parse(write("d.pptx", bytes));
    assertEquals(2, parsed.pages().size());
    assertTrue(parsed.pages().get(0).text().contains("First slide content"));
    assertTrue(parsed.pages().get(1).text().contains("Second slide content"));
  }

  @Test
  void textAndMarkdown() throws Exception {
    String md = "# Title\n\nSome body text here.\n";
    ParsedDocument parsed = registry.resolve("n.md")
        .parse(write("n.md", md.getBytes(StandardCharsets.UTF_8)));
    assertEquals(1, parsed.pages().get(0).pageNumber());
    assertTrue(parsed.pages().get(0).text().contains("Some body text"));
  }

  @Test
  void corruptPdfFails() throws Exception {
    DocumentParser p = registry.resolve("x.pdf");
    Path bad = write("x.pdf", "%PDF-1.4 not really a pdf".getBytes(StandardCharsets.UTF_8));
    try {
      p.parse(bad);
      assertFalse(true, "corrupt PDF must throw");
    } catch (java.io.IOException e) {
      assertNotNull(e.getMessage());
    }
  }
}
