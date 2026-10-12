package ai.sachet.service;

import ai.sachet.config.SachetProperties;
import ai.sachet.rag.EvidenceEvaluator;
import ai.sachet.rag.RagService;
import ai.sachet.domain.Classification;
import ai.sachet.domain.Document;
import ai.sachet.domain.DocumentChunk;
import ai.sachet.repo.DocumentChunkRepository;
import ai.sachet.repo.DocumentRepository;
import ai.sachet.security.SachetPrincipal;
import ai.sachet.web.ChatRequest;
import ai.sachet.web.ChatResponse;
import ai.sachet.web.ChatSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import reactor.core.publisher.Flux;

/**
 * Phase 5/7: Text Assistant with optional LLM generation.
 * 
 * Security property: The model/LLM layer never has unrestricted access to the
 * document store. Only permission-filtered, cited results from RagService.search()
 * are used. No raw document text is ever sent to a model.
 * 
 * Two modes:
 * 1. GROUNDED_TEXT (default): Pure text grounding from RAG results
 * 2. LLM_GENERATED: Streaming AI response grounded in RAG context
 */
@Service
public class ChatService {

  private static final Logger log = LoggerFactory.getLogger(ChatService.class);

  private final RagService rag;
  private final DocumentChunkRepository chunkRepo;
  private final DocumentRepository docRepo;
  private final SachetProperties props;
  private final ModelGatewayFactory modelGatewayFactory;
  private final ModelPolicy modelPolicy;

  public ChatService(RagService rag, DocumentChunkRepository chunkRepo,
      DocumentRepository docRepo, SachetProperties props,
      ModelGatewayFactory modelGatewayFactory, ModelPolicy modelPolicy) {
    this.rag = rag;
    this.chunkRepo = chunkRepo;
    this.docRepo = docRepo;
    this.props = props;
    this.modelGatewayFactory = modelGatewayFactory;
    this.modelPolicy = modelPolicy;
  }

  /** Main entry point: RAG-augmented chat response (grounded text mode). */
  @Transactional(readOnly = true)
  public ChatResponse chat(SachetPrincipal principal, ChatRequest req) {
    boolean useLLM = modelGatewayFactory.isAnyAvailable();
    return chatInternal(principal, req, useLLM, req.responseMode(), req.language(), req.customPrompt());
  }

  /** Streaming AI generation with RAG grounding (takes extra params from ChatRequest). */
  public Flux<String> chatStream(SachetPrincipal principal, ChatRequest req,
      String responseMode, String language, String customPrompt) {
    return chatStreamInternal(principal, req, responseMode, language, customPrompt);
  }

  /** Unified internal method for both modes. */
  private ChatResponse chatInternal(SachetPrincipal principal, ChatRequest req,
      boolean useLLM, String responseMode, String language, String customPrompt) {

    RagService.RagResult result = rag.search(principal, req.query(), req.topK(), req.docIds());

    if (result.evidenceStatus().equals(EvidenceEvaluator.EvidenceStatus.INSUFFICIENT)) {
      return new ChatResponse(
          EvidenceEvaluator.INSUFFICIENT_MESSAGE,
          EvidenceEvaluator.EvidenceStatus.INSUFFICIENT.name(),
          EvidenceEvaluator.INSUFFICIENT_MESSAGE,
          List.of());
    }

    List<ChatSource> sources = buildSources(result.results());
    Classification strictest = computeStrictestClassification(result.results());

    if (useLLM && modelGatewayFactory.isAnyAvailable()) {
      if (!modelPolicy.allowsExternal(strictest)) {
        log.info("External LLM transfer blocked by ModelPolicy for classification {}. Serving local grounded response.", strictest);
      } else {
        // Build RAG context for LLM
        String ragContext = buildRagContext(result.results());
        String lang = language != null ? language : props.getDefaultRagMode();
        String mode = responseMode != null ? responseMode : props.getDefaultRagMode();

        try {
          ModelGateway gateway = modelGatewayFactory.getGateway();
          String answer = gateway.generateGrounded(
              principal, req.query(), ragContext, lang, mode, req.customPrompt());
          return new ChatResponse(answer, result.evidenceStatus().name(), null, sources);
        } catch (Exception e) {
          log.warn("LLM generation failed, falling back to grounded text: {}", e.getMessage());
          // Fall back to grounded text
        }
      }
    }

    // Grounded text mode (default / local fallback)
    String answer = groundedAnswer(result.results());
    return new ChatResponse(answer, result.evidenceStatus().name(), null, sources);
  }

  private Flux<String> chatStreamInternal(SachetPrincipal principal, ChatRequest req,
      String responseMode, String language, String customPrompt) {

    RagService.RagResult result = rag.search(principal, req.query(), req.topK(), req.docIds());

    if (result.evidenceStatus().equals(EvidenceEvaluator.EvidenceStatus.INSUFFICIENT)) {
      return Flux.just(EvidenceEvaluator.INSUFFICIENT_MESSAGE);
    }

    List<ChatSource> sources = buildSources(result.results());
    Classification strictest = computeStrictestClassification(result.results());

    if (!modelGatewayFactory.isAnyAvailable() || !modelPolicy.allowsExternal(strictest)) {
      if (modelGatewayFactory.isAnyAvailable() && !modelPolicy.allowsExternal(strictest)) {
        log.info("External LLM streaming blocked by ModelPolicy for classification {}. Serving local grounded text.", strictest);
      }
      // No LLM available or policy forbids external transfer - stream the grounded text
      String answer = groundedAnswer(result.results());
      return Flux.just(answer);
    }

    String ragContext = buildRagContext(result.results());
    String lang = language != null ? language : "en-IN";
    String mode = responseMode != null ? responseMode : props.getDefaultRagMode();

    ModelGateway gateway = modelGatewayFactory.getGateway();
    return gateway.generateGroundedStream(principal, req.query(), ragContext, lang, mode, customPrompt)
        .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
        .onErrorResume(e -> {
          log.warn("LLM streaming failed, falling back to grounded text: {}", e.getMessage());
          return Flux.just(groundedAnswer(result.results()));
        });
  }

