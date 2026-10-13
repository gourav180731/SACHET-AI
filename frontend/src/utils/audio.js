/**
 * Minimal L16 PCM -> 16-bit WAV header converter so standard browsers can play
 * raw Gemini TTS audio payloads.
 */
export function pcmToWav(rawBuffer, sampleRate = 24000) {
  const bytes = rawBuffer instanceof Uint8Array ? rawBuffer : new Uint8Array(rawBuffer);
  const byteLen = bytes.byteLength;
  const bytesPerSample = 2;
  const buffer = new ArrayBuffer(44 + byteLen);
  const view = new DataView(buffer);
  const str = (off, s) => {
    for (let i = 0; i < s.length; i += 1) view.setUint8(off + i, s.charCodeAt(i));
  };

  str(0, "RIFF");
  view.setUint32(4, 36 + byteLen, true);
  str(8, "WAVE");
  str(12, "fmt ");
  view.setUint32(16, 16, true);
  view.setUint16(20, 1, true); // PCM
  view.setUint16(22, 1, true); // mono
  view.setUint32(24, sampleRate, true);
  view.setUint32(28, sampleRate * bytesPerSample, true);
  view.setUint16(32, bytesPerSample, true);
  view.setUint16(34, 16, true);
  str(36, "data");
  view.setUint32(40, byteLen, true);
  new Uint8Array(buffer, 44).set(bytes);
  return new Blob([buffer], { type: "audio/wav" });
}

/**
 * Strips citation tags, markdown formatting, and code blocks
 * so the TTS engine speaks clean, natural sentences.
 */
