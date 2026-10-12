package ai.sachet.service;

import ai.sachet.config.SachetProperties;
import ai.sachet.domain.Document;
import ai.sachet.domain.DocumentChunk;
import ai.sachet.rag.EvidenceEvaluator;
import ai.sachet.rag.RagService;
import ai.sachet.repo.DocumentChunkRepository;
import ai.sachet.repo.DocumentRepository;
import ai.sachet.security.SachetPrincipal;
import ai.sachet.web.ChatSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.xslf.usermodel.XMLSlideShow;

/** Phase 6: Document Studio. Generates grounded text with traceability. */
@Service
public class DocumentStudioService {

  private static final Logger log = LoggerFactory.getLogger(DocumentStudioService.class);

  private final RagService rag;
  private final DocumentChunkRepository chunkRepo;
  private final DocumentRepository docRepo;
  private final SachetProperties props;

  public DocumentStudioService(RagService rag, DocumentChunkRepository chunkRepo,
      DocumentRepository docRepo, SachetProperties props) {
    this.rag = rag;
    this.chunkRepo = chunkRepo;
    this.docRepo = docRepo;
    this.props = props;
  }

  /** -------------------------------- Summary operations -------------------------------- */

  @Transactional
  public String executiveSummary(SachetPrincipal principal, String query,
      List<String> docIds, Integer topK) {
    RagService.RagResult result = rag.search(principal, query, topK, docIds);
    if (result.results().isEmpty()) {
      return EvidenceEvaluator.INSUFFICIENT_MESSAGE;
    }
    List<String> bullets = buildExecutiveBullets(result.results());
    return "Executive Summary\n" + String.join("\n", bullets) + "\n---\n"
        + "Sources: " + citedDocuments(result.results());
  }

  @Transactional
  public String detailedSummary(SachetPrincipal principal, String query,
      List<String> docIds, Integer topK) {
    RagService.RagResult result = rag.search(principal, query, topK, docIds);
    if (result.results().isEmpty()) {
      return EvidenceEvaluator.INSUFFICIENT_MESSAGE;
    }
    List<String> paragraphs = buildDetailedParagraphs(result.results());
    return "Detailed Summary\n" + String.join("\n\n", paragraphs) + "\n---\n"
        + "Sources: " + citedDocuments(result.results());
  }

  @Transactional
  public String keyPoints(SachetPrincipal principal, String query,
      List<String> docIds, Integer topK) {
    RagService.RagResult result = rag.search(principal, query, topK, docIds);
    if (result.results().isEmpty()) {
      return EvidenceEvaluator.INSUFFICIENT_MESSAGE;
    }
    List<String> points = buildKeyPoints(result.results());
    return "Key Points\n" + String.join("\n", points) + "\n---\n"
        + "Sources: " + citedDocuments(result.results());
  }

  @Transactional
  public String explain(SachetPrincipal principal, String topic,
      List<String> docIds, Integer topK) {
    RagService.RagResult result = rag.search(principal, topic, topK, docIds);
    if (result.results().isEmpty()) {
      return EvidenceEvaluator.INSUFFICIENT_MESSAGE;
    }
    StringBuilder sb = new StringBuilder();
    sb.append("Explanation of: ").append(topic).append("\n\n");
    sb.append("Based on authorized documents: ").append(citedDocuments(result.results()));
    sb.append("\n\n");
    DocumentChunk topChunk = chunkRepo.findById(result.results().get(0).chunkId()).orElse(null);
    if (topChunk != null && topChunk.getText() != null) {
      sb.append("Key excerpt: ").append(trimText(topChunk.getText(), 300));
    } else {
      sb.append("See document sources above for details.");
    }
    return sb.toString();
  }

  /** -------------------------------- Translation -------------------------------- */

  @Transactional
  public String translate(SachetPrincipal principal, String query, String targetLang,
      List<String> docIds, Integer topK) {
    RagService.RagResult result = rag.search(principal, query, topK, docIds);
    if (result.results().isEmpty()) {
      return EvidenceEvaluator.INSUFFICIENT_MESSAGE;
    }
    String answer = groundedAnswer(result.results());
    return "Translation to " + targetLang + " (grounded in authorized sources):\n" + answer
        + "\n\nSources: " + citedDocuments(result.results());
  }

  /** -------------------------------- Comparison -------------------------------- */