  /** Compute strictest classification across retrieved chunks to guard external egress. */
  private Classification computeStrictestClassification(List<RagService.ResultItem> items) {
    Classification strictest = Classification.PUBLIC;
    for (RagService.ResultItem item : items) {
      Document doc = docRepo.findById(item.documentId()).orElse(null);
      if (doc != null && doc.getClassification() != null) {
        if (doc.getClassification().rank() > strictest.rank()) {
          strictest = doc.getClassification();
        }
      }
    }
    return strictest;
  }

  /** Build RAG context string for LLM with citations. */
  private String buildRagContext(List<RagService.ResultItem> items) {
    if (items.isEmpty()) return "";

    Map<String, List<RagService.ResultItem>> byDoc = items.stream()
        .collect(Collectors.groupingBy(RagService.ResultItem::documentId));

    StringBuilder sb = new StringBuilder();
    int chunkNum = 1;

    for (Map.Entry<String, List<RagService.ResultItem>> entry : byDoc.entrySet()) {
      String docId = entry.getKey();
      List<RagService.ResultItem> itemList = entry.getValue();
      Document doc = docRepo.findById(docId).orElse(null);
      String docTitle = doc != null ? doc.getTitle() : docId;
      int docVersion = doc != null ? doc.getVersion() : 1;

      sb.append(String.format("[SOURCE %d: %s v%d]\n", chunkNum++, docTitle, docVersion));

      for (RagService.ResultItem item : itemList) {
        DocumentChunk c = chunkRepo.findById(item.chunkId()).orElse(null);
        if (c != null && c.getText() != null) {
          String excerpt = c.getText().trim();
          if (excerpt.length() > 500) excerpt = excerpt.substring(0, 500) + "...";
          sb.append(String.format("  (p%d", c.getPageNumber()));
          if (c.getSection() != null && !c.getSection().isBlank()) {
            sb.append(String.format(" §%s", c.getSection()));
          }
          sb.append(") ").append(excerpt).append("\n");
        }
      }
      sb.append("\n");
    }

    return sb.toString();
  }

  /** Build ChatSource citations from ResultItem list. */
  private List<ChatSource> buildSources(List<RagService.ResultItem> items) {
    List<Long> chunkIds = items.stream().map(RagService.ResultItem::chunkId).distinct().toList();
    Map<Long, DocumentChunk> chunkById = new HashMap<>();
    if (!chunkIds.isEmpty()) {
      chunkRepo.findAllById(chunkIds).forEach(c -> chunkById.put(c.getId(), c));
    }

    List<String> docIds = items.stream().map(RagService.ResultItem::documentId).distinct().toList();
    Map<String, Document> docById = new HashMap<>();
    if (!docIds.isEmpty()) {
      docRepo.findAllById(docIds).forEach(d -> docById.put(d.getId(), d));
    }

    return items.stream().distinct().map(item -> {
      DocumentChunk c = chunkById.get(item.chunkId());
      Document d = docById.get(item.documentId());
      String title = d != null ? d.getTitle() : "unknown";
      return new ChatSource(
          item.documentId(),
          title,
          item.documentVersion(),
          item.page(),
          item.section(),
          item.vectorScore());
    }).collect(Collectors.toList());
  }

  /** Generate a grounded answer from the retrieved result items (no LLM). */
  private String groundedAnswer(List<RagService.ResultItem> items) {
    if (items.isEmpty()) {
      return EvidenceEvaluator.INSUFFICIENT_MESSAGE;
    }

    Map<String, List<RagService.ResultItem>> byDoc = items.stream()
        .collect(Collectors.groupingBy(RagService.ResultItem::documentId));

    StringBuilder sb = new StringBuilder();
    sb.append("### Grounded Document Intelligence\n\n");
    sb.append("Based on authorized records, here is the synthesis of information:\n\n");

    for (Map.Entry<String, List<RagService.ResultItem>> entry : byDoc.entrySet()) {
      String docId = entry.getKey();
      List<RagService.ResultItem> itemList = entry.getValue();
      Document doc = docRepo.findById(docId).orElse(null);
      String title = doc != null ? doc.getTitle() : docId;
      int version = doc != null ? doc.getVersion() : 1;

      sb.append("**Source: ").append(title).append(" (v").append(version).append(")**\n");

      for (RagService.ResultItem item : itemList) {
        DocumentChunk c = chunkRepo.findById(item.chunkId()).orElse(null);
        if (c != null && c.getText() != null && !c.getText().isBlank()) {
          String text = c.getText().trim();
          // Extract leading sentences or clean paragraph
          String[] lines = text.split("\n+");
          String excerpt = lines.length > 0 ? lines[0] : text;
          if (excerpt.length() > 400) excerpt = excerpt.substring(0, 400) + "...";
          sb.append("- Page ").append(c.getPageNumber());
          if (c.getSection() != null && !c.getSection().isBlank()) {
            sb.append(" (§").append(c.getSection()).append(")");
          }
          sb.append(": ").append(excerpt);
          sb.append(" [doc:").append(title).append(" v").append(version).append(" p").append(c.getPageNumber()).append("]\n");
        }
      }
      sb.append("\n");
    }

    return sb.toString().trim();
  }
}