package ai.sachet.web;

import jakarta.validation.constraints.Size;
import java.util.List;

/** Chat request: user query plus optional document scope and LLM options. */
public record ChatRequest(
    @Size(min = 1, max = 2000) String query,
    List<String> docIds,
    Integer topK,
    String responseMode,   // detailed, short, bullets, custom
    String language,       // BCP-47 tag e.g., en-IN, hi-IN
    String customPrompt    // for custom mode
) {
}