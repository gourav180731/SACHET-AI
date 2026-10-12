package ai.sachet.service;

import ai.sachet.config.SachetProperties;
import ai.sachet.security.SachetPrincipal;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * Model Gateway abstraction (Phase 7).
 * Abstracts LLM providers behind a common interface.
 * Security: model must obey the same authorization model as text.
 * Must NOT bypass: authentication, document permissions, export restrictions, tool permissions.
 */
public interface ModelGateway {

  /**
   * Generate a grounded streaming response using RAG context.
   *
   * @param principal     authenticated user principal
   * @param query         user's question
   * @param ragContext    grounded context from RAG (document excerpts with citations)
   * @param language      response language (BCP-47 tag)
   * @param responseMode  response format: detailed, short, bullets, custom
   * @param customPrompt  optional custom system prompt for custom mode
   * @return stream of response tokens
   */
  Flux<String> generateGroundedStream(SachetPrincipal principal,
      String query,
      String ragContext,
      String language,
      String responseMode,
      String customPrompt);

  /**
   * Generate a non-streaming grounded response.
   */
  String generateGrounded(SachetPrincipal principal,
      String query,
      String ragContext,
      String language,
      String responseMode,
      String customPrompt);

  /**
   * Check if the gateway is available (API key configured, provider reachable).
   */
  boolean isAvailable();

  /**
   * Get supported models.
   */
  List<String> getSupportedModels();

  /**
   * Get supported languages for generation.
   */
  List<String> getSupportedLanguages();
}