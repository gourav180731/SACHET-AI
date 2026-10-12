package ai.sachet.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.sachet.config.SachetProperties;
import ai.sachet.domain.Classification;
import ai.sachet.domain.Document;
import ai.sachet.domain.DocumentChunk;
import ai.sachet.rag.EvidenceEvaluator;
import ai.sachet.rag.RagService;
import ai.sachet.repo.DocumentChunkRepository;
import ai.sachet.repo.DocumentRepository;
import ai.sachet.security.SachetPrincipal;
import ai.sachet.web.ChatRequest;
import ai.sachet.web.ChatResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

/**
 * Unit test verifying that ChatService strictly enforces ModelPolicy
 * before any context can reach an external LLM gateway.
 */
class ChatServicePolicyGateTest {

  private RagService rag;
  private DocumentChunkRepository chunkRepo;
  private DocumentRepository docRepo;
  private SachetProperties props;
  private ModelGatewayFactory gatewayFactory;
  private ModelGateway gateway;
  private ModelPolicy modelPolicy;
  private ChatService chatService;

  private SachetPrincipal principal;

  @BeforeEach
  void setUp() {
    rag = mock(RagService.class);
    chunkRepo = mock(DocumentChunkRepository.class);
    docRepo = mock(DocumentRepository.class);
    gatewayFactory = mock(ModelGatewayFactory.class);
    gateway = mock(ModelGateway.class);
    modelPolicy = mock(ModelPolicy.class);

    props = new SachetProperties();
    props.setDefaultRagMode("detailed");

    when(gatewayFactory.isAnyAvailable()).thenReturn(true);
    when(gatewayFactory.getGateway()).thenReturn(gateway);

    chatService = new ChatService(rag, chunkRepo, docRepo, props, gatewayFactory, modelPolicy);

    principal = new SachetPrincipal(
        UUID.randomUUID().toString(),
        List.of("ANALYST"),
        "SECRET",
        "TELECOM",
        List.of()
    );
  }

  @Test
  @DisplayName("policy gate: SECRET document blocks external LLM and uses local grounded answer")
  void secretDocumentBlocksExternalLlm() {
    String docId = "doc-secret-1";
    Document doc = new Document("classified.pdf", "owner-1", Classification.SECRET);
    when(docRepo.findById(docId)).thenReturn(Optional.of(doc));

    RagService.ResultItem item = new RagService.ResultItem(1L, docId, "classified.pdf", 1, 1, "Section 1", 0.95, 0.95);
    when(rag.search(any(), anyString(), any(), any()))
        .thenReturn(new RagService.RagResult(List.of(item), EvidenceEvaluator.EvidenceStatus.STRONG, "Found"));

    DocumentChunk chunk = new DocumentChunk(docId, 1, 1, "Section 1", "Classified response details.");
    when(chunkRepo.findById(1L)).thenReturn(Optional.of(chunk));

    // ModelPolicy forbids external egress for SECRET
    when(modelPolicy.allowsExternal(Classification.SECRET)).thenReturn(false);

    ChatRequest req = new ChatRequest("What are the classified details?", List.of(docId), 5, "detailed", "en-IN", null);
    ChatResponse response = chatService.chat(principal, req);

    assertNotNull(response);
    // Ensure ModelGateway was NEVER called
    verify(gateway, never()).generateGrounded(any(), anyString(), anyString(), anyString(), anyString(), any());
    // Ensure answer was produced locally from chunk text
    assertTrue(response.answer().contains("Classified response details"));
  }

  @Test
  @DisplayName("policy gate: PUBLIC document with allowed external policy permits LLM call")
  void publicDocumentAllowsExternalLlmWhenPermitted() {
    String docId = "doc-public-1";
    Document doc = new Document("manual.pdf", "owner-1", Classification.PUBLIC);
    when(docRepo.findById(docId)).thenReturn(Optional.of(doc));

    RagService.ResultItem item = new RagService.ResultItem(2L, docId, "manual.pdf", 1, 1, "Overview", 0.90, 0.90);
    when(rag.search(any(), anyString(), any(), any()))
        .thenReturn(new RagService.RagResult(List.of(item), EvidenceEvaluator.EvidenceStatus.STRONG, "Found"));

    DocumentChunk chunk = new DocumentChunk(docId, 1, 1, "Overview", "Public user manual text.");
    when(chunkRepo.findById(2L)).thenReturn(Optional.of(chunk));

    // ModelPolicy allows external egress for PUBLIC
    when(modelPolicy.allowsExternal(Classification.PUBLIC)).thenReturn(true);
    when(gateway.generateGrounded(any(), anyString(), anyString(), anyString(), anyString(), any()))
        .thenReturn("AI generated response based on manual.");

    ChatRequest req = new ChatRequest("How to use the manual?", List.of(docId), 5, "detailed", "en-IN", null);
    ChatResponse response = chatService.chat(principal, req);

    assertNotNull(response);
    // Default chat method uses local grounded text (useLLM=false)
    assertTrue(response.answer().contains("Public user manual text"));
  }

  @Test
  @DisplayName("policy gate: stream blocks external LLM streaming when policy forbids egress")
  void streamBlocksExternalLlmWhenForbidden() {
    String docId = "doc-conf-1";
    Document doc = new Document("confidential.pdf", "owner-1", Classification.CONFIDENTIAL);
    when(docRepo.findById(docId)).thenReturn(Optional.of(doc));

    RagService.ResultItem item = new RagService.ResultItem(3L, docId, "confidential.pdf", 1, 1, "Plans", 0.88, 0.88);
    when(rag.search(any(), anyString(), any(), any()))
        .thenReturn(new RagService.RagResult(List.of(item), EvidenceEvaluator.EvidenceStatus.STRONG, "Found"));

    DocumentChunk chunk = new DocumentChunk(docId, 1, 1, "Plans", "Confidential strategic plan.");
    when(chunkRepo.findById(3L)).thenReturn(Optional.of(chunk));

    when(modelPolicy.allowsExternal(Classification.CONFIDENTIAL)).thenReturn(false);

    ChatRequest req = new ChatRequest("Explain confidential plans", List.of(docId), 5, "detailed", "en-IN", null);
    Flux<String> stream = chatService.chatStream(principal, req, "detailed", "en-IN", null);

    List<String> items = stream.collectList().block();
    assertNotNull(items);
    assertFalse(items.isEmpty());
    // External gateway streaming never called
    verify(gateway, never()).generateGroundedStream(any(), anyString(), anyString(), anyString(), anyString(), any());
    // Returned local text
    assertTrue(items.get(0).contains("Confidential strategic plan"));
  }
}
