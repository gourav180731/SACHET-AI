import { useEffect, useRef, useState } from "react";
import { voiceApi, fetchSpeech } from "../api/phase5";
import { getToken } from "../api/client";
import { Tooltip } from "./UI";
import { pcmToWav, cleanTextForSpeech, detectLanguageFromText } from "../utils/audio";

const LANGUAGES = [
  ["auto", "🌐 Auto Detect"],
  ["en-IN", "English"],
  ["hi-IN", "हिन्दी (Hindi)"],
  ["mai-IN", "मैथिली (Maithili)"],
  ["pa-IN", "ਪੰਜਾਬੀ (Punjabi)"],
  ["bn-IN", "বাংলা (Bangla)"],
  ["te-IN", "తెలుగు (Telugu)"],
  ["mr-IN", "मराठी (Marathi)"],
  ["ta-IN", "தமிழ் (Tamil)"],
  ["gu-IN", "ગુજરાતી (Gujarati)"],
  ["kn-IN", "ಕನ್ನಡ (Kannada)"],
  ["ml-IN", "മലയാളം (Malayalam)"],
  ["ur-IN", "اردو (Urdu)"],
  ["or-IN", "ଓଡ଼ିଆ (Odia)"],
  ["as-IN", "অসমীয়া (Assamese)"],
  ["ks-IN", "कॉशुर (Kashmiri)"],
  ["kok-IN", "कोंकणी (Konkani)"],
];

const SpeakerIcon = (
  <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" width="15" height="15" aria-hidden="true">
    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M19.114 5.636a9 9 0 010 12.728M16.463 8.288a5.25 5.25 0 010 7.424M6.75 8.25l4.72-4.72a.75.75 0 011.28.53v15.88a.75.75 0 01-1.28.53l-4.72-4.72H4.51c-.88 0-1.704-.507-1.938-1.354A9.01 9.01 0 012.25 12c0-.83.112-1.633.322-2.396C2.806 8.756 3.63 8.25 4.51 8.25H6.75z" />
  </svg>
);

const StopIcon = (
  <svg fill="currentColor" viewBox="0 0 24 24" width="13" height="13" aria-hidden="true">
    <rect x="6" y="6" width="12" height="12" rx="2" />
  </svg>
);

/**
 * Read an answer aloud with automatic language and script detection.
 */
