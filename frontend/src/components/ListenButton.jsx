import { useEffect, useRef, useState } from "react";
import { voiceApi, fetchSpeech } from "../api/phase5";
import { getToken } from "../api/client";
import { Tooltip } from "./UI";
import { pcmToWav, cleanTextForSpeech, detectLanguageFromText, speakBrowserText } from "../utils/audio";

let serverTtsSupported = null;

const LANGUAGES = [
  ["auto", "🌐 Auto Detect"],
  ["mai-IN", "मैथिली (Maithili)"],
  ["hi-IN", "हिन्दी (Hindi)"],
  ["pa-IN", "ਪੰਜਾਬੀ (Punjabi)"],
  ["bn-IN", "বাংলা (Bangla)"],
  ["en-IN", "English (India)"],
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
];

const SpeakerIcon = (
  <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" width="15" height="15" aria-hidden="true">
    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M19.114 5.636a9 9 0 010 12.728M16.463 8.288a5.25 5.25 0 010 7.424M6.75 8.25l4.72-4.72a.75.75 0 011.28.53v15.88a.75.75 0 01-1.28.53l-4.72-4.72H4.51c-.88 0-1.704-.507-1.938-1.354A9.01 9.01 0 012.25 12c0-.83.112-1.633.322-2.396C2.806 8.756 3.63 8.25 4.51 8.25H6.75z" />
  </svg>
);

const PauseIcon = (
  <svg fill="currentColor" viewBox="0 0 24 24" width="13" height="13" aria-hidden="true">
    <path d="M6 5h4v14H6V5zm8 0h4v14h-4V5z" />
  </svg>
);

const PlayIcon = (
  <svg fill="currentColor" viewBox="0 0 24 24" width="13" height="13" aria-hidden="true">
    <path d="M8 5v14l11-7z" />
  </svg>
);

const RepeatIcon = (
  <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" width="13" height="13" aria-hidden="true">
    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
  </svg>
);

const StopIcon = (
  <svg fill="currentColor" viewBox="0 0 24 24" width="12" height="12" aria-hidden="true">
    <rect x="6" y="6" width="12" height="12" rx="2" />
  </svg>
);

// Session memory cache so replaying the same text is 0ms instant
const audioBlobCache = new Map();

/**
 * Fast, user-friendly speech synthesis control with Pause, Resume, Repeat, and Stop.
 * Uses high-speed caching and seamless browser speech synthesis fallback for 0ms reactivity.
 */
