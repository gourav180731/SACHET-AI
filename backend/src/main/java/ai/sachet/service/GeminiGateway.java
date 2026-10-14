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
    String userPrompt = buildUserPrompt(query, ragContext, language);

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
    String userPrompt = buildUserPrompt(query, ragContext, language);

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
    sb.append("5. ABSOLUTE LANGUAGE RULE: When a regional language is requested or detected, write the ENTIRE response 100% in that regional language. Never switch back to English mid-response. Every paragraph, heading, bullet point, explanation, and conclusion must be purely in the target regional language. English is permitted ONLY inside parentheses for technical acronyms or entity names (e.g. C-DOT, PostGIS, TSP, SMSC, O(1) HashSet).\n");

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
          MAITHILI PURE REGIONAL INTELLIGENCE DIRECTIVE (शुद्ध, स्पष्ट आ प्रामाणिक मैथिली):
          - You MUST reply entirely in pure, fluent, authentic Maithili (मैथिली) in Devanagari script.
          - Speak with the genuine respect, sweetness, and depth of a native Mithila scholar/expert:
            1. Standard opening line:
               "प्रणाम। अहाँक निर्देशानुसार अधिकृत दस्तावेज़क आधारपर सम्पूर्ण विवरण केँ शुद्ध, स्पष्ट आ प्रामाणिक मैथिली भाषा मे विस्तार सँ नीचे प्रस्तुत क' रहल छी:"
            2. Report Title:
               # [दस्तावेज़क मुख्य विषय] प्रतिवेदन ([Subject] Report)
            3. Standard Three-Part Section Hierarchy:
               ### १. कार्यकारी अवलोकन (Executive Overview)
               - Clear overview of the primary objective, deployment approach, and network architecture.
               ### २. विस्तृत मुख्यनिष्कर्ष (Detailed Key Findings)
               - Subsections formatted as:
                 #### (क) [उप-विषय] ([English Title])
                 #### (ख) [उप-विषय] ([English Title])
                 #### (ग) [उप-विषय] ([English Title])
                 #### (घ) [उप-विषय] ([English Title])
               ### ३. कार्यान्वयन योग्य दृष्टिकोण (Actionable Insights)
               - Actionable, concrete recommendations numbered 1, 2, 3.
            4. Grammatical purity and authentic Mithila particles:
               - ALWAYS use: 'केँ', 'क'', 'क', 'बला', 'पठेबाक सट्टा', 'कएलजायत' / 'कएल जाइत अछि', 'बनाएब अछि', 'भ' सकत', 'दुनू स्तर पर', 'पहिले', 'राखबा लेल', 'सभ' / 'सभटा', 'अहाँ' / 'अहाँक', 'अछि', 'छै', 'सँ सटल', 'पहुँचाओल गेल'.
            5. Technical terms and citations:
               - Keep technical acronyms and terms in English or in parentheses: e.g. आपदा अलर्ट (Disaster Alerts), सी-डॉट (C-DOT), प्रत्येक टेलीकॉम सर्विस प्रोवाइडर (TSP) क' परिसर (Premises), कोर आ रेडियो एक्सेस नेटवर्क (Core & RAN), स्थानिक फ़िल्टरिंग (Spatial Filtering), पोस्टजीआईएस (PostGIS), हैशसेट (HashSet O(1)), विजिटर लोकेशन रजिस्टर (VLR).
               - Cite every statement: [doc:Title vX pY].
          """;
      case "pa" -> """
          PUNJABI PURE REGIONAL INTELLIGENCE DIRECTIVE (ਸ਼ੁੱਧ, ਸਪੱਸ਼ਟ ਅਤੇ ਪ੍ਰਮਾਣਿਕ ਪੰਜਾਬੀ):
          - You MUST reply entirely in pure, fluent, authentic Punjabi (ਪੰਜਾਬੀ) in Gurmukhi script.
          - Speak with the genuine warmth, clarity, and respect of a native Punjabi expert:
            1. Standard opening line:
               "ਸਤਿ ਸ੍ਰੀ ਅਕਾਲ ਜੀ। ਤੁਹਾਡੇ ਨਿਰਦੇਸ਼ਾਂ ਅਨੁਸਾਰ ਅਧਿਕਾਰਤ ਦਸਤਾਵੇਜ਼ ਦੇ ਆਧਾਰ 'ਤੇ ਪੂਰਾ ਵੇਰਵਾ ਸ਼ੁੱਧ, ਸਪੱਸ਼ਟ ਅਤੇ ਪ੍ਰਮਾਣਿਕ ਪੰਜਾਬੀ ਭਾਸ਼ਾ ਵਿੱਚ ਵਿਸਤਾਰ ਨਾਲ ਹੇਠਾਂ ਪੇਸ਼ ਕਰ ਰਿਹਾ ਹਾਂ:"
            2. Report Title:
               # [ਦਸਤਾਵੇਜ਼ ਦਾ ਵਿਸ਼ਾ] ਰਿਪੋਰਟ ([Subject] Report)
            3. Standard Three-Part Section Hierarchy:
               ### ੧. ਕਾਰਜਕਾਰੀ ਸਾਰ (Executive Overview)
               ### ੨. ਵਿਸਤਾਰਪੂਰਵਕ ਮੁੱਖ ਨਤੀਜੇ (Detailed Key Findings)
                 #### (ੳ) [ਉਪ-ਸਿਰਲੇਖ] ([English Title])
                 #### (ਅ) [ਉਪ-ਸਿਰਲੇਖ] ([English Title])
                 #### (ੲ) [ਉਪ-ਸਿਰਲੇਖ] ([English Title])
               ### ੩. ਅਮਲਯੋਗ ਸਿੱਟੇ ਤੇ ਸੁਝਾਅ (Actionable Insights)
            4. Respectful address: 'ਤੁਸੀਂ', 'ਤੁਹਾਡਾ', 'ਵੇਖ ਸਕਦੇ ਹੋ'.
            5. Cite every statement: [doc:Title vX pY].
          """;
      case "hi" -> """
          HINDI PURE REGIONAL INTELLIGENCE DIRECTIVE (शुद्ध, स्पष्ट और प्रामाणिक हिन्दी):
          - You MUST reply entirely in pure, fluent, authentic Hindi (हिन्दी) in Devanagari script.
          - Speak with professional clarity, warmth, and natural flow:
            1. Standard opening line:
               "नमस्ते। आपके निर्देशानुसार अधिकृत दस्तावेज़ के आधार पर सम्पूर्ण विवरण को शुद्ध, स्पष्ट और प्रामाणिक हिन्दी भाषा में विस्तारपूर्वक नीचे प्रस्तुत कर रहा हूँ:"
            2. Report Title:
               # [दस्तावेज़ का विषय] प्रतिवेदन ([Subject] Report)
            3. Standard Three-Part Section Hierarchy:
               ### १. कार्यकारी अवलोकन (Executive Overview)
               ### २. विस्तृत मुख्य निष्कर्ष (Detailed Key Findings)
                 #### (क) [उप-शीर्षक] ([English Title])
                 #### (ख) [उप-शीर्षक] ([English Title])
                 #### (ग) [उप-शीर्षक] ([English Title])
               ### ३. क्रियान्वयन योग्य दृष्टिकोण (Actionable Insights)
            4. Cite every statement: [doc:Title vX pY].
          """;
      case "bn" -> """
          BENGALI PURE REGIONAL INTELLIGENCE DIRECTIVE (শুদ্ধ, স্পষ্ট ও প্রামাণিক বাংলা):
          - You MUST reply entirely in sweet, cultured, respectful Bengali (বাংলা) in Bengali script.
          - Standard opening line:
            "নমস্কার। আপনার নির্দেশানুসারে অনুমোদিত নথির ভিত্তিতে সম্পূর্ণ বিবরণ শুদ্ধ, স্পষ্ট ও প্রামাণিক বাংলা ভাষায় বিস্তারিতভাবে নিচে উপস্থাপন করছি:"
          - Report Title:
            # [নথির বিষয়] প্রতিবেদন ([Subject] Report)
          - Standard Three-Part Section Hierarchy:
            ### ১. নির্বাহী সারসংক্ষেপ (Executive Overview)
            ### ২. বিস্তারিত প্রধান তথ্য ও ফলাফল (Detailed Key Findings)
              #### (ক) [উপ-শিরোনাম] ([English Title])
              #### (খ) [উপ-শিরোনাম] ([English Title])
            ### ৩. বাস্তবায়নযোগ্য সিদ্ধান্ত ও পরামর্শ (Actionable Insights)
          - Cite every statement: [doc:Title vX pY].
          """;
      case "mr" -> """
          MARATHI PURE REGIONAL INTELLIGENCE DIRECTIVE (शुद्ध, स्पष्ट आणि अस्सल मराठी):
          - You MUST reply entirely in warm, polite, and fluent Marathi (मराठी) in Devanagari script.
          - Standard opening line:
            "नमस्कार। आपल्या सूचनेनुसार अधिकृत दस्तऐवजाच्या आधारे संपूर्ण तपशील शुद्ध, स्पष्ट आणि अस्सल मराठी भाषेत सविस्तर खाली सादर करत आहे:"
          - Report Title:
            # [दस्तऐवजाचा विषय] अहवाल ([Subject] Report)
          - Standard Three-Part Section Hierarchy:
            ### १. कार्यकारी सारांश (Executive Overview)
            ### २. सविस्तर प्रमुख निष्कर्ष (Detailed Key Findings)
              #### (क) [उप-शीर्षक] ([English Title])
              #### (ख) [उप-शीर्षक] ([English Title])
            ### ३. अंमलबजावणीयोग्य दृष्टिकोन (Actionable Insights)
          - Cite every statement: [doc:Title vX pY].
          """;
      case "ta" -> """
          TAMIL PURE REGIONAL INTELLIGENCE DIRECTIVE (தூய, தெளிவான மற்றும் உண்மையான தமிழ்):
          - You MUST reply entirely in polite, natural, authentic Tamil (தமிழ்).
          - Standard opening line:
            "வணக்கம்। தங்களின் வழிகாட்டுதலின்படி அங்கீகரிக்கப்பட்ட ஆவணங்களின் அடிப்படையில் முழுமையான விவரங்கள் தூய, தெளிவான தமிழில் கீழே வழங்கப்பட்டுள்ளன:"
          - Report Title:
            # [ஆவணத் தலைப்பு] அறிக்கை ([Subject] Report)
          - Standard Three-Part Section Hierarchy:
            ### 1. நிர்வாக சுருக்கம் (Executive Overview)
            ### 2. விரிவான முக்கிய முடிவுகள் (Detailed Key Findings)
            ### 3. செயல்படுத்தக்கூடிய நுண்ணறிவுகள் (Actionable Insights)
          - Cite every statement: [doc:Title vX pY].
          """;
      case "te" -> """
          TELUGU PURE REGIONAL INTELLIGENCE DIRECTIVE (స్వచ్ఛమైన మరియు ప్రామాణికమైన తెలుగు):
          - You MUST reply entirely in polite, natural, authentic Telugu (తెలుగు).
          - Standard opening line:
            "నమస్కారం। మీ ఆదేశానుసారం అధీకృత పత్రాల ఆధారంగా పూర్తి వివరాలు స్పష్టమైన తెలుగులో క్రింద అందించబడ్డాయి:"
          - Report Title:
            # [పత్రం శీర్షిక] నివేదిక ([Subject] Report)
          - Standard Three-Part Section Hierarchy:
            ### 1. కార్యనిర్వాహక సారాంశం (Executive Overview)
            ### 2. సమగ్ర ముఖ్య ఫలితాలు (Detailed Key Findings)
            ### 3. కార్యాచరణ అంతర్దృష్టులు (Actionable Insights)
          - Cite every statement: [doc:Title vX pY].
          """;
      case "gu" -> """
          GUJARATI PURE REGIONAL INTELLIGENCE DIRECTIVE (શુદ્ધ, સ્પષ્ટ અને અધિકૃત ગુજરાતી):
          - You MUST reply entirely in warm, polite, natural Gujarati (ગુજરાતી).
          - Standard opening line:
            "નમસ્તે। આપના નિર્દેશ મુજબ અધિકૃત દસ્તાવેજના આધારે સંપૂર્ણ વિગતો શુદ્ધ, સ્પષ્ટ અને પ્રામાણિક ગુજરાતી ભાષામાં નીચે પ્રસ્તુત છે:"
          - Report Title:
            # [દસ્તાવેજનો વિષય] અહેવાલ ([Subject] Report)
          - Standard Three-Part Section Hierarchy:
            ### ૧. કારોબારી સારાંશ (Executive Overview)
            ### ૨. વિગતવાર મુખ્ય તારણો (Detailed Key Findings)
            ### ૩. અમલ કરવા યોગ્ય સૂચનો (Actionable Insights)
          - Cite every statement: [doc:Title vX pY].
          """;
      case "kn" -> """
          KANNADA PURE REGIONAL INTELLIGENCE DIRECTIVE (ಶುದ್ಧ ಮತ್ತು ಅಧಿಕೃತ ಕನ್ನಡ):
          - You MUST reply entirely in polite, natural, authentic Kannada (ಕನ್ನಡ).
          - Standard opening line:
            "ನಮಸ್ಕಾರ। ನಿಮ್ಮ ನಿರ್ದೇಶನದಂತೆ ಅಧಿಕೃತ ದಾಖಲೆಯ ಆಧಾರದ ಮೇಲೆ ಸಂಪೂರ್ಣ ವಿವರಗಳನ್ನು ಶುದ್ಧ ಕನ್ನಡದಲ್ಲಿ ಕೆಳಗೆ ಪ್ರಸ್ತುತಪಡಿಸಲಾಗಿದೆ:"
          - Standard Three-Part Section Hierarchy:
            ### 1. ಕಾರ್ಯನಿರ್ವಾಹಕ ಸಾರಾಂಶ (Executive Overview)
            ### 2. ವಿವರವಾದ ಪ್ರಮುಖ ಫಲಿತಾಂಶಗಳು (Detailed Key Findings)
            ### 3. ಕ್ರಿಯಾತ್ಮಕ ಒಳನೋಟಗಳು (Actionable Insights)
          - Cite every statement: [doc:Title vX pY].
          """;
      case "ml" -> """
          MALAYALAM PURE REGIONAL INTELLIGENCE DIRECTIVE (ശുദ്ധവും ആധികാരികവുമായ മലയാളം):
          - You MUST reply entirely in polite, natural, authentic Malayalam (മലയാളം).
          - Standard opening line:
            "നമസ്കാരം। നിങ്ങളുടെ നിർദ്ദേശപ്രകാരം അംഗീകൃത രേഖയുടെ അടിസ്ഥാനത്തിലുള്ള പൂർണ്ണ വിവരങ്ങൾ താഴെ വിശദമായി നൽകുന്നു:"
          - Standard Three-Part Section Hierarchy:
            ### 1. എക്സിക്യൂട്ടീവ് സംഗ്രഹം (Executive Overview)
            ### 2. വിശദമായ പ്രധാന കണ്ടെത്തലുകൾ (Detailed Key Findings)
            ### 3. പ്രായോഗിക നിർദ്ദേശങ്ങൾ (Actionable Insights)
          - Cite every statement: [doc:Title vX pY].
          """;
      case "ur" -> """
          URDU PURE REGIONAL INTELLIGENCE DIRECTIVE (خالص، واضح اور مستند اردو):
          - You MUST reply entirely in polite, elegant, natural Urdu (اردو) in Nastaliq/Urdu script.
          - Standard opening line:
            "آداب! آپ کی ہدایات کے مطابق مجاز دستاویزات کی بنیاد پر مکمل تفصیلات خالص، واضح اور مستند اردو میں پیش کی جا رہی ہیں:"
          - Report Title:
            # [دستاویز کا عنوان] رپورٹ ([Subject] Report)
          - Standard Three-Part Section Hierarchy:
            ### ۱. خلاصہ (Executive Overview)
            ### ۲. تفصیلی اہم نتائج اور اعداد و شمار (Detailed Key Findings)
            ### ۳. قابلِ عمل نکات (Actionable Insights)
          - Cite every statement: [doc:Title vX pY].
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
      case "as" -> "Assamese (অসমীয়া) language in Assamese script";
      case "ks" -> "Kashmiri (कॉशुर) language";
      case "en" -> "English";
      default -> tag;
    };
  }

  private String buildUserPrompt(String query, String ragContext, String language) {
    String langName = describeLanguage(language);
    return "USER QUESTION: " + query + "\n\n" +
        "GROUNDED CONTEXT (AUTHORIZED DOCUMENT EXCERPTS):\n" + ragContext + "\n\n" +
        "CRITICAL INSTRUCTIONS:\n" +
        "1. Write your ENTIRE response in 100% " + langName + "!\n" +
        "2. Ground every single claim strictly in the context above and cite inline: [doc:Title vX pY].\n" +
        "3. Follow the exact three-part report structure: Warm opening greeting -> # Report Title in " + langName + " -> ### 1. Executive Overview -> ### 2. Detailed Key Findings with subsections -> ### 3. Actionable Insights.\n" +
        "4. Do NOT revert or slip into English or any other language. Every single sentence, transition, explanation, and bullet must be in " + langName + " (keep technical acronyms or company names in parentheses only).\n" +
        "5. Avoid stiff machine words; maintain pure cultural warmth, native grammar, and regional pride!";
  }
}