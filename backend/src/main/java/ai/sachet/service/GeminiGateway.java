package ai.sachet.service;

import ai.sachet.config.SachetProperties;
import ai.sachet.security.SachetPrincipal;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import reactor.core.publisher.Flux;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Gemini implementation of ModelGateway.
 * Uses Google Generative AI Java SDK (com.google.genai:google-genai).
 * Security: only called with pre-filtered RAG context; never raw document access.
 */
@Service
public class GeminiGateway implements ModelGateway {

  private static final Logger log = LoggerFactory.getLogger(GeminiGateway.class);

  private final SachetProperties props;
  private final Client geminiClient;

  public GeminiGateway(SachetProperties props) {
    this.props = props;
    String apiKey = props.getGeminiApiKey();
    if (apiKey != null && !apiKey.isBlank()) {
      this.geminiClient = Client.builder().apiKey(apiKey).build();
    } else {
      this.geminiClient = null;
    }
  }

  @Override
  public Flux<String> generateGroundedStream(SachetPrincipal principal,
      String query,
      String ragContext,
      String language,
      String responseMode,
      String customPrompt) {

    if (!isAvailable()) {
      return Flux.error(new IllegalStateException(
          "Gemini API key not configured. Set SACHET_GEMINI_API_KEY to enable AI generation."));
    }

    String systemPrompt = buildSystemPrompt(language, responseMode, customPrompt);
    String userPrompt = buildUserPrompt(query, ragContext);

    log.info("Generating grounded response for user {} in {} mode {}",
        principal.getUserId(), language, responseMode);

    try {
      GenerateContentConfig config = GenerateContentConfig.builder()
          .temperature((float) props.getGeminiTemperature())
          .maxOutputTokens(props.getGeminiMaxTokens())
          .systemInstruction(com.google.genai.types.Content.fromParts(
              com.google.genai.types.Part.fromText(systemPrompt)))
          .build();

      List<String> candidates = getCandidateModels();
      Exception lastException = null;

      for (String targetModel : candidates) {
        try {
          var stream = geminiClient.models.generateContentStream(
              targetModel,
              com.google.genai.types.Content.fromParts(
                  com.google.genai.types.Part.fromText(userPrompt)),
              config);

          return Flux.fromIterable(stream)
              .map(chunk -> chunk.text())
              .filter(text -> text != null && !text.isBlank());
        } catch (Exception e) {
          log.warn("Gemini stream with model {} failed ({}). Trying next candidate.",
              targetModel, e.getMessage());
          lastException = e;
        }
      }

      throw lastException != null ? lastException : new RuntimeException("All Gemini models failed.");

    } catch (Exception e) {
      log.error("Gemini generation failed across all candidates", e);
      return Flux.error(new RuntimeException("AI generation failed: " + e.getMessage(), e));
    }
  }

  private List<String> getCandidateModels() {
    List<String> list = new java.util.ArrayList<>();
    list.add("gemini-flash-lite-latest");
    list.add("gemini-3.1-flash-lite");
    list.add("gemini-3.5-flash-lite");
    String configured = props.getGeminiModel();
    if (configured != null && !configured.isBlank() && !list.contains(configured)) {
      list.add(configured);
    }
    if (!list.contains("gemini-3.5-flash")) list.add("gemini-3.5-flash");
    return list;
  }

  @Override
  public String generateGrounded(SachetPrincipal principal,
      String query,
      String ragContext,
      String language,
      String responseMode,
      String customPrompt) {

    if (!isAvailable()) {
      throw new IllegalStateException("Gemini API key not configured");
    }

    String systemPrompt = buildSystemPrompt(language, responseMode, customPrompt);
    String userPrompt = buildUserPrompt(query, ragContext);

    GenerateContentConfig config = GenerateContentConfig.builder()
        .temperature((float) props.getGeminiTemperature())
        .maxOutputTokens(props.getGeminiMaxTokens())
        .systemInstruction(com.google.genai.types.Content.fromParts(
            com.google.genai.types.Part.fromText(systemPrompt)))
        .build();

    List<String> candidates = getCandidateModels();
    Exception lastException = null;

    for (String targetModel : candidates) {
      try {
        var response = geminiClient.models.generateContent(
            targetModel,
            com.google.genai.types.Content.fromParts(
                com.google.genai.types.Part.fromText(userPrompt)),
            config);

        String txt = response.text();
        if (txt != null && !txt.isBlank()) {
          return txt;
        }
      } catch (Exception e) {
        log.warn("Gemini generate with model {} failed ({}). Trying next candidate.",
            targetModel, e.getMessage());
        lastException = e;
      }
    }

    log.error("Gemini generation failed across all candidates", lastException);
    throw new RuntimeException("AI generation failed: " + (lastException != null ? lastException.getMessage() : "Unknown"), lastException);
  }

  @Override
  public boolean isAvailable() {
    return geminiClient != null && props.getGeminiApiKey() != null && !props.getGeminiApiKey().isBlank();
  }

  @Override
  public List<String> getSupportedModels() {
    return getCandidateModels();
  }

