package ai.sachet.web;

import ai.sachet.service.ChatService;
import ai.sachet.security.SachetPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * Phase 5/7: Text Assistant chat endpoints.
 * 
 * Security: Every query is permission-filtered through RagService.search() BEFORE
 * any generation occurs. The model/LLM layer never has unrestricted document store
 * access (AGENTS.md §3). If evidence is insufficient, the exact refusal sentence
 * is returned — no hallucination.
 * 
 * Endpoints:
 * - POST /chat           -> grounded text response (default, no LLM)
 * - POST /chat/stream    -> streaming AI response with RAG grounding
 * - GET  /chat/modes     -> available response modes and languages
 */
@RestController
@RequestMapping("${sachet.api-prefix:/api/v1}/chat")
public class ChatController {

  private final ChatService chatService;

  public ChatController(ChatService chatService) {
    this.chatService = chatService;
  }

  public record ChatReq(
      @Size(min = 1, max = 2000) String query,
      List<String> docIds,
      Integer topK,
      String responseMode,   // detailed, short, bullets, custom
      String language,       // BCP-47 tag e.g., en-IN, hi-IN
      String customPrompt    // for custom mode
  ) {
  }

  /** Grounded text response (no LLM) - existing behavior. */
  @PostMapping
  public ResponseEntity<ChatResponse> chat(
      @AuthenticationPrincipal SachetPrincipal principal,
      @Valid @RequestBody ChatReq req) {
    ChatResponse resp = chatService.chat(principal, new ChatRequest(
        req.query(), req.docIds(), req.topK(), req.responseMode(), req.language(), req.customPrompt()));
    return ResponseEntity.ok(resp);
  }

  /** Streaming AI response with RAG grounding. */
  @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Flux<String> chatStream(
      @AuthenticationPrincipal SachetPrincipal principal,
      @Valid @RequestBody ChatReq req) {

    String mode = req.responseMode() != null ? req.responseMode() : "detailed";
    String lang = req.language();
    String customPrompt = req.customPrompt();

    return chatService.chatStream(principal, new ChatRequest(
        req.query(), req.docIds(), req.topK(), req.responseMode(), req.language(), req.customPrompt()), mode, lang, customPrompt);
  }

  /** Get available response modes and supported languages. */
  @GetMapping("/modes")
  public ResponseEntity<Map<String, Object>> getModes() {
    return ResponseEntity.ok(Map.of(
        "responseModes", List.of("detailed", "short", "bullets", "custom"),
        "defaultMode", "detailed",
        "supportedLanguages", List.of(
            "en-IN", "hi-IN", "bn-IN", "te-IN", "mr-IN", "ta-IN",
            "gu-IN", "kn-IN", "ml-IN", "pa-IN", "ur-IN", "or-IN", "as-IN", "ks-IN"),
        "defaultLanguage", "en-IN"
    ));
  }
}