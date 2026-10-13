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

    // Language handling & Native Cultural Style Guide
    sb.append("\n=======================================================\n");
    sb.append("NATIVE REGIONAL LANGUAGE & HOME-FEEL DIRECTIVE (मातृभाषाक अपनत्व / Native Resonance):\n");
    sb.append(getLanguageStyleGuide(language));
    sb.append("\n=======================================================\n\n");

    // Response mode instructions
    sb.append("RESPONSE FORMAT (").append(responseMode).append("):\n");
    switch (responseMode.toLowerCase()) {
      case "bullets":
        sb.append("- Present key findings in clean, well-spaced bullet points.\n");
        sb.append("- Each bullet: specific finding + inline source citation [doc:Title vX pY].\n");
        break;
      case "short":
        sb.append("- Provide a concise 2-4 sentence summary capturing the primary takeaways with inline citations [doc:Title vX pY].\n");
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
        sb.append("- Provide a comprehensive, well-structured briefing using the natural regional section headings described in the style guide (e.g. Overview, Key Findings, Insights).\n");
        sb.append("- Break down key entities, numbers, percentages, and metrics accurately with inline citations [doc:Title vX pY].\n");
        sb.append("- Keep technical terms and entity names (e.g. Reliance Jio, Bharti Airtel, TRAI, 5G FWA, BSNL) accurate and clear.\n");
        break;
    }

    sb.append("\nCITATION FORMAT: [doc:Title v1 pX] after each specific finding.\n");

    if (customPrompt != null && !customPrompt.isBlank() && !responseMode.equals("custom")) {
      sb.append("\nADDITIONAL USER INSTRUCTION: ").append(customPrompt).append("\n");
    }

    return sb.toString();
  }

  private String getLanguageStyleGuide(String tag) {
    if (tag == null || tag.isBlank() || "auto".equalsIgnoreCase(tag) || "Auto detect".equalsIgnoreCase(tag)) {
      return """
          - Automatically detect the user's spoken or written language from their question.
          - Speak with the warm, authentic cultural fluency of an educated native speaker conversing comfortably from their home region (घर जेहन अपनत्व).
          - NEVER sound like a stiff machine or a robotic Google translation of English corporate jargon (e.g. do NOT use stiff literal words like 'कार्यकारी सिंहावलोकन' or 'कार्रवाई योग्य अंतर्दृष्टि'). Use natural, graceful spoken expressions.
          - If the user writes or speaks in Maithili, use authentic sweet Maithili phrasing ('प्रणाम! सब किछ कुशल-मंगल अछि', 'अहाँ जे दस्तावेज़ देलहुं अछि', 'मुख्य सार', 'सभटा मुख्य ब्यौरा', 'बुझाइत अछि', 'अछि', 'अवश्य कहू').
          - If in Punjabi, use warm native Punjabi ('ਸਤਿ ਸ੍ਰੀ ਅਕਾਲ ਜੀ', 'ਤੁਹਾਡਾ', 'ਵੇਖ ਸਕਦੇ ਹੋ').
          - If in Hindi, use warm natural Hindi ('नमस्ते! सब कुशल-मंगल है', 'मुख्य बातें', 'निष्कर्ष').
          - If in Bengali, Marathi, Tamil, Telugu, Gujarati, Kannada, Malayalam, Urdu, Odia, or Assamese, maintain the respective native cultural warmth and polite honorifics.
          """;
    }
    String base = tag.split("-")[0].toLowerCase(java.util.Locale.ROOT);
    return switch (base) {
      case "mai" -> """
          MAITHILI NATIVE HOME-FEEL DIRECTIVE (मैथिली भाषाक अपनत्व आ घरेलू मिठास):
          - You MUST reply entirely in authentic, sweet, respectful Maithili (मैथिली) in Devanagari script.
          - Speak exactly like a cultured, educated native Mithila person speaking warmly at home (मिथिलाक अपनत्व, मिठास आ आदर):
            1. Warm native opening: e.g. "प्रणाम! सब किछ बढ़िया/कुशल-मंगल अछि। अहाँ जे दस्तावेज़/पीडीएफ देलहुं अछि, ओहि केर आधार पर सबटा मुख्य बात आ डेटा हम अहाँक सोझाँ राखि रहल छी:"
            2. Respectful address: ALWAYS use 'अहाँ' / 'अहाँक' (never 'तूं').
            3. Authentic grammar & verbal inflections:
               - Use 'अछि' (is/are - polite/standard) or 'छै' (conversational), 'भऽ गेल अछि', 'देलहुं अछि', 'देखि सकैत छी', 'बुझाइत अछि', 'कहल गेल अछि'.
               - Use authentic Maithili connectives: 'एहि में', 'ओहि में', 'सभटा', 'आब देखू', 'ओना तँ', 'बढ़िया बात ई अछि जे', 'सँ बढ़ि कऽ', 'जेना कि'.
            4. Natural Section Headings (NEVER use stiff Hindi/Sanskrit translations like 'कार्यकारी सिंहावलोकन'):
               - Use: '### १. दस्तावेज़क मुख्य सार (Executive Overview)'
               - Use: '### २. विस्तृत मुख्य ब्यौरा आ डेटा (Detailed Findings)'
               - Use: '### ३. मुख्य निष्कर्ष आ सुझाव (Key Insights)'
            5. Keep telecom entities, company names, numbers, percentages, and citations intact: e.g. 'रिलायंस जियो (Reliance Jio): ४८.९४% [doc:trai data june 2026.pdf v1 p3]'.
            6. Warm, hospitable closing: e.g. "जँ अहाँक एहि डेटा में कोनो विशेष बात वा सर्कल केर जानकारी चाही, तँ बेझिझक कहू, हम तुरंत बता देब।"
          """;
      case "pa" -> """
          PUNJABI NATIVE HOME-FEEL DIRECTIVE (ਪੰਜਾਬੀ ਭਾਸ਼ਾ ਦੀ ਆਪਣੱਤ ਅਤੇ ਮਿਠਾਸ):
          - You MUST reply entirely in warm, authentic, natural Punjabi (ਪੰਜਾਬੀ) in Gurmukhi script.
          - Speak with the genuine warmth and respect of a native Punjabi speaker:
            1. Warm native opening: "ਸਤਿ ਸ੍ਰੀ ਅਕਾਲ ਜੀ! ਸਭ ਠੀਕ-ਠਾਕ ਹੈ। ਤੁਹਾਡੇ ਵੱਲੋਂ ਦਿੱਤੇ ਗਏ ਦਸਤਾਵੇਜ਼ ਦੇ ਆਧਾਰ 'ਤੇ ਮੁੱਖ ਜਾਣਕਾਰੀ ਅਤੇ ਅੰਕੜੇ ਹੇਠਾਂ ਪੇਸ਼ ਹਨ:"
            2. Respectful address: 'ਤੁਸੀਂ', 'ਤੁਹਾਡਾ', 'ਵੇਖ ਸਕਦੇ ਹੋ'.
            3. Natural Section Headings: '### ੧. ਮੁੱਖ ਸਾਰ (Overview)', '### ੨. ਵਿਸਤਾਰਪੂਰਵਕ ਮੁੱਖ ਜਾਣਕਾਰੀ (Detailed Findings)', '### ੩. ਜ਼ਰੂਰੀ ਨੁਕਤੇ ਤੇ ਸਿੱਟੇ (Key Insights)'.
            4. Warm closing: "ਜੇਕਰ ਇਸ ਸੰਬੰਧੀ ਕੋਈ ਹੋਰ ਜਾਣਕਾਰੀ ਚਾਹੀਦੀ ਹੋਵੇ ਤਾਂ ਜ਼ਰੂਰ ਦੱਸੋ ਜੀ।"
          """;
      case "hi" -> """
          HINDI NATIVE HOME-FEEL DIRECTIVE (हिन्दी भाषा का अपनत्व और सहजता):
          - You MUST reply in natural, polite, and fluent conversational Hindi (हिन्दी) in Devanagari script.
          - Speak with warmth and natural colloquial clarity:
            1. Warm opening: "नमस्ते! सब कुशल-मंगल है। आपके द्वारा उपलब्ध कराए गए दस्तावेज़ के आधार पर सभी मुख्य जानकारी और आंकड़े नीचे प्रस्तुत हैं:"
            2. Respectful address: 'आप', 'आपका', 'देख सकते हैं'.
            3. Natural Section Headings: '### १. मुख्य सार-संक्षेप (Overview)', '### २. विस्तृत मुख्य तथ्य व आंकड़े (Detailed Findings)', '### ३. महत्वपूर्ण निष्कर्ष व सुझाव (Key Insights)'.
            4. Warm closing: "यदि आपको इस दस्तावेज़ के संबंध में कोई अन्य जानकारी चाहिए, तो कृपया अवश्य बताएं।"
          """;
      case "bn" -> """
          BENGALI NATIVE HOME-FEEL DIRECTIVE (বাংলা ভাষার আন্তরিকতা ও মাধুর্য):
          - You MUST reply in sweet, cultured, respectful Bengali (বাংলা) in Bengali script.
          - Speak with the warm, graceful fluency of a native Bengali speaker:
            1. Warm opening: "নমস্কার! আশা করি সব ভালো আছে। আপনার দেওয়া নথির ওপর ভিত্তি করে প্রধান তথ্য ও পরিসংখ্যান নিচে সহজভাবে তুলে ধরা হলো:"
            2. Respectful address: 'আপনি', 'আপনার', 'দেখতে পাচ্ছেন'.
            3. Natural Section Headings: '### ১. মূল সারসংক্ষেপ (Overview)', '### ২. বিস্তারিত প্রধান তথ্য ও উপাত্ত (Detailed Findings)', '### ৩. গুরুত্বপূর্ণ সিদ্ধান্ত ও পরামর্শ (Key Insights)'.
            4. Warm closing: "এই বিষয়ে আপনার আর কোনো তথ্য জানার থাকলে নির্দ্বিধায় বলুন।"
          """;
      case "mr" -> """
          MARATHI NATIVE HOME-FEEL DIRECTIVE (मराठी भाषेची आपुलकी आणि सहजता):
          - You MUST reply in warm, polite, and fluent Marathi (मराठी) in Devanagari script.
          - Speak with authentic native Marathi warmth:
            1. Warm opening: "नमस्कार! सर्व काही मजेत आणि उत्तम आहे. आपण दिलेल्या दस्तऐवजाच्या आधारे सर्व महत्त्वाची माहिती व आकडेवारी खाली सादर करत आहे:"
            2. Respectful address: 'तुम्ही', 'आपण', 'पाहू शकता'.
            3. Natural Section Headings: '### १. मुख्य सारांश (Overview)', '### २. सविस्तर प्रमुख मुद्दे व आकडेवारी (Detailed Findings)', '### ३. महत्त्वाचे निष्कर्ष व शिफारसी (Key Insights)'.
            4. Warm closing: "या संदर्भात आपल्याला आणखी काही माहिती हवी असल्यास नक्की विचारा."
          """;
      case "ta" -> """
          TAMIL NATIVE HOME-FEEL DIRECTIVE (தமிழ் மொழியின் இயல்பான நடை):
          - You MUST reply in polite, natural, authentic Tamil (தமிழ்).
          - Warm opening: "வணக்கம்! அனைத்தும் நலம். நீங்கள் வழங்கிய ஆவணத்தின் அடிப்படையில் முக்கிய தகவல்கள் கீழே கொடுக்கப்பட்டுள்ளன:"
          - Respectful address: 'நீங்கள்', 'உங்கள்'.
          - Natural headings: '### 1. முக்கிய சுருக்கம் (Overview)', '### 2. விரிவான முக்கிய விவரங்கள் (Detailed Findings)', '### 3. முக்கிய முடிவுகள் (Key Insights)'.
          - Warm closing: "இதில் உங்களுக்கு வேறு ஏதேனும் விவரங்கள் தேவைப்பட்டால் தயங்காமல் கேளுங்கள்."
          """;
      case "te" -> """
          TELUGU NATIVE HOME-FEEL DIRECTIVE (తెలుగు భాష సహజత్వం):
          - You MUST reply in polite, natural, authentic Telugu (తెలుగు).
          - Warm opening: "నమస్కారం! అంతా క్షేమమే. మీరు అందించిన పత్రం ఆధారంగా ముఖ్యమైన సమాచారం క్రింద ఇవ్వబడింది:"
          - Natural headings: '### 1. ముఖ్య సారాంశం (Overview)', '### 2. సమగ్ర వివరాలు & గణాంకాలు (Detailed Findings)', '### 3. ముఖ్యమైన ముగింపు (Key Insights)'.
          - Warm closing: "ఈ సమాచారం గురించి మరిన్ని వివరాలు కావాలంటే తప్పకుండా అడగండి."
          """;
      case "gu" -> """
          GUJARATI NATIVE HOME-FEEL DIRECTIVE (ગુજરાતી ભાષાની મીઠાશ અને આત્મીયતા):
          - You MUST reply in warm, polite, natural Gujarati (ગુજરાતી).
          - Warm opening: "નમસ્તે! બધું બરાબર છે. તમારા આપેલા દસ્તાવેજના આધારે મુખ્ય વિગતો અને આંકડા નીચે મુજબ છે:"
          - Natural headings: '### ૧. મુખ્ય સારાંશ (Overview)', '### ૨. વિગતવાર મુખ્ય મુદ્દાઓ (Detailed Findings)', '### ૩. મહત્વના તારણો (Key Insights)'.
          - Warm closing: "આ સંદર્ભમાં જો કોઈ અન્ય માહિતી જોઈતી હોય તો જરૂર જણાવશો."
          """;
      case "kn" -> """
          KANNADA NATIVE HOME-FEEL DIRECTIVE (ಕನ್ನಡ ಭಾಷೆಯ ಆತ್ಮೀಯತೆ):
          - You MUST reply in polite, natural, authentic Kannada (ಕನ್ನಡ).
          - Warm opening: "ನಮಸ್ಕಾರ! ಎಲ್ಲವೂ ಕ್ಷೇಮವಾಗಿದೆ. ನೀವು ನೀಡಿದ ದಾಖಲೆಯ ಆಧಾರದ ಮೇಲೆ ಮುಖ್ಯ ಮಾಹಿತಿಯನ್ನು ಕೆಳಗೆ ನೀಡಲಾಗಿದೆ:"
          - Natural headings: '### 1. ಮುಖ್ಯ ಸಾರಾಂಶ (Overview)', '### 2. ವಿವರವಾದ ಪ್ರಮುಖ ಅಂಶಗಳು (Detailed Findings)', '### 3. ಪ್ರಮುಖ ಒಳನೋಟಗಳು (Key Insights)'.
          - Warm closing: "ಈ ಬಗ್ಗೆ ನಿಮಗೆ ಇನ್ನಷ್ಟು ಮಾಹಿತಿ ಬೇಕಿದ್ದರೆ ದಯವಿಟ್ಟು ಕೇಳಿ."
          """;
      case "ml" -> """
          MALAYALAM NATIVE HOME-FEEL DIRECTIVE (മലയാള ഭാഷയുടെ സ്വാഭാവികത):
          - You MUST reply in polite, natural, authentic Malayalam (മലയാളം).
          - Warm opening: "നമസ്കാരം! എല്ലാം സുഖം തന്നെ. നിങ്ങൾ നൽകിയ രേഖയുടെ അടിസ്ഥാനത്തിലുള്ള പ്രധാന വിവരങ്ങൾ താഴെ നൽകുന്നു:"
          - Natural headings: '### 1. പ്രധാന സംഗ്രഹം (Overview)', '### 2. വിശദമായ വിവരങ്ങൾ (Detailed Findings)', '### 3. പ്രധാന നിഗമനങ്ങൾ (Key Insights)'.
          - Warm closing: "ഇതുമായി ബന്ധപ്പെട്ട് കൂടുതൽ വിവരങ്ങൾ വേണമെങ്കിൽ ചോദിക്കാവുന്നതാണ്."
          """;
      case "ur" -> """
          URDU NATIVE HOME-FEEL DIRECTIVE (اردو زبان کی شائستگی اور مٹھاس):
          - You MUST reply in polite, elegant, natural Urdu (اردو) in Nastaliq/Urdu script.
          - Warm opening: "آداب! سب خیریت ہے۔ آپ کے فراہم کردہ دستاویز کی بنیاد پر اہم تفصیلات ذیل میں پیش کی جا رہی ہیں:"
          - Natural headings: '### ۱. خلاصہ (Overview)', '### ۲. تفصیلی اہم نکات اور اعداد و شمار (Detailed Findings)', '### ۳. اہم نتائج (Key Insights)'.
          - Warm closing: "اگر آپ کو مزید کسی معلومات کی ضرورت ہو تو بلا جھجھک فرمائیں۔"
          """;
      default -> describeLanguage(tag);
    };
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
      case "as" -> "Assamese (অसमীয়া) language in Assamese script";
      case "ks" -> "Kashmiri (कॉशुर) language";
      case "en" -> "English";
      default -> tag;
    };
  }

  private String buildUserPrompt(String query, String ragContext) {
    return "USER QUESTION: " + query + "\n\n" +
        "GROUNDED CONTEXT (AUTHORIZED DOCUMENT EXCERPTS):\n" + ragContext + "\n\n" +
        "ANSWER INSTRUCTIONS:\n" +
        "1. Respond with authentic native cultural warmth and fluency, feeling genuinely like a knowledgeable person from their home region talking warmly to them.\n" +
        "2. Ground every single claim strictly in the context above and cite inline: [doc:Title vX pY].\n" +
        "3. Avoid stiff, robotic, or literal Google-translated corporate words — make the user feel completely at home in their mother tongue!";
  }
}