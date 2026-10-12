package ai.sachet.web;

import java.util.List;

/** Chat response: grounded answer with citations and evidence status. */
public record ChatResponse(
    String answer,
    String evidenceStatus,
    String message,
    List<ChatSource> sources
) {
  public ChatResponse(String answer, String evidenceStatus, String message) {
    this(answer, evidenceStatus, message, List.of());
  }
}