export function cleanTextForSpeech(text) {
  if (!text) return "";
  return text
    .replace(/\[doc:[^\]]+\]/gi, "")
    .replace(/\[SOURCE\s*\d+:[^\]]+\]/gi, "")
    .replace(/```[\s\S]*?```/g, "")
    .replace(/[*_#`~>]/g, "")
    .replace(/\s+/g, " ")
    .trim();
}

/**
 * Automatically inspects the character script to determine
 * whether the text is Maithili/Hindi, Punjabi, Bengali, Tamil, etc., or English.
 */
export function detectLanguageFromText(text) {
  if (!text) return "en-IN";

  // Check distinctive Indian scripts first
  for (const ch of text) {
    const code = ch.charCodeAt(0);
    if (code >= 0x0a00 && code <= 0x0a7f) return "pa-IN"; // Gurmukhi (Punjabi)
    if (code >= 0x0980 && code <= 0x09ff) return "bn-IN"; // Bengali
    if (code >= 0x0b80 && code <= 0x0bff) return "ta-IN"; // Tamil
    if (code >= 0x0c00 && code <= 0x0c7f) return "te-IN"; // Telugu
    if (code >= 0x0a80 && code <= 0x0aff) return "gu-IN"; // Gujarati
    if (code >= 0x0c80 && code <= 0x0cff) return "kn-IN"; // Kannada
    if (code >= 0x0d00 && code <= 0x0d7f) return "ml-IN"; // Malayalam
    if (code >= 0x0600 && code <= 0x06ff) return "ur-IN"; // Urdu
  }

  // Devanagari script: differentiate Maithili vs Marathi vs Hindi
  let hasDevanagari = false;
  for (const ch of text) {
    const code = ch.charCodeAt(0);
    if (code >= 0x0900 && code <= 0x097f) {
      hasDevanagari = true;
      break;
    }
  }

  if (hasDevanagari) {
    const lower = text.toLowerCase();
    // Distinctive Maithili dialect vocabulary and verbal inflections
    const maithiliMarkers = [
      "की हाल", "हाल चाल", "बढ़िया", "छै", "अछि", "दलोहान", "अहाँ", "हमर", "तोहर",
      "कहलहुं", "देलहुं", "ओकरा", "एहि", "ओहि", "सभ", "सभटा", "बुझाइत", "नीचाँ",
      "सोझाँ", "भेल", "भऽ गेल", "कऽ कऽ", "रहल छी", "रहल अछि", "की अछि", "की भेल",
      "कहू", "बताउ", "बुझाउ", "हमार", "देलियै", "कहलियै", "ब्यौरा", "सार"
    ];
    if (maithiliMarkers.some((m) => lower.includes(m))) {
      return "mai-IN";
    }

    // Distinctive Marathi markers
    const marathiMarkers = ["आहे", "नाही", "कसे", "झाले", "करा", "माझे", "तुमचे", "काय", "आहेत"];
    if (marathiMarkers.some((m) => lower.includes(m))) {
      return "mr-IN";
    }

    // Distinctive Hindi markers
    const hindiMarkers = ["रहा है", "रही है", "रहे हैं", "क्या", "कैसे", "बताइए", "आपका", "हमारे", "होता है"];
    if (hindiMarkers.some((m) => lower.includes(m))) {
      return "hi-IN";
    }

    // Default Devanagari: mai-IN for regional Mithila / Hindi
    return "mai-IN";
  }

  return "en-IN";
}

/**
 * Natural instant task acknowledgment phrases for low-latency conversational feedback.
 */
export const ACK_MESSAGES = {
  "en-IN": [
    "Checking the authorized documents...",
    "Looking into the records...",
    "One moment, searching documents...",
  ],
  "mai-IN": [
    "प्रणाम! एक सेकंड रुकू, अहाँक अधिकृत दस्तावेज़ देखि रहल छी...",
    "दस्तावेज़ में खोज कऽ रहल छी, तुरंत सबटा बात बताबैत छी...",
    "अहाँक देल दस्तावेज़ केर जाँच कऽ रहल छी...",
  ],
  "hi-IN": [
    "एक सेकंड, अधिकृत दस्तावेज़ देख रहा हूँ...",
    "रिकॉर्ड में जाँच कर रहा हूँ...",
  ],
  "pa-IN": [
    "ਇੱਕ ਮਿੰਟ, ਦਸਤਾਵੇਜ਼ ਚੈੱਕ ਕਰ ਰਿਹਾ ਹਾਂ...",
    "ਰਿਕਾਰਡ ਦੀ ਜਾਂਚ ਕਰ ਰਿਹਾ ਹਾਂ...",
  ],
  "bn-IN": [
    "এক সেকেন্ড, অনুমোদিত নথি দেখছি...",
    "নথিপত্রে তথ্য খুঁজছি...",
  ],
  "mr-IN": [
    "एक क्षण, अधिकृत कागदपत्रे तपासत आहे...",
  ],
  "ta-IN": [
    "ஒரு நிமிடம், ஆவணங்களை சரிபார்க்கிறேன்...",
  ],
  "te-IN": [
    "ఒక్క నిమిషం, పత్రాలను తనిఖీ చేస్తున్నాను...",
  ],
};

export function getInstantAck(lang = "en-IN") {
  const list = ACK_MESSAGES[lang] || ACK_MESSAGES["en-IN"];
  return list[Math.floor(Math.random() * list.length)];
}

/** Cached voices array across user sessions */
let cachedVoicesList = [];

/**
 * Robust async loader for speech synthesis voices.
 * Chromium browsers populate voices asynchronously via onvoiceschanged event.
 */
export function getVoicesAsync() {
  return new Promise((resolve) => {
    if (typeof window === "undefined" || !window.speechSynthesis) {
      resolve([]);
      return;
    }
    if (cachedVoicesList.length > 0) {
      resolve(cachedVoicesList);
      return;
    }
    const immediate = window.speechSynthesis.getVoices();
    if (immediate && immediate.length > 0) {
      cachedVoicesList = immediate;
      resolve(immediate);
      return;
    }
    let resolved = false;
    const onVoices = () => {
      if (resolved) return;
      resolved = true;
      const v = window.speechSynthesis.getVoices() || [];
      if (v.length > 0) cachedVoicesList = v;
      resolve(v);
    };
    window.speechSynthesis.onvoiceschanged = onVoices;
    setTimeout(() => {
      if (!resolved) {
        resolved = true;
        const v = window.speechSynthesis.getVoices() || [];
        if (v.length > 0) cachedVoicesList = v;
        resolve(v);
      }
    }, 350);
  });
}

// Pre-warm voices list on module load in browser
if (typeof window !== "undefined" && window.speechSynthesis) {
  try {
    const list = window.speechSynthesis.getVoices();
    if (list && list.length > 0) cachedVoicesList = list;
    window.speechSynthesis.onvoiceschanged = () => {
      const v = window.speechSynthesis.getVoices();
      if (v && v.length > 0) cachedVoicesList = v;
    };
  } catch { /* noop */ }
}

/**
 * Returns the exact phonetic BCP-47 tag that browser speech engines
 * require to pronounce text in authentic regional Indian phonetics.
 *
 * NOTE: OS/Browser speech synthesis engines (Windows SAPI, macOS, Android, Chromium)
 * do NOT have a voice tagged "mai-IN". Maithili is written in the Devanagari script.
 * Setting utterance.lang to "hi-IN" (Devanagari phonetic synthesizer) ensures
 * 100% authentic native Indian pronunciation of every Devanagari character,
 * whereas setting "mai-IN" causes browsers to fail back to English!
 */
export function getBrowserPhoneticLang(cleanText, langTag) {
  if (!cleanText) return "en-IN";

  // Check script Unicode ranges first:
  for (const ch of cleanText) {
    const code = ch.charCodeAt(0);
    // Devanagari script (Maithili, Hindi, Marathi, Bhojpuri, Nepali)
    if (code >= 0x0900 && code <= 0x097f) return "hi-IN";
    // Gurmukhi script (Punjabi)
    if (code >= 0x0a00 && code <= 0x0a7f) return "pa-IN";
    // Bengali/Assamese script
    if (code >= 0x0980 && code <= 0x09ff) return "bn-IN";
    // Tamil script
    if (code >= 0x0b80 && code <= 0x0bff) return "ta-IN";
    // Telugu script
    if (code >= 0x0c00 && code <= 0x0c7f) return "te-IN";
    // Gujarati script
    if (code >= 0x0a80 && code <= 0x0aff) return "gu-IN";
    // Kannada script
    if (code >= 0x0c80 && code <= 0x0cff) return "kn-IN";
    // Malayalam script
    if (code >= 0x0d00 && code <= 0x0d7f) return "ml-IN";
    // Urdu / Arabic script
    if (code >= 0x0600 && code <= 0x06ff) return "ur-IN";
  }

  if (langTag && langTag !== "auto" && langTag !== "Auto detect") {
    const base = langTag.split("-")[0].toLowerCase();
    if (base === "mai") return "hi-IN"; // Devanagari engine
    return langTag;
  }

  return "en-IN";
}

/**
 * Resolves the absolute best browser SpeechSynthesisVoice for the given text.
 * CRITICAL RULE: Non-Latin regional script (Devanagari, Punjabi, Bengali, etc.)
 * MUST NEVER be assigned an English voice (e.g. en-IN, en-US, Microsoft Ravi, David).
 */
export function resolveBrowserVoice(voices, cleanText, langTag) {
  if (!voices || voices.length === 0) return null;

  // Determine if text has non-Latin Indian script
  let isDevanagari = false;
  let isGurmukhi = false;
  let isBengali = false;
  let isTamil = false;
  let isTelugu = false;
  let isGujarati = false;
  let isKannada = false;
  let isMalayalam = false;
  let isUrdu = false;

  for (const ch of cleanText) {
    const code = ch.charCodeAt(0);
    if (code >= 0x0900 && code <= 0x097f) { isDevanagari = true; break; }
    if (code >= 0x0a00 && code <= 0x0a7f) { isGurmukhi = true; break; }
    if (code >= 0x0980 && code <= 0x09ff) { isBengali = true; break; }
    if (code >= 0x0b80 && code <= 0x0bff) { isTamil = true; break; }
    if (code >= 0x0c00 && code <= 0x0c7f) { isTelugu = true; break; }
    if (code >= 0x0a80 && code <= 0x0aff) { isGujarati = true; break; }
    if (code >= 0x0c80 && code <= 0x0cff) { isKannada = true; break; }
    if (code >= 0x0d00 && code <= 0x0d7f) { isMalayalam = true; break; }
    if (code >= 0x0600 && code <= 0x06ff) { isUrdu = true; break; }
  }

  // 1. DEVANAGARI SCRIPT (Maithili, Hindi, Marathi, Nepali)
  if (isDevanagari || (langTag && langTag.startsWith("mai")) || (langTag && langTag.startsWith("hi"))) {
    // STRICT FILTER: Exclude ALL English voices!
    const nonEnglishVoices = voices.filter(
      (v) =>
        !v.lang.toLowerCase().startsWith("en") &&
        !/english|david|zira|hazel|george|susan|mark|ravi|heera|neerja/i.test(v.name)
    );

    // Prefer exact Hindi/Devanagari voices (e.g. Microsoft Hemant, Microsoft Kalpana, Google हिन्दी, Swara, Madhur)
    const hindiVoice =
      nonEnglishVoices.find((v) => v.lang.toLowerCase() === "hi-in" || v.lang.toLowerCase().startsWith("hi")) ||
      nonEnglishVoices.find((v) => /hindi|हिन्दी|hemant|kalpana|swara|madhur|devanagari/i.test(v.name)) ||
      // Fallback to Marathi Devanagari voices
      nonEnglishVoices.find((v) => v.lang.toLowerCase().startsWith("mr") || /marathi|मराठी/i.test(v.name)) ||
      // Any voice whose name contains Hindi
      voices.find((v) => /hindi|हिन्दी|hemant|kalpana|swara|madhur/i.test(v.name));

    if (hindiVoice) return hindiVoice;
  }

  // 2. GURMUKHI SCRIPT (Punjabi)
  if (isGurmukhi || (langTag && langTag.startsWith("pa"))) {
    const nonEnglish = voices.filter((v) => !v.lang.toLowerCase().startsWith("en"));
    const punjabiVoice =
      nonEnglish.find((v) => v.lang.toLowerCase().startsWith("pa") || /punjabi|ਪੰਜਾਬੀ/i.test(v.name)) ||
      // Fallback to Indian phonetic voice
      nonEnglish.find((v) => v.lang.toLowerCase().startsWith("hi") || /hindi|hemant|kalpana/i.test(v.name));
    if (punjabiVoice) return punjabiVoice;
  }

  // 3. BENGALI SCRIPT (Bangla / Assamese)
  if (isBengali || (langTag && langTag.startsWith("bn")) || (langTag && langTag.startsWith("as"))) {
    const nonEnglish = voices.filter((v) => !v.lang.toLowerCase().startsWith("en"));
    const bengaliVoice =
      nonEnglish.find((v) => v.lang.toLowerCase().startsWith("bn") || /bengali|bangla|বাংলা/i.test(v.name)) ||
      nonEnglish.find((v) => v.lang.toLowerCase().startsWith("as") || /assamese|অসমীয়া/i.test(v.name)) ||
      nonEnglish.find((v) => v.lang.toLowerCase().startsWith("hi") || /hindi|hemant|kalpana/i.test(v.name));
    if (bengaliVoice) return bengaliVoice;
  }

  // 4. TAMIL SCRIPT
  if (isTamil || (langTag && langTag.startsWith("ta"))) {
    const nonEnglish = voices.filter((v) => !v.lang.toLowerCase().startsWith("en"));
    const tamilVoice =
      nonEnglish.find((v) => v.lang.toLowerCase().startsWith("ta") || /tamil|தமிழ்/i.test(v.name)) ||
      nonEnglish.find((v) => v.lang.toLowerCase().startsWith("hi") || /hindi|hemant|kalpana/i.test(v.name));
    if (tamilVoice) return tamilVoice;
  }

  // 5. TELUGU SCRIPT
  if (isTelugu || (langTag && langTag.startsWith("te"))) {
    const nonEnglish = voices.filter((v) => !v.lang.toLowerCase().startsWith("en"));
    const teluguVoice =
      nonEnglish.find((v) => v.lang.toLowerCase().startsWith("te") || /telugu|తెలుగు/i.test(v.name)) ||
      nonEnglish.find((v) => v.lang.toLowerCase().startsWith("hi") || /hindi|hemant|kalpana/i.test(v.name));
    if (teluguVoice) return teluguVoice;
  }

  // 6. GUJARATI SCRIPT
  if (isGujarati || (langTag && langTag.startsWith("gu"))) {
    const nonEnglish = voices.filter((v) => !v.lang.toLowerCase().startsWith("en"));
    const gujaratiVoice =
      nonEnglish.find((v) => v.lang.toLowerCase().startsWith("gu") || /gujarati|ગુજરાતી/i.test(v.name)) ||
      nonEnglish.find((v) => v.lang.toLowerCase().startsWith("hi") || /hindi|hemant|kalpana/i.test(v.name));
    if (gujaratiVoice) return gujaratiVoice;
  }

  // 7. KANNADA SCRIPT
  if (isKannada || (langTag && langTag.startsWith("kn"))) {
    const nonEnglish = voices.filter((v) => !v.lang.toLowerCase().startsWith("en"));
    const kannadaVoice =
      nonEnglish.find((v) => v.lang.toLowerCase().startsWith("kn") || /kannada|ಕನ್ನಡ/i.test(v.name)) ||
      nonEnglish.find((v) => v.lang.toLowerCase().startsWith("hi") || /hindi|hemant|kalpana/i.test(v.name));
    if (kannadaVoice) return kannadaVoice;
  }

  // 8. MALAYALAM SCRIPT
  if (isMalayalam || (langTag && langTag.startsWith("ml"))) {
    const nonEnglish = voices.filter((v) => !v.lang.toLowerCase().startsWith("en"));
    const malayalamVoice =
      nonEnglish.find((v) => v.lang.toLowerCase().startsWith("ml") || /malayalam|മലയാളം/i.test(v.name)) ||
      nonEnglish.find((v) => v.lang.toLowerCase().startsWith("hi") || /hindi|hemant|kalpana/i.test(v.name));
    if (malayalamVoice) return malayalamVoice;
  }

  // 9. URDU SCRIPT
  if (isUrdu || (langTag && langTag.startsWith("ur"))) {
    const nonEnglish = voices.filter((v) => !v.lang.toLowerCase().startsWith("en"));
    const urduVoice =
      nonEnglish.find((v) => v.lang.toLowerCase().startsWith("ur") || /urdu|اردو/i.test(v.name)) ||
      nonEnglish.find((v) => v.lang.toLowerCase().startsWith("hi") || /hindi|hemant|kalpana/i.test(v.name));
    if (urduVoice) return urduVoice;
  }

  // 10. NON-LATIN SCRIPT GUARD:
  // If text is in an Indian regional script and no dedicated voice exists,
  // NEVER assign an English voice (David/Zira/etc.)! Returning null allows
  // the browser/OS to select the native phonetic engine for utterance.lang.
  if (isDevanagari || isGurmukhi || isBengali || isTamil || isTelugu || isGujarati || isKannada || isMalayalam || isUrdu) {
    return null;
  }

  // 11. ENGLISH / DEFAULT LATIN
  const englishVoice =
    // Indian English preferred for regional context
    voices.find((v) => v.lang.toLowerCase() === "en-in" || /india|heera|ravi|neerja/i.test(v.name)) ||
    voices.find((v) => v.lang.toLowerCase().startsWith("en")) ||
    voices[0];

  return englishVoice || null;
}

/**
 * High-speed native browser speech synthesis execution.
 * Guarantees instantaneous audio start with correct regional phonetics.
 */
export async function speakBrowserText(cleanSpeechText, langTag, options = {}) {
  const { onStart, onEnd, onError, rate = 1.0, pitch = 1.0 } = options;

  if (typeof window === "undefined" || !window.speechSynthesis) {
    throw new Error("Speech synthesis is not supported in this browser.");
  }

  try {
    window.speechSynthesis.cancel();
  } catch { /* noop */ }

  const voices = await getVoicesAsync();
  const phoneticLang = getBrowserPhoneticLang(cleanSpeechText, langTag);
  const voice = resolveBrowserVoice(voices, cleanSpeechText, langTag);

  const utterance = new SpeechSynthesisUtterance(cleanSpeechText);
  utterance.rate = rate;
  utterance.pitch = pitch;
  utterance.lang = phoneticLang;
  if (voice) {
    utterance.voice = voice;
  }

  if (onStart) utterance.onstart = onStart;
  if (onEnd) utterance.onend = onEnd;
  if (onError) utterance.onerror = onError;

  window.speechSynthesis.speak(utterance);
  return utterance;
}

