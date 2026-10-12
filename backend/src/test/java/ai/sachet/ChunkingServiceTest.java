package ai.sachet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.sachet.domain.DocumentChunk;
import ai.sachet.ingest.ChunkingService;
import ai.sachet.ingest.ParsedDocument;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Chunker: page boundaries, overlap, section tracking. */
class ChunkingServiceTest {

  private final ChunkingService chunking = new ChunkingService();

  @Test
  void shortPageIsSingleChunk() {
    var parsed = new ParsedDocument(List.of(
        new ParsedDocument.ParsedPage(1, "Just a short note.", null, false)));
    List<ChunkingService.Chunk> chunks = chunking.split(parsed);
    assertEquals(1, chunks.size());
    assertEquals(1, chunks.get(0).pageNumber());
  }

  @Test
  void chunksNeverCrossPages() {
    String p1 = "Alpha " + "word ".repeat(1200);
    String p2 = "Beta " + "term ".repeat(1200);
    var parsed = new ParsedDocument(List.of(
        new ParsedDocument.ParsedPage(1, p1, null, false),
        new ParsedDocument.ParsedPage(2, p2, null, false)));
    List<ChunkingService.Chunk> chunks = chunking.split(parsed);
    for (var c : chunks) {
      if (c.pageNumber() == 1) {
        assertTrue(!c.text().contains("Beta"), "page-1 chunk leaked page-2 text");
      } else {
        assertTrue(!c.text().contains("Alpha"), "page-2 chunk leaked page-1 text");
      }
    }
  }

  @Test
  void overlapExistsOnLongPages() {
    String long1 = "content ".repeat(1500); // ~12000 chars
    var parsed = new ParsedDocument(List.of(
        new ParsedDocument.ParsedPage(3, long1, null, false)));
    List<ChunkingService.Chunk> chunks = chunking.split(parsed);
    assertTrue(chunks.size() >= 3, "expected several chunks, got " + chunks.size());
    int total = chunks.stream().mapToInt(c -> c.text().length()).sum();
    assertTrue(total > long1.length(), "no overlap detected");
    // every source word survives somewhere
    for (String w : new String[] {"content"}) {
      assertTrue(chunks.stream().anyMatch(c -> c.text().contains(w)));
    }
  }

  @Test
  void headingsBecomeSections() {
    String text = "# Intro\n\nFirst para.\n\n# Details\n\nSecond para here.";
    var parsed = new ParsedDocument(List.of(
        new ParsedDocument.ParsedPage(1, text, null, false)));
    List<ChunkingService.Chunk> chunks = chunking.split(parsed);
    assertTrue(chunks.stream().anyMatch(c -> "Intro".equals(c.section())));
    assertTrue(chunks.stream().anyMatch(c -> "Details".equals(c.section())));
    // text before the second heading is NOT attributed to Details
    var first = chunks.get(0);
    assertEquals("Intro", first.section());
  }

  @Test
  void sectionCarriesAcrossPages() {
    var parsed = new ParsedDocument(List.of(
        new ParsedDocument.ParsedPage(1, "# Scope\n\nPage one text.", null, false),
        new ParsedDocument.ParsedPage(2, "Page two continues.", null, false)));
    List<ChunkingService.Chunk> chunks = chunking.split(parsed);
    assertTrue(chunks.stream().filter(c -> c.pageNumber() == 2)
        .allMatch(c -> "Scope".equals(c.section())));
  }

  @Test
  void entitiesAreOrdered() {
    var parsed = new ParsedDocument(List.of(
        new ParsedDocument.ParsedPage(1, "a ".repeat(2000), null, false)));
    List<DocumentChunk> entities = chunking.toEntities("doc-1", parsed);
    for (int i = 0; i < entities.size(); i++) {
      assertEquals(i, entities.get(i).getChunkIndex());
      assertEquals("doc-1", entities.get(i).getDocumentId());
    }
  }
}