export default function ListenButton({ text, compact = false, initialLang = "auto" }) {
  const [lang, setLang] = useState(initialLang || "auto");
  const [state, setState] = useState("idle"); // idle | loading | playing | error
  const [error, setError] = useState(null);
  const [available, setAvailable] = useState(false);
  const audioRef = useRef(null);
  const urlRef = useRef(null);

  useEffect(() => {
    let alive = true;
    voiceApi.ttsCapabilities()
      .then((c) => { if (alive) setAvailable(!!c.available); })
      .catch(() => { if (alive) setAvailable(false); });
    return () => {
      alive = false;
      audioRef.current?.pause();
      if (urlRef.current) URL.revokeObjectURL(urlRef.current);
    };
  }, []);

  const play = async () => {
    if (state === "loading" || state === "playing") return;
    setError(null);
    setState("loading");
    try {
      const clean = cleanTextForSpeech(text);
      if (!clean) {
        setState("idle");
        return;
      }
      const token = getToken() || sessionStorage.getItem("sachet.token");
      const targetLang = (lang && lang !== "auto" && lang !== "Auto detect")
        ? lang
        : detectLanguageFromText(clean);

      const blob = await fetchSpeech(clean.slice(0, 4000), targetLang, token);
      const raw = await blob.arrayBuffer();
      const wav = pcmToWav(raw, 24000);
      if (urlRef.current) URL.revokeObjectURL(urlRef.current);
      urlRef.current = URL.createObjectURL(wav);
      const audio = new Audio(urlRef.current);
      audioRef.current = audio;
      audio.onended = () => setState("idle");
      audio.onerror = () => { setState("error"); setError("Could not play the audio."); };
      await audio.play();
      setState("playing");
    } catch (e) {
      console.warn("Speech playback error:", e);
      setState("error");
      setError(e.message || "Speech synthesis failed.");
    }
  };

  const stop = () => {
    audioRef.current?.pause();
    setState("idle");
  };

  if (!available) {
    return (
      <Tooltip content="Speech synthesis unavailable (set SACHET_TTS_PROVIDER=gemini and API key in .env)">
        <button
          type="button"
          className="p-1 text-slate-400 hover:text-slate-600 rounded transition-colors opacity-40 cursor-not-allowed"
          disabled
          aria-label="Speech synthesis not configured"
        >
          {SpeakerIcon}
        </button>
      </Tooltip>
    );
  }

  const detected = detectLanguageFromText(text);
  const langLabel = detected === "mai-IN" ? "मैथिली / हिन्दी" : detected === "pa-IN" ? "ਪੰਜਾਬੀ" : detected === "bn-IN" ? "বাংলা" : "English";

  if (compact) {
    return (
      <span className="inline-flex items-center gap-1">
        <Tooltip content={state === "playing" ? "Stop speaking" : (state === "loading" ? "Preparing speech…" : `Listen aloud (${langLabel})`)}>
          <button
            type="button"
            className={`p-1 rounded-lg transition-colors flex items-center justify-center cursor-pointer ${
              state === "playing"
                ? "bg-red-500/10 text-red-600 dark:text-red-400 font-bold"
                : state === "loading"
                ? "bg-emerald-500/10 text-emerald-600 animate-pulse"
                : "text-slate-500 hover:text-emerald-600 hover:bg-slate-200/50 dark:hover:bg-zinc-700/50"
            }`}
            onClick={state === "playing" ? stop : play}
            disabled={state === "loading"}
            aria-label={state === "playing" ? "Stop speaking" : "Listen aloud"}
          >
            {state === "loading" ? (
              <span className="w-3.5 h-3.5 border-2 border-emerald-600 border-t-transparent rounded-full animate-spin" />
            ) : (state === "playing" ? StopIcon : SpeakerIcon)}
          </button>
        </Tooltip>
        {error && (
          <Tooltip content={error}>
            <span className="text-[10px] text-red-500 cursor-help" role="alert">⚠️</span>
          </Tooltip>
        )}
      </span>
    );
  }

  return (
    <span className={`listen-control ${state !== "idle" ? "listen-control--active" : ""}`} style={{ display: "inline-flex", alignItems: "center", gap: 4, flexWrap: "nowrap" }}>
      <select
        className="ui-select"
        value={lang}
        onChange={(e) => setLang(e.target.value)}
        disabled={state === "loading" || state === "playing"}
        aria-label="Speech language"
        title="Speech language: Auto detect matches the script automatically"
        style={{
          height: 28,
          fontSize: "11px",
          padding: "0 22px 0 6px",
          width: "auto",
          minWidth: 84,
          borderRadius: "var(--radius-sm, 4px)",
        }}
      >
        {LANGUAGES.map(([code, label]) => (
          <option key={code} value={code}>{label}</option>
        ))}
      </select>

      <Tooltip content={state === "playing" ? "Stop speaking" : (state === "loading" ? "Synthesizing…" : "Listen (Read aloud)")}>
        <button
          type="button"
          className={`ui-btn ui-btn--sm ${state === "playing" ? "ui-btn--danger-outline" : "ui-btn--ghost"}`}
          onClick={state === "playing" ? stop : play}
          disabled={state === "loading"}
          aria-label={state === "playing" ? "Stop speaking" : "Read this aloud"}
          style={{ height: 28, padding: "0 8px", minWidth: 28 }}
        >
          {state === "loading" ? (
            <svg className="ui-spinner ui-spinner--xs" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="3.5" opacity="0.25" />
              <path d="M22 12a10 10 0 00-10-10" stroke="currentColor" strokeWidth="3.5" strokeLinecap="round" />
            </svg>
          ) : (state === "playing" ? StopIcon : SpeakerIcon)}
        </button>
      </Tooltip>

      {error && (
        <Tooltip content={error}>
          <span className="ui-small" style={{ color: "var(--color-danger-700)", cursor: "help" }} role="alert">⚠️</span>
        </Tooltip>
      )}
    </span>
  );
}