export default function ListenButton({ text, compact = false, initialLang = "auto" }) {
  const [lang, setLang] = useState(initialLang || "auto");
  // State: "idle" | "loading" | "playing" | "paused" | "error"
  const [state, setState] = useState("idle");
  const [hasPlayedOnce, setHasPlayedOnce] = useState(false);
  const [error, setError] = useState(null);
  const modeRef = useRef("audio"); // "audio" | "synth"
  const audioRef = useRef(null);
  const urlRef = useRef(null);
  const abortControllerRef = useRef(null);

  useEffect(() => {
    return () => {
      if (abortControllerRef.current) abortControllerRef.current.abort();
      if (audioRef.current) {
        audioRef.current.pause();
        audioRef.current = null;
      }
      if (typeof window !== "undefined" && window.speechSynthesis) {
        window.speechSynthesis.cancel();
      }
      if (urlRef.current) {
        URL.revokeObjectURL(urlRef.current);
        urlRef.current = null;
      }
    };
  }, []);

  const getLanguageTag = (clean) => {
    if (lang && lang !== "auto" && lang !== "Auto detect") return lang;
    return detectLanguageFromText(clean);
  };

  const speakWithBrowserSynth = async (cleanSpeechText, langTag) => {
    try {
      modeRef.current = "synth";
      setState("playing");
      setHasPlayedOnce(true);

      await speakBrowserText(cleanSpeechText, langTag, {
        onStart: () => {
          modeRef.current = "synth";
          setState("playing");
          setHasPlayedOnce(true);
        },
        onEnd: () => {
          setState("idle");
        },
        onError: (e) => {
          if (e && e.error !== "canceled" && e.error !== "interrupted") {
            setState("idle");
          }
        },
      });
    } catch (err) {
      console.warn("Browser regional speech synthesis failed:", err);
      setState("idle");
    }
  };

  const play = async (forceRestart = false) => {
    // If paused, simply resume
    if (state === "paused" && !forceRestart) {
      if (modeRef.current === "audio" && audioRef.current) {
        try {
          await audioRef.current.play();
          setState("playing");
          return;
        } catch { /* proceed to resume synth */ }
      }
      if (modeRef.current === "synth" && typeof window !== "undefined" && window.speechSynthesis) {
        window.speechSynthesis.resume();
        setState("playing");
        return;
      }
    }

    // If already playing and not forced restart, ignore
    if (state === "playing" && !forceRestart) return;

    setError(null);

    const clean = cleanTextForSpeech(text);
    if (!clean || !clean.trim()) {
      setState("idle");
      return;
    }

    const targetLang = getLanguageTag(clean);
    const cacheKey = `${targetLang}:${clean.slice(0, 300)}`;

    // 1. Instant Cache Hit Check (0ms latency!)
    if (audioBlobCache.has(cacheKey)) {
      try {
        const cachedBlob = audioBlobCache.get(cacheKey);
        if (urlRef.current) URL.revokeObjectURL(urlRef.current);
        urlRef.current = URL.createObjectURL(cachedBlob);
        const audio = new Audio(urlRef.current);
        audioRef.current = audio;
        modeRef.current = "audio";

        audio.onended = () => setState("idle");
        audio.onerror = () => {
          speakWithBrowserSynth(clean.slice(0, 1600), targetLang);
        };

        await audio.play();
        setState("playing");
        setHasPlayedOnce(true);
        return;
      } catch (cacheErr) {
        console.debug("Cache replay notice, falling back:", cacheErr);
      }
    }

    // If server TTS was already determined unviable (e.g. 403 on cloud audio), 0ms instant native voice!
    if (serverTtsSupported === false) {
      speakWithBrowserSynth(clean.slice(0, 1600), targetLang);
      return;
    }

    setState("loading");

    // 2. Fetch server speech with ultra-fast 800ms abort timeout
    const controller = new AbortController();
    abortControllerRef.current = controller;
    const timeoutId = setTimeout(() => controller.abort(), 800);

    try {
      const token = getToken() || sessionStorage.getItem("sachet.token");
      const snippet = clean.slice(0, 1400);
      const blob = await fetchSpeech(snippet, targetLang, token, controller.signal);
      clearTimeout(timeoutId);
      serverTtsSupported = true;

      const raw = await blob.arrayBuffer();
      const wav = pcmToWav(raw, 24000);
      audioBlobCache.set(cacheKey, wav);

      if (urlRef.current) URL.revokeObjectURL(urlRef.current);
      urlRef.current = URL.createObjectURL(wav);
      const audio = new Audio(urlRef.current);
      audioRef.current = audio;
      modeRef.current = "audio";

      audio.onended = () => setState("idle");
      audio.onerror = () => {
        speakWithBrowserSynth(clean.slice(0, 1600), targetLang);
      };

      await audio.play();
      setState("playing");
      setHasPlayedOnce(true);
    } catch (err) {
      clearTimeout(timeoutId);
      serverTtsSupported = false;
      // Fallback immediately to zero-latency Browser Regional Speech Synthesis
      try {
        speakWithBrowserSynth(clean.slice(0, 1600), targetLang);
      } catch (synthErr) {
        console.warn("Both server and browser regional TTS failed:", synthErr);
        setError("Speech synthesis could not start.");
        setState("idle");
      }
    } finally {
      abortControllerRef.current = null;
    }
  };

  const pause = () => {
    if (modeRef.current === "audio" && audioRef.current) {
      try {
        audioRef.current.pause();
      } catch { /* noop */ }
    } else if (modeRef.current === "synth" && typeof window !== "undefined" && window.speechSynthesis) {
      window.speechSynthesis.pause();
    }
    setState("paused");
  };

  const repeat = () => {
    if (modeRef.current === "audio" && audioRef.current) {
      try {
        audioRef.current.currentTime = 0;
        audioRef.current.play();
        setState("playing");
        return;
      } catch { /* re-run play */ }
    }
    play(true);
  };

  const stop = () => {
    if (abortControllerRef.current) abortControllerRef.current.abort();
    if (modeRef.current === "audio" && audioRef.current) {
      try {
        audioRef.current.pause();
        audioRef.current.currentTime = 0;
      } catch { /* noop */ }
    }
    if (typeof window !== "undefined" && window.speechSynthesis) {
      window.speechSynthesis.cancel();
    }
    setState("idle");
  };

  const detected = detectLanguageFromText(cleanTextForSpeech(text));
  const langLabel = detected === "mai-IN" ? "मैथिली / हिन्दी" :
                    detected === "pa-IN" ? "ਪੰਜਾਬੀ" :
                    detected === "bn-IN" ? "বাংলা" :
                    detected === "ta-IN" ? "தமிழ்" :
                    detected === "te-IN" ? "తెలుగు" :
                    detected === "mr-IN" ? "मराठी" :
                    detected === "gu-IN" ? "ગુજરાતી" :
                    detected === "en-IN" ? "English (India)" : "English";

  // Compact layout for chat bubbles
  if (compact) {
    return (
      <span className="inline-flex items-center gap-1">
        {state === "idle" && (
          <Tooltip content={hasPlayedOnce ? `Listen again (${langLabel})` : `Listen aloud (${langLabel})`}>
            <button
              type="button"
              className="p-1 rounded-lg text-slate-500 dark:text-slate-300 hover:text-emerald-600 dark:hover:text-emerald-400 hover:bg-slate-200/50 dark:hover:bg-zinc-700/50 transition-colors flex items-center justify-center cursor-pointer"
              onClick={() => play(false)}
              aria-label={hasPlayedOnce ? "Listen again" : "Listen aloud"}
            >
              {hasPlayedOnce ? RepeatIcon : SpeakerIcon}
            </button>
          </Tooltip>
        )}

        {state === "loading" && (
          <Tooltip content="Preparing speech synthesis…">
            <span className="p-1 rounded-lg flex items-center justify-center text-emerald-500 animate-pulse">
              <span className="w-3.5 h-3.5 border-2 border-emerald-500 border-t-transparent rounded-full animate-spin" />
            </span>
          </Tooltip>
        )}

        {state === "playing" && (
          <span className="inline-flex items-center gap-0.5 bg-emerald-500/15 border border-emerald-500/30 rounded-lg px-1 py-0.5 animate-fadeIn">
            <Tooltip content="Pause reading aloud">
              <button
                type="button"
                className="p-1 rounded hover:bg-emerald-500/20 text-emerald-600 dark:text-emerald-400 cursor-pointer transition-colors"
                onClick={pause}
                aria-label="Pause reading"
              >
                {PauseIcon}
              </button>
            </Tooltip>
            <Tooltip content="Repeat from start">
              <button
                type="button"
                className="p-1 rounded hover:bg-emerald-500/20 text-emerald-600 dark:text-emerald-400 cursor-pointer transition-colors"
                onClick={repeat}
                aria-label="Repeat from start"
              >
                {RepeatIcon}
              </button>
            </Tooltip>
            <Tooltip content="Stop reading">
              <button
                type="button"
                className="p-1 rounded hover:bg-red-500/20 text-red-600 dark:text-red-400 cursor-pointer transition-colors"
                onClick={stop}
                aria-label="Stop reading"
              >
                {StopIcon}
              </button>
            </Tooltip>
          </span>
        )}

        {state === "paused" && (
          <span className="inline-flex items-center gap-0.5 bg-amber-500/15 border border-amber-500/30 rounded-lg px-1 py-0.5 animate-fadeIn">
            <Tooltip content="Resume reading">
              <button
                type="button"
                className="p-1 rounded hover:bg-amber-500/20 text-amber-600 dark:text-amber-400 cursor-pointer transition-colors"
                onClick={() => play(false)}
                aria-label="Resume reading"
              >
                {PlayIcon}
              </button>
            </Tooltip>
            <Tooltip content="Repeat from start">
              <button
                type="button"
                className="p-1 rounded hover:bg-amber-500/20 text-amber-600 dark:text-amber-400 cursor-pointer transition-colors"
                onClick={repeat}
                aria-label="Repeat from start"
              >
                {RepeatIcon}
              </button>
            </Tooltip>
            <Tooltip content="Stop reading">
              <button
                type="button"
                className="p-1 rounded hover:bg-red-500/20 text-red-600 dark:text-red-400 cursor-pointer transition-colors"
                onClick={stop}
                aria-label="Stop reading"
              >
                {StopIcon}
              </button>
            </Tooltip>
          </span>
        )}

        {error && (
          <Tooltip content={error}>
            <span className="text-[11px] text-red-500 cursor-help" role="alert">⚠️</span>
          </Tooltip>
        )}
      </span>
    );
  }

  // Full toolbar view
  return (
    <span
      className={`listen-control ${state !== "idle" ? "listen-control--active" : ""}`}
      style={{ display: "inline-flex", alignItems: "center", gap: 6, flexWrap: "nowrap" }}
    >
      <select
        className="ui-select"
        value={lang}
        onChange={(e) => setLang(e.target.value)}
        disabled={state === "loading" || state === "playing"}
        aria-label="Speech language"
        title="Speech language: Auto detect matches the script automatically"
        style={{
          height: 28,
          fontSize: "12px",
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

      {state === "idle" && (
        <Tooltip content={hasPlayedOnce ? `Listen again (${langLabel})` : `Listen aloud (${langLabel})`}>
          <button
            type="button"
            className="ui-btn ui-btn--ghost ui-btn--sm"
            onClick={() => play(false)}
            aria-label="Listen aloud"
            style={{ height: 28, padding: "0 8px", minWidth: 28 }}
          >
            {hasPlayedOnce ? RepeatIcon : SpeakerIcon}
          </button>
        </Tooltip>
      )}

      {state === "loading" && (
        <button
          type="button"
          className="ui-btn ui-btn--ghost ui-btn--sm"
          disabled
          style={{ height: 28, padding: "0 8px", minWidth: 28 }}
        >
          <svg className="ui-spinner ui-spinner--xs" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="3.5" opacity="0.25" />
            <path d="M22 12a10 10 0 00-10-10" stroke="currentColor" strokeWidth="3.5" strokeLinecap="round" />
          </svg>
        </button>
      )}

      {state === "playing" && (
        <span className="inline-flex items-center gap-1">
          <Tooltip content="Pause reading">
            <button
              type="button"
              className="ui-btn ui-btn--sm ui-btn--secondary"
              onClick={pause}
              style={{ height: 28, padding: "0 8px" }}
              aria-label="Pause"
            >
              {PauseIcon}
            </button>
          </Tooltip>
          <Tooltip content="Repeat from start">
            <button
              type="button"
              className="ui-btn ui-btn--sm ui-btn--ghost"
              onClick={repeat}
              style={{ height: 28, padding: "0 8px" }}
              aria-label="Repeat"
            >
              {RepeatIcon}
            </button>
          </Tooltip>
          <Tooltip content="Stop reading">
            <button
              type="button"
              className="ui-btn ui-btn--sm ui-btn--danger-outline"
              onClick={stop}
              style={{ height: 28, padding: "0 8px" }}
              aria-label="Stop"
            >
              {StopIcon}
            </button>
          </Tooltip>
        </span>
      )}

      {state === "paused" && (
        <span className="inline-flex items-center gap-1">
          <Tooltip content="Resume reading">
            <button
              type="button"
              className="ui-btn ui-btn--sm ui-btn--primary"
              onClick={() => play(false)}
              style={{ height: 28, padding: "0 8px" }}
              aria-label="Resume"
            >
              {PlayIcon}
            </button>
          </Tooltip>
          <Tooltip content="Repeat from start">
            <button
              type="button"
              className="ui-btn ui-btn--sm ui-btn--ghost"
              onClick={repeat}
              style={{ height: 28, padding: "0 8px" }}
              aria-label="Repeat"
            >
              {RepeatIcon}
            </button>
          </Tooltip>
          <Tooltip content="Stop reading">
            <button
              type="button"
              className="ui-btn ui-btn--sm ui-btn--danger-outline"
              onClick={stop}
              style={{ height: 28, padding: "0 8px" }}
              aria-label="Stop"
            >
              {StopIcon}
            </button>
          </Tooltip>
        </span>
      )}

      {error && (
        <Tooltip content={error}>
          <span className="ui-small" style={{ color: "var(--color-danger-700)", cursor: "help" }} role="alert">⚠️</span>
        </Tooltip>
      )}
    </span>
  );
}