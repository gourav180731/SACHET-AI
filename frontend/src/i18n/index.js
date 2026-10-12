const dictionaries = {
  en: {
    appName: "SACHET AI",
    tagline: "Security-first AI Knowledge Assistant",
    nav: {
      dashboard: "Dashboard",
      assistant: "AI Assistant",
      knowledge: "Knowledge Vault",
      documents: "Documents",
      studio: "Document Studio",
      search: "Search",
      generated: "Generated Files",
      audit: "Audit & Activity",
      security: "Security & Privacy",
      settings: "Settings",
    },
  },
};

export const SUPPORTED_LANGUAGES = [
  { code: "en", name: "English" },
  { code: "hi", name: "Hindi" },
  { code: "mai", name: "Maithili" },
  { code: "ks", name: "Kashmiri" },
  { code: "bn", name: "Bengali" },
  { code: "te", name: "Telugu" },
  { code: "mr", name: "Marathi" },
  { code: "ta", name: "Tamil" },
  { code: "gu", name: "Gujarati" },
  { code: "kn", name: "Kannada" },
  { code: "ml", name: "Malayalam" },
  { code: "or", name: "Odia" },
  { code: "pa", name: "Punjabi" },
  { code: "as", name: "Assamese" },
  { code: "ur", name: "Urdu" },
];

let current = "en";
export function setLanguage(code) { current = dictionaries[code] ? code : "en"; }
export function getLanguage() { return current; }
export function t(key, fallback) {
  const parts = key.split(".");
  let node = dictionaries[current] || dictionaries.en;
  for (const p of parts) node = node?.[p];
  return typeof node === "string" ? node : fallback ?? key;
}