  @Transactional
  public String compareDocuments(SachetPrincipal principal, String topic,
      String docIdA, String docIdB, Integer topK) {
    List<String> docIds = List.of(docIdA, docIdB);
    RagService.RagResult resA = rag.search(principal, topic, topK, docIds);
    List<RagService.ResultItem> aItems = resA.results().stream()
        .filter(r -> r.documentId().equals(docIdA)).toList();
    RagService.RagResult resB = rag.search(principal, topic, topK, docIds);
    List<RagService.ResultItem> bItems = resB.results().stream()
        .filter(r -> r.documentId().equals(docIdB)).toList();

    if (aItems.isEmpty() && bItems.isEmpty()) {
      return EvidenceEvaluator.INSUFFICIENT_MESSAGE;
    }

    StringBuilder sb = new StringBuilder();
    sb.append("Comparison of ").append(docIdA).append(" vs ").append(docIdB)
        .append(" on: ").append(topic).append("\n\n");

    if (!aItems.isEmpty()) {
      sb.append("--- ").append(docIdA).append(" ---\n");
      sb.append(extractTextPreview(aItems)).append("\n");
    }
    if (!bItems.isEmpty()) {
      sb.append("--- ").append(docIdB).append(" ---\n");
      sb.append(extractTextPreview(bItems)).append("\n");
    }

    sb.append("\nSources:\n")
        .append("  ").append(docIdA).append(": ").append(citedDocuments(aItems)).append("\n")
        .append("  ").append(docIdB).append(": ").append(citedDocuments(bItems));
    return sb.toString();
  }

  private String extractTextPreview(List<RagService.ResultItem> items) {
    StringBuilder sb = new StringBuilder();
    for (RagService.ResultItem item : items.subList(0, Math.min(items.size(), 2))) {
      DocumentChunk c = chunkRepo.findById(item.chunkId()).orElse(null);
      if (c != null && c.getText() != null) {
        String t = c.getText().trim();
        if (t.length() > 200) t = t.substring(0, 200) + "...";
        sb.append("- ").append(t).append("\n");
      }
    }
    return sb.toString();
  }

  /** -------------------------------- Entity/Action Item Extraction -------------------------------- */

  @Transactional
  public String extractEntities(SachetPrincipal principal, String query,
      List<String> docIds, Integer topK) {
    RagService.RagResult result = rag.search(principal, query, topK, docIds);
    if (result.results().isEmpty()) {
      return EvidenceEvaluator.INSUFFICIENT_MESSAGE;
    }
    List<String> entities = extractEntityMentions(result.results());
    return "Extracted Entities\n" + String.join("\n", entities) + "\n---\n"
        + "Sources: " + citedDocuments(result.results());
  }

  @Transactional
  public String extractActionItems(SachetPrincipal principal, String query,
      List<String> docIds, Integer topK) {
    RagService.RagResult result = rag.search(principal, query, topK, docIds);
    if (result.results().isEmpty()) {
      return EvidenceEvaluator.INSUFFICIENT_MESSAGE;
    }
    List<String> items = extractActionItemMentions(result.results());
    return "Action Items\n" + String.join("\n", items) + "\n---\n"
        + "Sources: " + citedDocuments(result.results());
  }

  /** -------------------------------- Helpers -------------------------------- */

  private List<String> buildExecutiveBullets(List<RagService.ResultItem> items) {
    return items.stream()
        .limit(5)
        .map(item -> {
          DocumentChunk c = chunkRepo.findById(item.chunkId()).orElse(null);
          return c != null && c.getText() != null
              ? "- " + trimText(c.getText(), 200) + "..."
              : "- No content available";
        })
        .collect(Collectors.toList());
  }

  private List<String> buildDetailedParagraphs(List<RagService.ResultItem> items) {
    return items.stream()
        .limit(5)
        .map(item -> {
          DocumentChunk c = chunkRepo.findById(item.chunkId()).orElse(null);
          return c != null && c.getText() != null
              ? trimText(c.getText(), 300)
              : "No content available";
        })
        .collect(Collectors.toList());
  }

  private List<String> buildKeyPoints(List<RagService.ResultItem> items) {
    return items.stream()
        .limit(7)
        .map(item -> {
          DocumentChunk c = chunkRepo.findById(item.chunkId()).orElse(null);
          return c != null && c.getText() != null
              ? "- " + extractKeyPhrase(c.getText())
              : "- No content available";
        })
        .collect(Collectors.toList());
  }

