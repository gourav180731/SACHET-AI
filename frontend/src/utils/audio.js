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
    if (code >= 0x0900 && code <= 0x097f) return "mai-IN"; // Devanagari (Maithili/Hindi)
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
    "एक सेकंड, अधिकृत दस्तावेज़ देख रहल छी...",
    "दस्तावेज़ में खोज कऽ रहल छी...",
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