  @Override
  public List<String> getSupportedLanguages() {
    // Indian regional languages + English
    return Arrays.asList(
        "en-IN", "mai-IN", "hi-IN", "pa-IN", "bn-IN", "te-IN", "mr-IN", "ta-IN",
        "gu-IN", "kn-IN", "ml-IN", "ur-IN", "or-IN", "as-IN", "ks-IN");
  }

  private String buildSystemPrompt(String language, String responseMode, String customPrompt) {
    StringBuilder sb = new StringBuilder();

    // Core identity and security
    sb.append("You are SACHET AI, a security-first document intelligence and voice assistant for authorized government and organizational users.\n");
    sb.append("CRITICAL SECURITY & CITATION RULES:\n");
    sb.append("1. Ground your answers strictly in the provided authorized document excerpts (RAG context).\n");
    sb.append("2. If the context has insufficient supporting evidence, state clearly: \"I could not find sufficient supporting information in the authorized documents.\"\n");
    sb.append("3. Always cite sources inline using [doc:Title vX pY] format after key factual statements.\n");
    sb.append("4. Never fabricate citations or claim facts not supported by the document excerpts.\n");

    // Language handling
    if (language != null && !language.isBlank() && !"auto".equalsIgnoreCase(language) && !"Auto detect".equalsIgnoreCase(language)) {
      sb.append("5. LANGUAGE DIRECTIVE: You MUST respond entirely in ").append(describeLanguage(language))
          .append(". Do NOT reply in English unless the question was in English.\n\n");
    } else {
      sb.append("5. CRITICAL LANGUAGE DIRECTIVE: Automatically detect the language and script of the user's question. "
          + "If the question is in Maithili (मैथिली, e.g. using 'की हाल चाल', 'अछि', 'दलोहान', 'हम', 'अहाँ', etc.), you MUST answer ENTIRELY in authentic Maithili language using Devanagari script. "
          + "If the question is in Hindi, answer in Hindi. If in Punjabi, answer in Punjabi. If in Bengali, answer in Bengali. If in English, answer in English. "
          + "NEVER fall back to English when the user queries in an Indian regional language.\n\n");
    }

    // Response mode instructions
    sb.append("RESPONSE FORMAT (").append(responseMode).append("):\n");
    switch (responseMode.toLowerCase()) {
      case "bullets":
        sb.append("- Provide clear, thorough bullet-point findings.\n");
        sb.append("- Each bullet: detailed point + inline source citation [doc:Title vX pY].\n");
        break;
      case "short":
        sb.append("- Provide a concise 2-4 sentence summary capturing the primary takeaways with inline source citations.\n");
        break;
      case "custom":
        if (customPrompt != null && !customPrompt.isBlank()) {
          sb.append("- Follow the user's custom instruction: ").append(customPrompt).append("\n");
        } else {
          sb.append("- Provide a thorough, well-structured answer with complete citations.\n");
        }
        break;
      case "detailed":
      default:
        sb.append("- Provide a comprehensive, in-depth, structured briefing.\n");
        sb.append("- Include an Executive Overview, Detailed Key Findings (with entity-wise or topic-wise breakdowns, statistics, and projections), and Actionable Insights.\n");
        sb.append("- Ensure thorough coverage of all retrieved pages with inline source citations [doc:Title vX pY].\n");
        break;
    }

    sb.append("\nCITATION FORMAT: [doc:Title v1 pX] after each specific finding.\n");

    if (customPrompt != null && !customPrompt.isBlank() && !responseMode.equals("custom")) {
      sb.append("\nADDITIONAL USER INSTRUCTION: ").append(customPrompt).append("\n");
    }

    return sb.toString();
  }

  private String describeLanguage(String tag) {
    if (tag == null || tag.isBlank()) return "English";
    String base = tag.split("-")[0].toLowerCase(java.util.Locale.ROOT);
    return switch (base) {
      case "mai" -> "Maithili (मैथिली) language in Devanagari script";
      case "hi" -> "Hindi (हिन्दी) language in Devanagari script";
      case "pa" -> "Punjabi (ਪੰਜਾਬੀ) language in Gurmukhi script";
      case "bn" -> "Bengali (বাংলা) language in Bengali script";
      case "ta" -> "Tamil (தமிழ்) language in Tamil script";
      case "te" -> "Telugu (తెలుగు) language in Telugu script";
      case "mr" -> "Marathi (मराठी) language in Devanagari script";
      case "gu" -> "Gujarati (ગુજરાતી) language in Gujarati script";
      case "kn" -> "Kannada (ಕನ್ನಡ) language in Kannada script";
      case "ml" -> "Malayalam (മലയാളം) language in Malayalam script";
      case "ur" -> "Urdu (اردو) language in Urdu script";
      case "or" -> "Odia (ଓଡ଼ିଆ) language in Odia script";
      case "as" -> "Assamese (অসমীয়া) language in Assamese script";
      case "ks" -> "Kashmiri (कॉशुर) language";
      case "en" -> "English";
      default -> tag;
    };
  }

  private String buildUserPrompt(String query, String ragContext) {
    return "USER QUESTION: " + query + "\n\n" +
        "GROUNDED CONTEXT (AUTHORIZED DOCUMENT EXCERPTS):\n" + ragContext + "\n\n" +
        "ANSWER BASED ONLY ON THE ABOVE CONTEXT (ensure output matches the user question language and cite sources [doc:Title vX pY]):";
  }
}