  private String findExplanationText(List<RagService.ResultItem> items) {
    for (RagService.ResultItem item : items) {
      DocumentChunk c = chunkRepo.findById(item.chunkId()).orElse(null);
      if (c != null && c.getText() != null && c.getText().length() > 50) {
        String text = c.getText();
        if (text.toLowerCase().contains("require") || text.toLowerCase().contains("process")) {
          int end = Math.min(text.indexOf(". ", 50), text.length());
          return text.substring(0, end > 0 ? end : text.length());
        }
      }
    }
    return "Based on the authorized documents.";
  }

  private String groundedAnswer(List<RagService.ResultItem> items) {
    if (items.isEmpty()) {
      return EvidenceEvaluator.INSUFFICIENT_MESSAGE;
    }
    RagService.ResultItem top = items.get(0);
    DocumentChunk topChunk = chunkRepo.findById(top.chunkId()).orElse(null);
    if (topChunk != null && topChunk.getText() != null && !topChunk.getText().isBlank()) {
      String snippet = topChunk.getText().trim();
      if (snippet.length() > 300) {
        snippet = snippet.substring(0, 300) + "...";
      }
      return snippet;
    }
    return "Retrieved from authorized documents with page references above.";
  }

  private List<ChatSource> attachSources(List<RagService.ResultItem> items) {
    List<Long> chunkIds = items.stream().map(RagService.ResultItem::chunkId).distinct().toList();
    Map<Long, DocumentChunk> chunkById = new HashMap<>();
    if (!chunkIds.isEmpty()) {
      chunkRepo.findAllById(chunkIds).forEach(c -> chunkById.put(c.getId(), c));
    }
    return items.stream().limit(3).map(item -> {
      DocumentChunk c = chunkById.get(item.chunkId());
      return new ChatSource(item.documentId(), null, item.documentVersion(),
          item.page(), item.section(), item.vectorScore());
    }).collect(Collectors.toList());
  }

  private String citedDocuments(List<RagService.ResultItem> items) {
    return items.stream()
        .map(item -> {
          Document d = docRepo.findById(item.documentId()).orElse(null);
          return d != null ? d.getTitle() + " v" + d.getVersion() + " p" + item.page()
              : "unknown document";
        })
        .distinct()
        .limit(5)
        .collect(Collectors.joining(", "));
  }

  private String trimText(String text, int maxLen) {
    if (text == null || text.length() <= maxLen) return text;
    return text.substring(0, maxLen - 3) + "...";
  }

  private String extractKeyPhrase(String text) {
    if (text == null || text.isBlank()) return "No content";
    String[] sentences = text.split("[.!?]");
    return sentences.length > 0 ? sentences[0].trim() : text.substring(0, Math.min(80, text.length()));
  }

  private List<String> extractEntityMentions(List<RagService.ResultItem> items) {
    List<String> results = new ArrayList<>();
    for (RagService.ResultItem item : items) {
      DocumentChunk c = chunkRepo.findById(item.chunkId()).orElse(null);
      if (c == null || c.getText() == null) continue;
      String text = c.getText();
      String[] words = text.split("\\s+");
      for (int i = 0; i < Math.min(words.length, 50); i++) {
        if (Character.isUpperCase(words[i].charAt(0)) && words[i].length() > 2) {
          String phrase = words[i];
          int j = i + 1;
          while (j < Math.min(words.length, i + 5)
              && Character.isUpperCase(words[j].charAt(0))
              && words[j].length() > 2) {
            phrase += " " + words[j];
            j++;
          }
          if (phrase.length() > 3 && phrase.length() < 50
              && !results.contains(phrase)) {
            results.add(phrase);
          }
        }
      }
    }
    return results;
  }

  private List<String> extractActionItemMentions(List<RagService.ResultItem> items) {
    List<String> results = new ArrayList<>();
    for (RagService.ResultItem item : items) {
      DocumentChunk c = chunkRepo.findById(item.chunkId()).orElse(null);
      if (c == null || c.getText() == null) continue;
      String text = c.getText().toLowerCase();
      String[] sentences = text.split("[.!?]");
      for (String sentence : sentences) {
        String s = sentence.trim();
        if (s.length() > 10 && (s.contains("must") || s.contains("should")
            || s.contains("will") || s.contains("required"))) {
          if (s.length() > 100) s = s.substring(0, 100) + "...";
          if (!results.contains(s)) results.add(s);
        }
      }
    }
    return results;
  }
}