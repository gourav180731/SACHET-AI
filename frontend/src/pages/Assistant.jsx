import { useEffect, useState, useRef, useCallback } from "react";
import { useSearchParams, Link } from "react-router-dom";
import { chatApi, documentsApi } from "../api";
import { generationApi, voiceApi, fetchSpeech } from "../api/phase5";
import { getToken } from "../api/client";
import { pcmToWav, cleanTextForSpeech, detectLanguageFromText, getInstantAck, speakBrowserText } from "../utils/audio";
import ListenButton from "../components/ListenButton";
import VoiceOrb from "../components/VoiceOrb";
import {
  Button, DeleteButton, Modal, Select, Textarea, Input, Badge, DotsLoader,
  Alert, Tooltip, EmptyState,
} from "../components/UI";

const EXAMPLES = [
  "Summarize the latest uploaded document.",
  "What are the key recommendations in this document?",
  "Compare the current and previous versions.",
  "Create an executive briefing from these documents.",
  "Find all references to emergency communication.",
];

const REGIONAL_LANGUAGES = [
  { code: "Auto detect", label: "Auto detect", flag: "🌐" },
  { code: "mai-IN", label: "मैथिली (Maithili)", flag: "🇮🇳" },
  { code: "pa-IN", label: "ਪੰਜਾਬੀ (Punjabi)", flag: "🇮🇳" },
  { code: "hi-IN", label: "हिन्दी (Hindi)", flag: "🇮🇳" },
  { code: "en-IN", label: "English (India)", flag: "🇮🇳" },
  { code: "bn-IN", label: "বাংলা (Bengali)", flag: "🇮🇳" },
  { code: "mr-IN", label: "मराठी (Marathi)", flag: "🇮🇳" },
  { code: "ta-IN", label: "தமிழ் (Tamil)", flag: "🇮🇳" },
  { code: "te-IN", label: "తెలుగు (Telugu)", flag: "🇮🇳" },
  { code: "gu-IN", label: "ગુજરાતી (Gujarati)", flag: "🇮🇳" },
  { code: "kn-IN", label: "ಕನ್ನಡ (Kannada)", flag: "🇮🇳" },
  { code: "ml-IN", label: "മലയാളം (Malayalam)", flag: "🇮🇳" },
  { code: "ur-IN", label: "اردو (Urdu)", flag: "🇮🇳" },
  { code: "or-IN", label: "ଓଡ଼ିଆ (Odia)", flag: "🇮🇳" },
  { code: "as-IN", label: "অসমীয়া (Assamese)", flag: "🇮🇳" },
  { code: "ks-IN", label: "कॉशुर (Kashmiri)", flag: "🇮🇳" },
];

const MODES = [
  ["detailed", "Detailed", "Full grounded answer with citations"],
  ["short", "Short", "Concise answer, minimal prose"],
  ["bullets", "Bullets", "Key points as a list"],
  ["custom", "Custom", "Follow your own instruction"],
];

const newId = () => `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 7)}`;

let messageSeq = 0;
const newMessageId = () => `${Date.now().toString(36)}-${(messageSeq += 1)}`;

const fmtTime = (d) => new Date(d).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });

const SHIELD = (
  <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" width="16" height="16" aria-hidden="true">
    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M9 12.75L11.25 15 15 9.75M21 12c0 4.97-4.03 9-9 9s-9-4.03-9-9 4.03-9 9-9c1.19 0 2.33.22 3.36.62M21 12a9 9 0 00-9-9" />
  </svg>
);

const MicIcon = () => (
  <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 18.75a6 6 0 006-6v-1.5m-6 7.5a6 6 0 01-6-6v-1.5m6 7.5v3.75m-3.75 0h7.5M12 15a3 3 0 003-3V6a3 3 0 00-6 0v6a3 3 0 003 3z" />
  </svg>
);

const StopIcon = () => (
  <svg fill="currentColor" viewBox="0 0 24 24" width="14" height="14" aria-hidden="true">
    <rect x="5" y="5" width="14" height="14" rx="2" />
  </svg>
);

export default function Assistant() {
  const [params] = useSearchParams();
  const initialDoc = params.get("docId");

  const [docs, setDocs] = useState([]);
  const [selectedDocs, setSelectedDocs] = useState(initialDoc ? [initialDoc] : []);
  const [scopeOpen, setScopeOpen] = useState(false);

  const [chats, setChats] = useState(() => {
    try {
      const saved = JSON.parse(localStorage.getItem("sachet_chats") || "null");
      if (Array.isArray(saved) && saved.length) return saved;
    } catch { /* corrupted storage */ }
    return [{ id: newId(), title: "New Chat", messages: [], createdAt: Date.now() }];
  });
  const [activeChatId, setActiveChatId] = useState(() => {
    const savedId = localStorage.getItem("sachet_active_chat");
    if (savedId) return savedId;
    try {
      const saved = JSON.parse(localStorage.getItem("sachet_chats") || "null");
      if (Array.isArray(saved) && saved.length) return saved[0].id;
    } catch { /* ignore */ }
    return newId();
  });

  const [sidebarOpen, setSidebarOpen] = useState(() => {
    try {
      const saved = localStorage.getItem("sachet_chat_sidebar");
      return saved !== "false";
    } catch {
      return true;
    }
  });

  const toggleSidebar = useCallback(() => {
    setSidebarOpen((prev) => {
      const next = !prev;
      try { localStorage.setItem("sachet_chat_sidebar", String(next)); } catch { /* noop */ }
      return next;
    });
  }, []);

  const [showSettings, setShowSettings] = useState(false);
  const [input, setInput] = useState("");
  const [busy, setBusy] = useState(false);
  const [language, setLanguage] = useState("Auto detect");
  const [responseMode, setResponseMode] = useState("detailed");
  const [customPrompt, setCustomPrompt] = useState("");
  const [error, setError] = useState(null);

  // SACHET Voice Agent State Machine: "SLEEPING" | "LISTENING" | "THINKING" | "SPEAKING"
  const [voiceMode, setVoiceMode] = useState(false);
  const [voiceState, setVoiceState] = useState("SLEEPING");
  const [isFollowUp, setIsFollowUp] = useState(false);
  const [audioLevel, setAudioLevel] = useState(0); // 0 to 1
  const [instantAck, setInstantAck] = useState("");
  const [detectedLangDisplay, setDetectedLangDisplay] = useState(null);
  const [lastVoiceTurn, setLastVoiceTurn] = useState(null);
  const [micError, setMicError] = useState(null);
  const [recordingSeconds, setRecordingSeconds] = useState(0);

  // Voice & audio refs
  const speechAudioRef = useRef(null);
  const speechUrlRef = useRef(null);
  const mediaRecorderRef = useRef(null);
  const audioChunksRef = useRef([]);
  const streamRef = useRef(null);
  const timerRef = useRef(null);
  const audioCtxRef = useRef(null);
  const analyserRef = useRef(null);
  const animFrameRef = useRef(null);
  const silenceTimerRef = useRef(null);
  const inactivityTimerRef = useRef(null);
  const hasSpokenRef = useRef(false);
  const isStoppingRef = useRef(false);
  const wakeRecRef = useRef(null);
  const recognitionRef = useRef(null);
  const liveSpeechRef = useRef("");

  // Generate document modal state
  const [showGenerate, setShowGenerate] = useState(false);
  const [genSourceMsg, setGenSourceMsg] = useState("");
  const [genTitle, setGenTitle] = useState("");
  const [genType, setGenType] = useState("docx");
  const [genSlides, setGenSlides] = useState(10);
  const [genBusy, setGenBusy] = useState(false);
  const [genError, setGenError] = useState(null);

  const messagesEndRef = useRef(null);
  const inputRef = useRef(null);
  const abortRef = useRef(null);

  const activeChat = chats.find((c) => c.id === activeChatId) || chats[0];
  const messages = activeChat?.messages || [];
  const lastStreaming = messages.length > 0 && messages[messages.length - 1]?.streaming;

  useEffect(() => {
    try {
      localStorage.setItem("sachet_chats", JSON.stringify(chats));
      localStorage.setItem("sachet_active_chat", activeChatId);
    } catch { /* storage full */ }
  }, [chats, activeChatId]);

  useEffect(() => {
    const el = messagesEndRef.current;
    if (el) el.scrollIntoView({ behavior: lastStreaming ? "auto" : "smooth", block: "end" });
  }, [messages, lastStreaming]);

  useEffect(() => {
    let mounted = true;
    documentsApi.list(0, 100)
      .then((d) => { if (mounted) setDocs(d.content || []); })
      .catch(() => {});
    return () => { mounted = false; };
  }, []);

  const updateActiveChat = useCallback((updater) => {
    setChats((prev) => {
      const idx = prev.findIndex((c) => c.id === activeChatId);
      if (idx === -1) return prev;
      const next = [...prev];
      next[idx] = updater(next[idx]);
      return next;
    });
  }, [activeChatId]);

  const createNewChat = useCallback(() => {
    const chat = { id: newId(), title: "New Chat", messages: [], createdAt: Date.now() };
    setChats((prev) => [chat, ...prev]);
    setActiveChatId(chat.id);
    setSidebarOpen(true);
  }, []);

  const deleteChat = useCallback((chatId) => {
    setChats((prev) => {
      const next = prev.filter((c) => c.id !== chatId);
      if (chatId === activeChatId) setActiveChatId(next[0]?.id || newId());
      return next.length ? next : [{ id: newId(), title: "New Chat", messages: [], createdAt: Date.now() }];
    });
  }, [activeChatId]);

  const renameChat = useCallback((chatId, title) => {
    const clean = title.trim();
    if (!clean) return;
    setChats((prev) => prev.map((c) => (c.id === chatId ? { ...c, title: clean.slice(0, 60) } : c)));
  }, []);

  const stopGeneration = useCallback(() => {
    abortRef.current?.abort();
    abortRef.current = null;
    setBusy(false);
    updateActiveChat((chat) => ({ ...chat, messages: chat.messages.filter((m) => !m.streaming) }));
  }, [updateActiveChat]);

  // Zero-latency local wake chime
  const playWakeChime = useCallback(() => {
    try {
      const AudioCtx = window.AudioContext || window.webkitAudioContext;
      if (!AudioCtx) return;
      const ctx = new AudioCtx();
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.frequency.setValueAtTime(523.25, ctx.currentTime); // C5
      osc.frequency.exponentialRampToValueAtTime(783.99, ctx.currentTime + 0.12); // G5
      gain.gain.setValueAtTime(0.15, ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.28);
      osc.start();
      osc.stop(ctx.currentTime + 0.3);
    } catch { /* noop */ }
  }, []);

  const playStartListeningTone = useCallback(() => {
    try {
      const AudioCtx = window.AudioContext || window.webkitAudioContext;
      if (!AudioCtx) return;
      const ctx = new AudioCtx();
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.frequency.setValueAtTime(440, ctx.currentTime); // A4
      osc.frequency.exponentialRampToValueAtTime(659.25, ctx.currentTime + 0.08); // E5
      gain.gain.setValueAtTime(0.12, ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.18);
      osc.start();
      osc.stop(ctx.currentTime + 0.2);
    } catch { /* noop */ }
  }, []);

  const stopSpeaking = useCallback(() => {
    if (speechAudioRef.current) {
      try {
        speechAudioRef.current.pause();
      } catch { /* noop */ }
      speechAudioRef.current = null;
    }
    if (typeof window !== "undefined" && window.speechSynthesis) {
      try { window.speechSynthesis.cancel(); } catch { /* noop */ }
    }
    if (speechUrlRef.current) {
      URL.revokeObjectURL(speechUrlRef.current);
      speechUrlRef.current = null;
    }
  }, []);

  const cleanupMicAndAnalyser = useCallback(() => {
    if (recognitionRef.current) {
      try { recognitionRef.current.stop(); } catch { /* noop */ }
      recognitionRef.current = null;
    }
    if (animFrameRef.current) {
      cancelAnimationFrame(animFrameRef.current);
      animFrameRef.current = null;
    }
    if (silenceTimerRef.current) {
      clearTimeout(silenceTimerRef.current);
      silenceTimerRef.current = null;
    }
    if (inactivityTimerRef.current) {
      clearTimeout(inactivityTimerRef.current);
      inactivityTimerRef.current = null;
    }
    if (timerRef.current) {
      clearInterval(timerRef.current);
      timerRef.current = null;
    }
    if (streamRef.current) {
      streamRef.current.getTracks().forEach((t) => t.stop());
      streamRef.current = null;
    }
    if (audioCtxRef.current && audioCtxRef.current.state !== "closed") {
      try { audioCtxRef.current.close(); } catch { /* noop */ }
      audioCtxRef.current = null;
    }
    analyserRef.current = null;
    setAudioLevel(0);
  }, []);

  // Forward declarations for mutually recursive turn-taking
  const processVoiceChatTurnRef = useRef(null);
  const startListeningRef = useRef(null);
  const finishListeningAndProcessRef = useRef(null);

  // Read answer aloud in voice mode with high speed & fallback
  const playSpokenAnswer = useCallback(async (text, lang) => {
    const cleanText = cleanTextForSpeech(text);
    if (!cleanText || !cleanText.trim()) {
      if (startListeningRef.current) startListeningRef.current(true);
      return;
    }

    // Auto-detect language directly from the text so regional answers speak in their authentic native phonetics
    const textLang = detectLanguageFromText(cleanText);
    const speechLang = (textLang && textLang !== "en-IN")
      ? textLang
      : ((!lang || lang === "Auto detect") ? "en-IN" : lang);

    const snippet = cleanText.slice(0, 1400);

    const onFinish = () => {
      speechAudioRef.current = null;
      // CONVERSATIONAL CONTINUITY: Hands-free follow-up turn!
      if (startListeningRef.current) startListeningRef.current(true);
    };

    const fallbackToBrowserSynth = async () => {
      try {
        await speakBrowserText(snippet, speechLang, {
          onStart: () => {},
          onEnd: onFinish,
          onError: onFinish,
        });
      } catch (err) {
        console.warn("Browser regional TTS fallback error:", err);
        onFinish();
      }
    };

    // Allow 15000ms for high-fidelity Gemini neural TTS synthesis
    const timeoutController = new AbortController();
    const timeoutId = setTimeout(() => timeoutController.abort(), 15000);

    try {
      const token = getToken() || sessionStorage.getItem("sachet.token");
      const blob = await fetchSpeech(snippet, speechLang, token, timeoutController.signal);
      clearTimeout(timeoutId);

      const raw = await blob.arrayBuffer();
      const wav = pcmToWav(raw, 24000);

      if (speechUrlRef.current) URL.revokeObjectURL(speechUrlRef.current);
      speechUrlRef.current = URL.createObjectURL(wav);

      const audio = new Audio(speechUrlRef.current);
      speechAudioRef.current = audio;

      audio.onended = onFinish;
      audio.onerror = () => {
        fallbackToBrowserSynth();
      };

      await audio.play();
    } catch (err) {
      clearTimeout(timeoutId);
      fallbackToBrowserSynth();
    }
  }, []);

  // Voice Chat processing turn
  const processVoiceChatTurn = useCallback(async (query, activeLang) => {
    setError(null);
    setBusy(true);

    const userMsg = { id: newMessageId(), role: "user", text: query, timestamp: Date.now() };
    const botMsg = { id: newMessageId(), role: "assistant", text: "", streaming: true, timestamp: Date.now() };

    updateActiveChat((chat) => ({
      ...chat,
      messages: [...chat.messages, userMsg, botMsg],
      title: chat.messages.length === 0 ? query.slice(0, 40) : chat.title,
    }));

    const controller = new AbortController();
    abortRef.current = controller;

    const detectedQueryLang = detectLanguageFromText(query);
    const lang = (activeLang && activeLang !== "Auto detect" && activeLang !== "auto")
      ? activeLang
      : (detectedQueryLang !== "en-IN" ? detectedQueryLang : "en-IN");

    try {
      let full = "";
      try {
        for await (const chunk of chatApi.askStream(query, {
          responseMode,
          language: lang,
          customPrompt: responseMode === "custom" ? customPrompt : "",
          topK: 10,
          docIds: selectedDocs.length ? selectedDocs : undefined,
        })) {
          if (controller.signal.aborted) break;
          full += chunk;
          const snapshot = full;
          updateActiveChat((chat) => ({
            ...chat,
            messages: chat.messages.map((m) => (m.id === botMsg.id ? { ...m, text: snapshot, streaming: true } : m)),
          }));
        }
      } catch (streamErr) {
        if (controller.signal.aborted) throw streamErr;
        console.warn("askStream error in voice mode, falling back to ask():", streamErr);
        if (!full) {
          const res = await chatApi.ask(query, {
            responseMode,
            language: lang,
            customPrompt: responseMode === "custom" ? customPrompt : "",
            topK: 10,
            docIds: selectedDocs.length ? selectedDocs : undefined,
          });
          full = res?.answer || res?.message || "";
        } else {
          throw streamErr;
        }
      }

      if (!controller.signal.aborted && full) {
        const snapshot = full;
        updateActiveChat((chat) => ({
          ...chat,
          messages: chat.messages.map((m) => (m.id === botMsg.id ? { ...m, text: snapshot, streaming: false } : m)),
        }));
        setLastVoiceTurn({
          userText: query,
          botText: snapshot,
        });

        // Transition to SPEAKING and read aloud
        setVoiceState("SPEAKING");
        await playSpokenAnswer(snapshot.trim(), lang);
      }
    } catch (e) {
      if (e.name !== "AbortError") {
        const msg = e.message || "The request could not be completed.";
        setError(msg);
        updateActiveChat((chat) => ({
          ...chat,
          messages: chat.messages.map((m) => (m.id === botMsg.id ? { ...m, text: msg, streaming: false, error: true } : m)),
        }));
        setVoiceState("SLEEPING");
      }
    } finally {
      abortRef.current = null;
      setBusy(false);
    }
  }, [responseMode, customPrompt, selectedDocs, updateActiveChat, playSpokenAnswer]);

  processVoiceChatTurnRef.current = processVoiceChatTurn;

  // Finish listening and send to speech recognition + RAG
  const finishListeningAndProcess = useCallback(async () => {
    if (isStoppingRef.current) return;
    isStoppingRef.current = true;

    cleanupMicAndAnalyser();

    if (mediaRecorderRef.current && mediaRecorderRef.current.state !== "inactive") {
      try {
        mediaRecorderRef.current.stop();
      } catch { /* noop */ }
    }

    // 1. INSTANT BROWSER TRANSCRIPTION CHECK (0ms latency!)
    const liveTranscript = liveSpeechRef.current?.trim();
    if (liveTranscript && liveTranscript.length >= 2) {
      playWakeChime();
      setVoiceState("THINKING");
      const detected = detectLanguageFromText(liveTranscript);
      setDetectedLangDisplay(detected);
      const activeAck = getInstantAck(detected);
      setInstantAck(activeAck);

      if (processVoiceChatTurnRef.current) {
        await processVoiceChatTurnRef.current(liveTranscript, detected);
      }
      return;
    }

    // Brief tick for ondataavailable to deliver remaining chunks
    await new Promise((r) => setTimeout(r, 120));

    const chunks = audioChunksRef.current;
    if (!chunks.length) {
      setVoiceState("SLEEPING");
      return;
    }

    const mime = mediaRecorderRef.current?.mimeType || "audio/webm";
    const audioBlob = new Blob(chunks, { type: mime });

    if (audioBlob.size < 400 || !hasSpokenRef.current) {
      // In follow-up mode, silence means the user finished their queries -> gracefully sleep
      if (isFollowUp) {
        setVoiceState("SLEEPING");
      } else {
        setMicError("No speech detected. Please speak clearly into your microphone.");
        setVoiceState("SLEEPING");
      }
      return;
    }

    // Transition to THINKING
    playWakeChime();
    setVoiceState("THINKING");
    const activeAck = getInstantAck(language === "Auto detect" ? "en-IN" : language);
    setInstantAck(activeAck);

    try {
      // STT transcription with automatic language detection
      const res = await voiceApi.transcribe(
        audioBlob,
        language === "Auto detect" ? undefined : language
      );

      const transcribed = res?.text?.trim();
      const detectedLang = res?.language;

      if (transcribed) {
        if (detectedLang) {
          setLanguage(detectedLang);
          setDetectedLangDisplay(detectedLang);
        }

        // Query grounded chat and speak response
        if (processVoiceChatTurnRef.current) {
          await processVoiceChatTurnRef.current(transcribed, detectedLang || language);
        }
      } else {
        setMicError("No clear speech recognized. Please try speaking again.");
        setVoiceState("SLEEPING");
      }
    } catch (err) {
      console.warn("Transcription error:", err);
      setMicError(err.message || "Failed to transcribe speech.");
      setVoiceState("SLEEPING");
    }
  }, [cleanupMicAndAnalyser, language, isFollowUp]);

  finishListeningAndProcessRef.current = finishListeningAndProcess;

  // Start voice listening with live Web Audio Analyser, fast browser ASR, and VAD
  const startListening = useCallback(async (followUp = false) => {
    setMicError(null);
    stopSpeaking();
    cleanupMicAndAnalyser();

    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
      setMicError("Microphone access is not supported by your browser.");
      setVoiceState("SLEEPING");
      return;
    }

    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      streamRef.current = stream;
      audioChunksRef.current = [];
      liveSpeechRef.current = "";
      hasSpokenRef.current = false;
      isStoppingRef.current = false;
      setIsFollowUp(followUp);
      setRecordingSeconds(0);
      setVoiceState("LISTENING");
      playStartListeningTone();

      // Launch concurrent browser SpeechRecognition for 0ms recognition latency
      const SpeechRec = window.SpeechRecognition || window.webkitSpeechRecognition;
      if (SpeechRec) {
        try {
          const rec = new SpeechRec();
          rec.continuous = true;
          rec.interimResults = true;
          const activeCode = (language && language !== "Auto detect") ? language : "hi-IN";
          rec.lang = activeCode;
          rec.onresult = (event) => {
            let combined = "";
            for (let i = 0; i < event.results.length; ++i) {
              combined += event.results[i][0].transcript;
            }
            if (combined.trim()) {
              liveSpeechRef.current = combined.trim();
              hasSpokenRef.current = true;
            }
          };
          rec.onerror = () => {};
          rec.start();
          recognitionRef.current = rec;
        } catch { /* proceed with MediaRecorder */ }
      }

      // Setup Web Audio Analyser for live energy visualization & silence VAD
      try {
        const AudioCtx = window.AudioContext || window.webkitAudioContext;
        if (AudioCtx) {
          const ctx = new AudioCtx();
          const source = ctx.createMediaStreamSource(stream);
          const analyser = ctx.createAnalyser();
          analyser.fftSize = 256;
          source.connect(analyser);
          audioCtxRef.current = ctx;
          analyserRef.current = analyser;

          const dataArray = new Uint8Array(analyser.frequencyBinCount);
          let silenceStart = null;
          const VAD_THRESHOLD = 14; // Clean energy threshold
          const SILENCE_TIMEOUT = 1000; // 1.0s silence after speech triggers turn for snappy response

          const checkLevels = () => {
            if (!analyserRef.current) return;
            analyserRef.current.getByteFrequencyData(dataArray);
            let sum = 0;
            for (let i = 0; i < dataArray.length; i++) sum += dataArray[i];
            const avg = sum / dataArray.length;
            setAudioLevel(Math.min(1, avg / 65));

            if (avg > VAD_THRESHOLD) {
              hasSpokenRef.current = true;
              silenceStart = null;
            } else if (hasSpokenRef.current) {
              if (!silenceStart) {
                silenceStart = Date.now();
              } else if (Date.now() - silenceStart > SILENCE_TIMEOUT) {
                // Auto-submit after silence!
                if (finishListeningAndProcessRef.current) {
                  finishListeningAndProcessRef.current();
                }
                return;
              }
            }
            animFrameRef.current = requestAnimationFrame(checkLevels);
          };
          animFrameRef.current = requestAnimationFrame(checkLevels);
        }
      } catch (audioErr) {
        console.debug("Web Audio Analyser notice:", audioErr);
      }

      // Setup MediaRecorder
      let mimeType = "";
      if (typeof MediaRecorder !== "undefined") {
        if (MediaRecorder.isTypeSupported("audio/webm;codecs=opus")) mimeType = "audio/webm;codecs=opus";
        else if (MediaRecorder.isTypeSupported("audio/webm")) mimeType = "audio/webm";
        else if (MediaRecorder.isTypeSupported("audio/ogg;codecs=opus")) mimeType = "audio/ogg;codecs=opus";
        else if (MediaRecorder.isTypeSupported("audio/mp4")) mimeType = "audio/mp4";
      }

      const recorder = mimeType ? new MediaRecorder(stream, { mimeType }) : new MediaRecorder(stream);
      mediaRecorderRef.current = recorder;

      recorder.ondataavailable = (e) => {
        if (e.data && e.data.size > 0) audioChunksRef.current.push(e.data);
      };

      recorder.start(200);

      timerRef.current = setInterval(() => {
        setRecordingSeconds((s) => s + 1);
      }, 1000);

      // Auto-sleep if no speech after 20s
      inactivityTimerRef.current = setTimeout(() => {
        if (!hasSpokenRef.current && !isStoppingRef.current) {
          cleanupMicAndAnalyser();
          if (mediaRecorderRef.current && mediaRecorderRef.current.state !== "inactive") {
            try { mediaRecorderRef.current.stop(); } catch { /* noop */ }
          }
          setVoiceState("SLEEPING");
        }
      }, 20000);

    } catch (err) {
      if (err.name === "NotAllowedError" || err.name === "PermissionDeniedError") {
        setMicError("Microphone access was denied. Please allow microphone permissions in your browser.");
      } else {
        setMicError(`Could not access microphone: ${err.message || err.name}`);
      }
      setVoiceState("SLEEPING");
    }
  }, [cleanupMicAndAnalyser, stopSpeaking]);

  startListeningRef.current = startListening;

  // Local wake-word listener ("Hello Sachet" / "Sachet") in SLEEPING mode
  useEffect(() => {
    if (!voiceMode || voiceState !== "SLEEPING") {
      if (wakeRecRef.current) {
        try { wakeRecRef.current.stop(); } catch { /* noop */ }
        wakeRecRef.current = null;
      }
      return;
    }

    const SpeechRec = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SpeechRec) return;

    let rec = null;
    let active = true;

    try {
      rec = new SpeechRec();
      rec.continuous = true;
      rec.interimResults = true;
      rec.lang = language === "Auto detect" ? "en-IN" : language;

      rec.onresult = (e) => {
        if (!active) return;
        for (let i = e.resultIndex; i < e.results.length; i++) {
          const phrase = e.results[i][0].transcript.toLowerCase();
          if (
            phrase.includes("sachet") ||
            phrase.includes("hello sachet") ||
            phrase.includes("hey sachet") ||
            phrase.includes("सचेत") ||
            phrase.includes("नमस्ते सचेत") ||
            phrase.includes("सुनो सचेत")
          ) {
            active = false;
            try { rec.stop(); } catch { /* noop */ }
            wakeRecRef.current = null;
            playWakeChime();
            if (startListeningRef.current) startListeningRef.current(false);
            break;
          }
        }
      };

      rec.onerror = (e) => {
        if (e.error !== "no-speech" && e.error !== "aborted") {
          console.debug("Wake listener event:", e.error);
        }
      };

      rec.onend = () => {
        if (active && voiceMode && voiceState === "SLEEPING") {
          try { rec.start(); } catch { /* noop */ }
        }
      };

      wakeRecRef.current = rec;
      rec.start();
    } catch (err) {
      console.debug("Local wake recognition init notice:", err);
    }

    return () => {
      active = false;
      if (rec) {
        try { rec.stop(); } catch { /* noop */ }
      }
      wakeRecRef.current = null;
    };
  }, [voiceMode, voiceState, language, playWakeChime]);

  // Clean up all voice streams on unmount
  useEffect(() => {
    return () => {
      cleanupMicAndAnalyser();
      stopSpeaking();
      if (wakeRecRef.current) {
        try { wakeRecRef.current.stop(); } catch { /* noop */ }
        wakeRecRef.current = null;
      }
    };
  }, [cleanupMicAndAnalyser, stopSpeaking]);

  // Standard text chat send message
  const sendMessage = useCallback(async (text, overrides = {}) => {
    const trimmed = text.trim();
    if (!trimmed || busy) return;

    setError(null);
    setBusy(true);

    const userMsg = { id: newMessageId(), role: "user", text: trimmed, timestamp: Date.now() };
    const botMsg = { id: newMessageId(), role: "assistant", text: "", streaming: true, timestamp: Date.now() };

    updateActiveChat((chat) => ({
      ...chat,
      messages: [...chat.messages, userMsg, botMsg],
      title: chat.messages.length === 0 ? trimmed.slice(0, 40) : chat.title,
    }));

    const controller = new AbortController();
    abortRef.current = controller;

    const mode = overrides.responseMode || responseMode;
    const detectedQueryLang = detectLanguageFromText(trimmed);
    const lang = overrides.language || (language && language !== "Auto detect" && language !== "auto" ? language : (detectedQueryLang || "auto"));

    try {
      let full = "";
      try {
        for await (const chunk of chatApi.askStream(trimmed, {
          responseMode: mode,
          language: lang,
          customPrompt: overrides.customPrompt ?? (mode === "custom" ? customPrompt : ""),
          topK: 10,
          docIds: selectedDocs.length ? selectedDocs : undefined,
        })) {
          if (controller.signal.aborted) break;
          full += chunk;
          const snapshot = full;
          updateActiveChat((chat) => ({
            ...chat,
            messages: chat.messages.map((m) => (m.id === botMsg.id ? { ...m, text: snapshot, streaming: true } : m)),
          }));
        }
      } catch (streamErr) {
        if (controller.signal.aborted) throw streamErr;
        console.warn("askStream failed or interrupted, falling back to ask():", streamErr);
        if (!full) {
          const res = await chatApi.ask(trimmed, {
            responseMode: mode,
            language: lang,
            customPrompt: overrides.customPrompt ?? (mode === "custom" ? customPrompt : ""),
            topK: 10,
            docIds: selectedDocs.length ? selectedDocs : undefined,
          });
          full = res?.answer || res?.message || "";
        } else {
          throw streamErr;
        }
      }

      if (!controller.signal.aborted && full) {
        const snapshot = full;
        updateActiveChat((chat) => ({
          ...chat,
          messages: chat.messages.map((m) => (m.id === botMsg.id ? { ...m, text: snapshot, streaming: false } : m)),
        }));
        setLastVoiceTurn({
          userText: trimmed,
          botText: snapshot,
        });
      }
    } catch (e) {
      if (e.name !== "AbortError") {
        const msg = e.message || "The request could not be completed.";
        setError(msg);
        updateActiveChat((chat) => ({
          ...chat,
          messages: chat.messages.map((m) => (m.id === botMsg.id ? { ...m, text: msg, streaming: false, error: true } : m)),
        }));
      }
    } finally {
      abortRef.current = null;
      setBusy(false);
    }
  }, [busy, responseMode, language, customPrompt, selectedDocs, updateActiveChat]);

  const handleSubmit = (e) => {
    e.preventDefault();
    const text = input.trim();
    if (!text) return;
    setInput("");
    sendMessage(text);
  };

  const copyMessage = async (text) => {
    try {
      await navigator.clipboard.writeText(text);
    } catch { /* noop */ }
  };

  const handleGenerate = async (e) => {
    e.preventDefault();
    if (!genTitle.trim() || genBusy) return;
    setGenBusy(true);
    setGenError(null);
    try {
      const payload = {
        title: genTitle.trim(),
        topic: genSourceMsg || "Generate document from chat context",
        docIds: selectedDocs.length ? selectedDocs : undefined,
        slides: genSlides,
      };
      const res = genType === "pptx" ? await generationApi.pptx(payload) : await generationApi.docx(payload);
      setShowGenerate(false);
      setGenSourceMsg("");
      setGenTitle("");
      updateActiveChat((chat) => ({
        ...chat,
        messages: [...chat.messages, {
          role: "assistant",
          text: `Generated ${res.type}: ${res.title} (${res.classification}, ${res.size} bytes).`,
          timestamp: Date.now(),
          isSystem: true,
        }],
      }));
    } catch (err) {
      setGenError(err.message || "Generation failed.");
    } finally {
      setGenBusy(false);
    }
  };

  const exportChat = () => {
    if (!messages.length) return;
    const lines = messages
      .map((m) => `[${fmtTime(m.timestamp)}] ${m.role === "user" ? "You" : "SACHET AI"}: ${m.text}`)
      .join("\n\n");
    const url = URL.createObjectURL(new Blob([lines], { type: "text/plain;charset=utf-8" }));
    const a = document.createElement("a");
    a.href = url;
    a.download = `sachet-chat-${Date.now()}.txt`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
  };

  const currentLangObj = REGIONAL_LANGUAGES.find((l) => l.code === language) || REGIONAL_LANGUAGES[0];

  return (
    <div className="assistant flex h-[calc(100vh-64px)] overflow-hidden bg-transparent">
      {/* Sidebar history */}
      <aside className={`chat-sidebar ${sidebarOpen ? "chat-sidebar--open" : "chat-sidebar--collapsed"}`} aria-label="Chat history">
        <div className="chat-sidebar__head flex items-center justify-between p-3 border-b border-slate-200 dark:border-zinc-800">
          <h2 className="text-sm font-bold text-slate-800 dark:text-white flex items-center gap-1.5">
            <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" width="16" height="16"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M8.625 12a.375.375 0 11-.75 0 .375.375 0 01.75 0zm3.75 0a.375.375 0 11-.75 0 .375.375 0 01.75 0zm3.75 0a.375.375 0 11-.75 0 .375.375 0 01.75 0z" /></svg>
            Chats
          </h2>
          <div className="flex items-center gap-1">
            <Button variant="ghost" size="sm" onClick={createNewChat}>+ New</Button>
            <Tooltip content="Hide chat history sidebar">
              <button
                type="button"
                className="icon-toggle"
                style={{ width: 28, height: 28 }}
                onClick={toggleSidebar}
                aria-label="Collapse sidebar"
              >
                <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" width="14" height="14">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15.75 19.5L8.25 12l7.5-7.5" />
                </svg>
              </button>
            </Tooltip>
          </div>
        </div>

        <div className="chat-sidebar__list p-2 space-y-1 overflow-y-auto">
          {chats.map((chat) => (
            <div key={chat.id} className={`chat-item ${chat.id === activeChatId ? "active" : ""}`}>
              <button
                type="button"
                onClick={() => {
                  setActiveChatId(chat.id);
                  if (typeof window !== "undefined" && window.innerWidth < 992) setSidebarOpen(false);
                }}
                className="flex items-center gap-2 flex-1 min-w-0 bg-transparent border-0 p-0 text-left cursor-pointer"
              >
                <span className="chat-item__title text-xs truncate">{chat.title}</span>
                <span className="chat-item__time text-[10px] text-slate-400">{fmtTime(chat.createdAt)}</span>
              </button>
              <div className="chat-item__actions flex items-center">
                <button
                  className="icon-toggle"
                  style={{ width: 24, height: 24, border: "none" }}
                  onClick={() => {
                    const next = window.prompt("Chat title", chat.title);
                    if (next !== null) renameChat(chat.id, next);
                  }}
                  title="Rename"
                >
                  <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" width="12" height="12"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M16.862 4.487l1.687-1.688a1.875 1.875 0 112.652 2.652L10.582 16.07a4.5 4.5 0 01-1.897 1.13L6 18l.8-2.685a4.5 4.5 0 011.13-1.897l8.932-8.931z" /></svg>
                </button>
                <DeleteButton
                  size="xs"
                  onClick={() => deleteChat(chat.id)}
                  title="Delete chat"
                  aria-label={`Delete chat ${chat.title}`}
                />
              </div>
            </div>
          ))}
        </div>
      </aside>

      {/* Main chat surface */}
      <div className="chat-main flex-1 flex flex-col min-w-0 bg-zinc-950/75 backdrop-blur-xl rounded-2xl m-2 sm:m-3 border border-sky-500/25 shadow-2xl overflow-hidden relative">
        {voiceMode ? (
          <div className="flex-1 flex flex-col min-h-0 bg-gradient-to-b from-slate-950 via-zinc-950 to-black text-white relative overflow-y-auto animate-fadeIn">
            {/* Voice Mode Header */}
            <div className="px-4 py-3 border-b border-zinc-800/80 bg-zinc-900/60 backdrop-blur-md flex items-center justify-between gap-3 sticky top-0 z-10">
              <div className="flex items-center gap-2.5">
                <Tooltip content={sidebarOpen ? "Hide sidebar" : "Show chat history sidebar"}>
                  <button
                    type="button"
                    className={`icon-toggle transition-colors ${!sidebarOpen ? "border-purple-500/60 bg-purple-500/15 text-purple-400" : ""}`}
                    onClick={toggleSidebar}
                    aria-label={sidebarOpen ? "Hide chat history sidebar" : "Show chat history sidebar"}
                    title={sidebarOpen ? "Hide chat history sidebar" : "Show chat history sidebar"}
                  >
                    <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" width="16" height="16">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3.75 6.75h16.5M3.75 12h16.5m-16.5 5.25h16.5" />
                    </svg>
                  </button>
                </Tooltip>
                <div className="w-8 h-8 rounded-xl bg-purple-500/20 border border-purple-500/40 flex items-center justify-center text-purple-400">
                  <MicIcon />
                </div>
                <div>
                  <h2 className="text-sm sm:text-base font-bold text-white flex items-center gap-2 leading-tight">
                    SACHET Voice Assistant
                    <span className={`w-2 h-2 rounded-full ${
                      voiceState === "LISTENING" ? "bg-emerald-400 animate-ping" :
                      voiceState === "SPEAKING" ? "bg-pink-400 animate-pulse" :
                      voiceState === "THINKING" ? "bg-purple-400 animate-spin" :
                      "bg-zinc-500"
                    }`} />
                  </h2>
                  <span className="text-[11px] text-zinc-400">
                    {voiceState === "SLEEPING" && "🌙 Standby • Wake phrase: 'Hello Sachet'"}
                    {voiceState === "LISTENING" && (isFollowUp ? "🎙️ Hands-free listening for follow-up" : "🎙️ Listening...")}
                    {voiceState === "THINKING" && "⚡ Analyzing authorized documents..."}
                    {voiceState === "SPEAKING" && "🔊 Speaking aloud with regional voice"}
                  </span>
                </div>
              </div>

              <div className="flex items-center gap-2">
                {/* Detected language indicator */}
                {detectedLangDisplay && (
                  <span className="text-[11px] px-2 py-0.5 rounded-full bg-emerald-500/20 text-emerald-300 border border-emerald-500/40">
                    Auto-detected: {detectedLangDisplay}
                  </span>
                )}

                {/* Regional language dropdown */}
                <select
                  value={language}
                  onChange={(e) => {
                    setLanguage(e.target.value);
                    setDetectedLangDisplay(null);
                  }}
                  className="h-8 pl-2 pr-6 bg-zinc-800 border border-zinc-700 rounded-xl text-xs font-medium text-white focus:outline-none focus:ring-1 focus:ring-purple-400 cursor-pointer shadow-sm"
                  title="Switch language preference"
                >
                  {REGIONAL_LANGUAGES.map((l) => (
                    <option key={l.code} value={l.code}>
                      {l.flag} {l.label}
                    </option>
                  ))}
                </select>

                {/* Return to text chat */}
                <button
                  type="button"
                  onClick={() => {
                    cleanupMicAndAnalyser();
                    stopSpeaking();
                    setVoiceMode(false);
                  }}
                  className="h-8 px-3 rounded-xl bg-zinc-800 hover:bg-zinc-700 text-zinc-300 hover:text-white text-xs font-medium transition-colors flex items-center gap-1 cursor-pointer"
                  title="Return to standard chat interface"
                >
                  <span>✕ Exit</span>
                </button>
              </div>
            </div>

            {/* Voice Orb Central Stage */}
            <div className="flex-1 flex flex-col items-center justify-center p-4 sm:p-6 text-center max-w-lg mx-auto w-full my-auto">
              {/* Uiverse Glowing Chromatic Orb */}
              <div
                className="cursor-pointer active:scale-95 transition-transform"
                onClick={() => {
                  if (voiceState === "SLEEPING") {
                    startListening(false);
                  } else if (voiceState === "LISTENING") {
                    finishListeningAndProcess();
                  } else if (voiceState === "SPEAKING") {
                    stopSpeaking();
                    startListening(true);
                  }
                }}
                title={
                  voiceState === "SLEEPING" ? "Tap to speak" :
                  voiceState === "LISTENING" ? "Tap when done speaking" :
                  voiceState === "SPEAKING" ? "Tap to interrupt & ask follow-up" :
                  "Processing"
                }
              >
                <VoiceOrb
                  text={
                    voiceState === "LISTENING" ? "Listening" :
                    voiceState === "THINKING" ? "Thinking" :
                    voiceState === "SPEAKING" ? "Speaking" :
                    "Sachet"
                  }
                  mode={
                    voiceState === "LISTENING" ? "listening" :
                    voiceState === "THINKING" ? "generating" :
                    voiceState === "SPEAKING" ? "speaking" :
                    "sleeping"
                  }
                />
              </div>

              {/* Status and dynamic description */}
              <div className="mt-4 space-y-1">
                {voiceState === "SLEEPING" && (
                  <>
                    <div className="text-base font-semibold text-zinc-200 flex items-center justify-center gap-2">
                      <span>Assistant Standing By</span>
                    </div>
                    <p className="text-xs text-zinc-400 max-w-sm">
                      Say <strong className="text-emerald-400">"Hello Sachet"</strong> or tap the orb to ask your questions hands-free in your regional language.
                    </p>
                  </>
                )}

                {voiceState === "LISTENING" && (
                  <>
                    <div className="flex items-center justify-center gap-2 text-sm font-semibold text-emerald-400">
                      <span className="w-2.5 h-2.5 rounded-full bg-red-500 animate-ping" />
                      <span>
                        {isFollowUp ? "Listening for follow-up question..." : `Listening in ${currentLangObj.label}...`}
                      </span>
                      <span className="text-zinc-500 text-xs">({recordingSeconds}s)</span>
                    </div>
                    <p className="text-xs text-zinc-400">
                      Speak your question clearly. It automatically answers when you pause.
                    </p>
                    {/* Live audio visualizer waveform based on actual microphone audioLevel */}
                    <div className="flex items-center justify-center gap-1.5 h-8 my-2">
                      <span className="w-1.5 bg-emerald-400 rounded-full transition-all duration-75" style={{ height: `${Math.max(6, audioLevel * 32)}px` }} />
                      <span className="w-1.5 bg-teal-400 rounded-full transition-all duration-75" style={{ height: `${Math.max(10, audioLevel * 40)}px` }} />
                      <span className="w-1.5 bg-purple-400 rounded-full transition-all duration-75" style={{ height: `${Math.max(14, audioLevel * 48)}px` }} />
                      <span className="w-1.5 bg-pink-400 rounded-full transition-all duration-75" style={{ height: `${Math.max(10, audioLevel * 38)}px` }} />
                      <span className="w-1.5 bg-emerald-400 rounded-full transition-all duration-75" style={{ height: `${Math.max(6, audioLevel * 28)}px` }} />
                    </div>
                  </>
                )}

                {voiceState === "THINKING" && (
                  <>
                    <div className="flex items-center justify-center gap-2 text-sm font-semibold text-purple-300">
                      <span className="w-3.5 h-3.5 border-2 border-purple-400 border-t-transparent rounded-full animate-spin" />
                      <span>{instantAck || "Analyzing Authorized Documents..."}</span>
                    </div>
                    <p className="text-xs text-zinc-400">
                      Synthesizing grounded response with citations in your language...
                    </p>
                  </>
                )}

                {voiceState === "SPEAKING" && (
                  <>
                    <div className="flex items-center justify-center gap-2 text-sm font-semibold text-pink-400">
                      <span className="w-2.5 h-2.5 rounded-full bg-pink-500 animate-pulse" />
                      <span>SACHET AI Speaking Aloud</span>
                    </div>
                    <p className="text-xs text-zinc-400">
                      Streaming audio answer... It will automatically listen for your follow-up.
                    </p>
                    {/* Pulsing waveform while speaking */}
                    <div className="flex items-center justify-center gap-1.5 h-6 my-2">
                      <span className="w-1 bg-pink-400 rounded-full animate-bounce [animation-delay:-0.2s] h-5" />
                      <span className="w-1 bg-purple-400 rounded-full animate-bounce [animation-delay:-0.35s] h-6" />
                      <span className="w-1 bg-rose-400 rounded-full animate-bounce [animation-delay:-0.1s] h-4" />
                      <span className="w-1 bg-violet-400 rounded-full animate-bounce [animation-delay:-0.3s] h-6" />
                    </div>
                  </>
                )}
              </div>

              {micError && (
                <div className="mt-3 p-2.5 bg-red-950/60 border border-red-800 rounded-xl text-xs text-red-300 flex items-center justify-between gap-2 max-w-sm">
                  <span>⚠️ {micError}</span>
                  <button type="button" onClick={() => setMicError(null)} className="font-bold text-red-400 hover:text-white px-1">✕</button>
                </div>
              )}

              {/* Action Buttons */}
              <div className="mt-5 flex flex-wrap items-center justify-center gap-3">
                {voiceState === "SLEEPING" && (
                  <button
                    type="button"
                    onClick={() => startListening(false)}
                    className="h-12 px-8 bg-gradient-to-r from-emerald-600 to-teal-600 hover:from-emerald-500 hover:to-teal-500 text-white font-bold rounded-2xl shadow-lg shadow-emerald-950/60 hover:shadow-emerald-500/30 active:scale-95 transition-all flex items-center gap-2.5 cursor-pointer text-sm"
                  >
                    <MicIcon />
                    <span>Tap to Speak</span>
                  </button>
                )}

                {voiceState === "LISTENING" && (
                  <>
                    <button
                      type="button"
                      onClick={finishListeningAndProcess}
                      className="h-11 px-6 bg-emerald-600 hover:bg-emerald-500 text-white font-bold rounded-2xl shadow-lg shadow-emerald-900/50 active:scale-95 transition-all flex items-center gap-2 cursor-pointer text-sm"
                    >
                      <span>Done Speaking (Submit)</span>
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        cleanupMicAndAnalyser();
                        setVoiceState("SLEEPING");
                      }}
                      className="h-11 px-4 bg-zinc-800 hover:bg-zinc-700 text-zinc-300 rounded-2xl transition-all cursor-pointer text-xs"
                    >
                      Pause
                    </button>
                  </>
                )}

                {voiceState === "SPEAKING" && (
                  <>
                    <button
                      type="button"
                      onClick={() => {
                        stopSpeaking();
                        setVoiceState("SLEEPING");
                      }}
                      className="h-11 px-5 bg-amber-600 hover:bg-amber-500 text-white font-bold rounded-2xl shadow-lg shadow-amber-900/50 active:scale-95 transition-all flex items-center gap-2 cursor-pointer text-sm"
                    >
                      <StopIcon />
                      <span>Stop Voice</span>
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        stopSpeaking();
                        startListening(true);
                      }}
                      className="h-11 px-6 bg-emerald-600 hover:bg-emerald-500 text-white font-bold rounded-2xl shadow-lg shadow-emerald-900/50 active:scale-95 transition-all flex items-center gap-2 cursor-pointer text-sm"
                    >
                      <MicIcon />
                      <span>Ask Follow-up Now</span>
                    </button>
                  </>
                )}
              </div>

              {/* Latest Dialogue Transcript Card */}
              {lastVoiceTurn && (
                <div className="mt-5 w-full text-left bg-zinc-900/90 border border-zinc-800 rounded-2xl p-4 shadow-xl backdrop-blur-md animate-fadeIn">
                  <div className="mb-2">
                    <span className="text-[11px] font-semibold text-emerald-400 block mb-0.5">You asked:</span>
                    <p className="text-xs text-zinc-200 m-0 italic">"{lastVoiceTurn.userText}"</p>
                  </div>
                  <div className="pt-2 border-t border-zinc-800">
                    <div className="flex items-center justify-between gap-2 mb-1">
                      <span className="text-[11px] font-semibold text-purple-400">SACHET AI:</span>
                      <button
                        type="button"
                        onClick={() => playSpokenAnswer(lastVoiceTurn.botText, language)}
                        className="text-[11px] text-zinc-400 hover:text-white flex items-center gap-1 cursor-pointer"
                      >
                        🔊 Listen Again
                      </button>
                    </div>
                    <p className="text-xs text-zinc-300 m-0 whitespace-pre-wrap leading-relaxed max-h-36 overflow-y-auto">
                      {lastVoiceTurn.botText}
                    </p>
                  </div>
                </div>
              )}
            </div>

            {/* Voice Mode Bottom Bar */}
            <div className="px-4 py-2.5 border-t border-zinc-800/80 bg-zinc-900/60 backdrop-blur-md flex flex-wrap items-center justify-between gap-2 text-xs text-zinc-400 sticky bottom-0 z-10">
              <div className="flex items-center gap-1.5 flex-wrap">
                <span className="text-[11px] text-zinc-500">Quick languages:</span>
                {REGIONAL_LANGUAGES.slice(0, 6).map((l) => (
                  <button
                    key={l.code}
                    type="button"
                    onClick={() => {
                      setLanguage(l.code);
                      setDetectedLangDisplay(null);
                    }}
                    className={`px-2 py-0.5 rounded-md text-[11px] transition-colors cursor-pointer ${
                      language === l.code ? "bg-purple-600 text-white font-medium" : "bg-zinc-800 text-zinc-400 hover:text-white"
                    }`}
                  >
                    {l.flag} {l.label.split(" ")[0]}
                  </button>
                ))}
              </div>

              <button
                type="button"
                onClick={() => {
                  cleanupMicAndAnalyser();
                  stopSpeaking();
                  setVoiceMode(false);
                }}
                className="text-emerald-400 hover:text-emerald-300 font-medium cursor-pointer"
              >
                ← Return to text chat view
              </button>
            </div>
          </div>
        ) : (
          <>
            {/* Card Header matching template */}
            <div className="px-4 py-3 border-b border-slate-200 dark:border-zinc-700 bg-slate-50/80 dark:bg-zinc-800/60 backdrop-blur-sm flex items-center justify-between gap-3">
              <div className="flex items-center gap-2.5">
                <Tooltip content={sidebarOpen ? "Hide sidebar (expand chat)" : "Show chat history sidebar"}>
                  <button
                    type="button"
                    className={`icon-toggle transition-colors ${!sidebarOpen ? "border-emerald-500/60 bg-emerald-500/15 text-emerald-400" : ""}`}
                    onClick={toggleSidebar}
                    aria-label={sidebarOpen ? "Hide chat history sidebar" : "Show chat history sidebar"}
                    title={sidebarOpen ? "Hide chat history sidebar" : "Show chat history sidebar"}
                  >
                    <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" width="16" height="16">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3.75 6.75h16.5M3.75 12h16.5m-16.5 5.25h16.5" />
                    </svg>
                  </button>
                </Tooltip>
                <div className="w-8 h-8 rounded-xl bg-emerald-500/10 border border-emerald-500/30 flex items-center justify-center text-emerald-600">
                  {SHIELD}
                </div>
                <div>
                  <h2 className="text-base font-bold text-slate-800 dark:text-white leading-tight">
                    Chatbot Assistant
                  </h2>
                  <span className="text-[11px] text-slate-500 font-medium">SACHET AI Grounded Intelligence</span>
                </div>
              </div>

              <div className="flex items-center gap-2">
                {/* Online pill badge matching user template */}
                <div className="bg-emerald-500 text-white text-xs px-2.5 py-1 rounded-full font-medium inline-flex items-center gap-1.5 shadow-sm">
                  <span className="w-1.5 h-1.5 rounded-full bg-white animate-pulse" />
                  Online
                </div>

                {/* Voice Mode quick switch button */}
                <button
                  type="button"
                  onClick={() => {
                    setVoiceMode(true);
                    startListening(false);
                  }}
                  className="bg-purple-600 hover:bg-purple-500 text-white text-xs px-2.5 py-1 rounded-full font-medium inline-flex items-center gap-1.5 shadow-sm transition-all cursor-pointer active:scale-95"
                  title="Open Voice Assistant Mode"
                >
                  <MicIcon />
                  <span>Voice Mode</span>
                </button>

                {/* Document scope selector button */}
                <Button
                  variant={selectedDocs.length ? "primary" : "secondary"}
                  size="sm"
                  onClick={() => setScopeOpen((v) => !v)}
                  title={selectedDocs.length ? `${selectedDocs.length} document(s) in scope` : "Scope to specific documents"}
                >
                  {selectedDocs.length ? `${selectedDocs.length} Scoped` : "Scope"}
                </Button>

                {/* Assistant settings toggle */}
                <Tooltip content="Response settings (mode / instruction)">
                  <button
                    className={`icon-toggle ${showSettings ? "bg-slate-200 text-slate-800" : ""}`}
                    style={{ width: 34, height: 34 }}
                    onClick={() => setShowSettings((v) => !v)}
                    aria-label="Assistant settings"
                  >
                    <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" width="16" height="16"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.7} d="M10.343 3.94c.09-.542.56-.94 1.11-.94h1.093c.55 0 1.02.398 1.11.94l.213 1.281c.063.374.313.686.645.87.074.04.147.083.22.127.325.196.72.257 1.076.124l1.217-.456a1.125 1.125 0 011.37.49l1.296 2.247a1.125 1.125 0 01-.26 1.431l-1.003.827c-.293.24-.438.613-.43.992a7.723 7.723 0 010 .255c-.008.378.137.75.43.99l1.005.828c.424.35.534.955.26 1.43l-1.298 2.247a1.125 1.125 0 01-1.369.491l-1.217-.456c-.355-.133-.75-.072-1.076.124a6.57 6.57 0 01-.22.128c-.331.183-.581.495-.644.869l-.213 1.28c-.09.543-.56.94-1.11.94h-1.094c-.55 0-1.019-.398-1.11-.94l-.213-1.281c-.062-.374-.312-.686-.644-.87a6.52 6.52 0 01-.22-.127c-.325-.196-.72-.257-1.076-.124l-1.217.456a1.125 1.125 0 01-1.369-.49l-1.297-2.247a1.125 1.125 0 01.26-1.431l1.004-.827c.292-.24.437-.613.43-.991a6.932 6.932 0 010-.255c.007-.38-.138-.751-.43-.992l-1.004-.827a1.125 1.125 0 01-.26-1.43l1.297-2.247a1.125 1.125 0 011.37-.491l1.216.456c.356.133.751.072 1.076-.124.072-.044.146-.086.22-.128.332-.183.582-.495.644-.869l.214-1.281zM15 12a3 3 0 11-6 0 3 3 0 016 0z" /></svg>
                  </button>
                </Tooltip>

                {/* Export conversation */}
                <Tooltip content="Export conversation">
                  <button
                    className="icon-toggle"
                    style={{ width: 34, height: 34 }}
                    onClick={exportChat}
                    disabled={!messages.length}
                    aria-label="Export conversation"
                  >
                    <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" width="16" height="16"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.7} d="M3 16.5v2.25A2.25 2.25 0 005.25 21h13.5A2.25 2.25 0 0021 18.75V16.5M16.5 12L12 16.5m0 0L7.5 12m4.5 4.5V3" /></svg>
                  </button>
                </Tooltip>
              </div>
            </div>

            {/* Scope selector drawer */}
            {scopeOpen && (
              <div className="p-3 bg-emerald-50 dark:bg-zinc-800 border-b border-emerald-200 dark:border-zinc-700 animate-fadeIn">
                <div className="flex items-center justify-between mb-2">
                  <div>
                    <strong className="text-xs font-semibold text-emerald-900 dark:text-emerald-300">Document Scope Filter</strong>
                    <p className="text-[11px] text-slate-500 m-0">Restrict retrieval to specific authorized files.</p>
                  </div>
                  <Button variant="ghost" size="sm" onClick={() => setSelectedDocs([])} disabled={!selectedDocs.length}>Clear all</Button>
                </div>
                {docs.length === 0 ? (
                  <p className="text-xs text-slate-500">No authorized documents available.</p>
                ) : (
                  <div className="max-h-36 overflow-y-auto space-y-1">
                    {docs.map((d) => (
                      <label key={d.id} className="flex items-center gap-2 text-xs text-slate-700 dark:text-slate-300 cursor-pointer">
                        <input
                          type="checkbox"
                          className="rounded text-emerald-600 focus:ring-emerald-500"
                          checked={selectedDocs.includes(d.id)}
                          onChange={(e) => setSelectedDocs((prev) => (e.target.checked ? [...prev, d.id] : prev.filter((x) => x !== d.id)))}
                        />
                        <span className="truncate">{d.title || d.sourceFilename}</span>
                      </label>
                    ))}
                  </div>
                )}
              </div>
            )}

            {/* Settings drawer */}
            {showSettings && (
              <div className="p-3 bg-slate-50 dark:bg-zinc-800/90 border-b border-slate-200 dark:border-zinc-700 grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs animate-fadeIn">
                <Select
                  label="Response mode"
                  value={responseMode}
                  onChange={(e) => setResponseMode(e.target.value)}
                  options={MODES.map(([v, l, d]) => ({ value: v, label: `${l} — ${d}` }))}
                />
                {responseMode === "custom" && (
                  <Textarea
                    label="Custom instruction"
                    value={customPrompt}
                    onChange={(e) => setCustomPrompt(e.target.value)}
                    rows={2}
                    placeholder="e.g. Write as an executive briefing note"
                  />
                )}
              </div>
            )}

            {/* Messages display area id="chatDisplay" */}
            <div className="flex-1 p-3 sm:p-4 overflow-y-auto flex flex-col space-y-3" id="chatDisplay">
              {messages.length === 0 ? (
                <div className="my-auto text-center max-w-md mx-auto py-8">
                  <div className="w-14 h-14 rounded-2xl bg-emerald-50 dark:bg-zinc-800 text-emerald-600 mx-auto flex items-center justify-center mb-3 shadow-inner">
                    {SHIELD}
                  </div>
                  <h3 className="text-base font-bold text-slate-800 dark:text-white mb-1">
                    Ask your authorized documents
                  </h3>
                  <p className="text-xs text-slate-500 dark:text-slate-400 mb-4">
                    Ask in any regional language like Maithili, Punjabi, or Hindi. Speak with the mic or type below.
                  </p>
                  <div className="grid grid-cols-1 gap-2 text-left">
                    {EXAMPLES.slice(0, 3).map((ex) => (
                      <button
                        key={ex}
                        type="button"
                        onClick={() => sendMessage(ex)}
                        className="p-2.5 rounded-xl border border-slate-200 dark:border-zinc-700 bg-slate-50/60 dark:bg-zinc-800/60 hover:border-emerald-500 text-xs text-slate-700 dark:text-slate-300 text-left transition-all hover:bg-emerald-50/50"
                      >
                        💡 {ex}
                      </button>
                    ))}
                  </div>
                </div>
              ) : (
                messages.map((msg, index) => {
                  const isUser = msg.role === "user";
                  return (
                    <div
                      key={msg.id || `${msg.role}-${msg.timestamp}-${index}`}
                      className={`chat-message flex flex-col ${isUser ? "self-end items-end" : "self-start items-start"} max-w-[85%] sm:max-w-xl`}
                    >
                      <div
                        className={`rounded-2xl px-4 py-2.5 text-sm shadow-sm leading-relaxed ${
                          isUser
                            ? "bg-emerald-600 text-white rounded-tr-xs"
                            : "bg-slate-100 dark:bg-zinc-800 border border-slate-200 dark:border-zinc-700 text-slate-900 dark:text-slate-100 rounded-tl-xs"
                        }`}
                      >
                        {/* Message Header with actions for Assistant */}
                        {!isUser && (
                          <div className="flex items-center justify-between gap-3 pb-1.5 mb-1.5 border-b border-slate-200/80 dark:border-zinc-700/80 text-xs text-slate-500 dark:text-slate-300">
                            <span className="font-semibold text-emerald-700 dark:text-emerald-400 flex items-center gap-1">
                              SACHET AI
                            </span>
                            {!msg.streaming && !msg.error && (
                              <div className="flex items-center gap-1">
                                <ListenButton text={msg.text} initialLang={language} compact />
                                <Tooltip content="Copy answer">
                                  <button
                                    type="button"
                                    className="p-1 text-slate-500 dark:text-slate-300 hover:text-slate-800 dark:hover:text-white rounded transition-colors"
                                    onClick={() => copyMessage(msg.text)}
                                    aria-label="Copy"
                                  >
                                    📋
                                  </button>
                                </Tooltip>
                                <Tooltip content="Generate DOCX / Presentation">
                                  <button
                                    type="button"
                                    className="p-1 text-slate-500 dark:text-slate-300 hover:text-slate-800 dark:hover:text-white rounded transition-colors"
                                    onClick={() => {
                                      setGenSourceMsg(msg.text);
                                      setGenTitle("Generated briefing");
                                      setShowGenerate(true);
                                    }}
                                    aria-label="Generate document"
                                  >
                                    📄
                                  </button>
                                </Tooltip>
                              </div>
                            )}
                          </div>
                        )}

                        <div className="whitespace-pre-wrap">{msg.text}</div>
                        {msg.streaming && <span className="inline-block w-1.5 h-3.5 bg-emerald-500 ml-1 animate-pulse" />}

                        {/* Sources citations */}
                        {msg.sources?.length > 0 && !msg.streaming && (
                          <div className="mt-2.5 pt-2 border-t border-slate-200/60 dark:border-zinc-700/60 text-xs">
                            <span className="font-semibold text-slate-600 dark:text-slate-300 block mb-1">Sources:</span>
                            <div className="flex flex-wrap gap-1.5">
                              {msg.sources.map((s, j) => (
                                <Link
                                  key={`${s.documentId}-${s.page}-${j}`}
                                  to={`/documents/${s.documentId}/view?page=${s.page ?? 1}`}
                                  className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md bg-white dark:bg-zinc-800 border border-slate-200 dark:border-zinc-600 text-xs text-emerald-700 dark:text-emerald-300 hover:underline shadow-sm font-medium"
                                >
                                  📄 {s.documentTitle || s.documentId} {s.page != null && `(p.${s.page})`}
                                </Link>
                              ))}
                            </div>
                          </div>
                        )}

                        {msg.insufficient && !msg.streaming && (
                          <div className="mt-2 text-xs text-amber-700 dark:text-amber-300 bg-amber-50 dark:bg-amber-950/50 p-2.5 rounded-lg border border-amber-200 dark:border-amber-700/60 font-medium">
                            ⚠️ I could not find enough authorized source material to answer this reliably.
                          </div>
                        )}
                      </div>
                      <span className="text-[11px] text-slate-400 dark:text-slate-400 mt-1 px-1">
                        {fmtTime(msg.timestamp)}
                      </span>
                    </div>
                  );
                })
              )}

              {busy && !lastStreaming && (
                <div className="self-start bg-slate-100 dark:bg-zinc-800 p-3 rounded-2xl rounded-tl-xs shadow-sm">
                  <DotsLoader label="SACHET AI is researching authorized documents..." />
                </div>
              )}
              <div ref={messagesEndRef} />
            </div>

            {error && (
              <div className="px-4 py-2 bg-red-50 dark:bg-red-950/40 text-red-700 dark:text-red-300 text-xs border-t border-red-200 dark:border-red-900 flex items-center justify-between gap-2 animate-fadeIn">
                <div className="flex items-center gap-1.5 min-w-0">
                  <span className="shrink-0">⚠️</span>
                  <span className="truncate">{error}</span>
                </div>
                <button
                  type="button"
                  onClick={() => setError(null)}
                  className="text-red-500 hover:text-red-700 dark:hover:text-red-200 font-bold px-1.5 py-0.5 rounded cursor-pointer"
                  title="Dismiss"
                >
                  ✕
                </button>
              </div>
            )}

            {/* Card Composer at bottom */}
            <div className="px-3 py-2.5 border-t border-slate-200 dark:border-zinc-700 bg-slate-50/60 dark:bg-zinc-900/60 backdrop-blur-sm">
              {selectedDocs.length > 0 && (
                <div className="mb-2 flex items-center justify-between text-xs bg-emerald-50 dark:bg-zinc-800 text-emerald-800 dark:text-emerald-300 px-2.5 py-1 rounded-lg border border-emerald-200 dark:border-zinc-700">
                  <span>Scoped to {selectedDocs.length} document(s)</span>
                  <button type="button" onClick={() => setSelectedDocs([])} className="hover:underline font-semibold">
                    Clear
                  </button>
                </div>
              )}

              <form onSubmit={handleSubmit} className="flex items-center gap-2">
                {/* Regional Language Picker Dropdown */}
                <div className="relative">
                  <select
                    value={language}
                    onChange={(e) => {
                      setLanguage(e.target.value);
                      setDetectedLangDisplay(null);
                    }}
                    title="Select language: Auto detect automatically identifies regional language from speech"
                    className="h-10 pl-2 pr-7 bg-white dark:bg-zinc-800 border border-slate-300 dark:border-zinc-700 rounded-xl text-xs font-medium text-slate-700 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-emerald-500 cursor-pointer shadow-sm"
                  >
                    {REGIONAL_LANGUAGES.map((l) => (
                      <option key={l.code} value={l.code}>
                        {l.flag} {l.label}
                      </option>
                    ))}
                  </select>
                </div>

                {/* Prominent Mic Button: Opens Voice Mode & immediately starts listening */}
                <Tooltip content={`Open SACHET Voice Mode in ${currentLangObj.label}`}>
                  <button
                    type="button"
                    onClick={() => {
                      setVoiceMode(true);
                      startListening(false);
                    }}
                    disabled={busy}
                    className="h-10 w-10 flex items-center justify-center rounded-xl transition-all shadow-sm bg-white dark:bg-zinc-800 text-slate-700 dark:text-slate-200 hover:text-emerald-600 hover:border-emerald-500 border border-slate-300 dark:border-zinc-700 cursor-pointer active:scale-95"
                    aria-label="Microphone Voice Assistant"
                  >
                    <MicIcon />
                  </button>
                </Tooltip>

                {/* Input box */}
                <input
                  id="chatInput"
                  ref={inputRef}
                  type="text"
                  placeholder={`Type or click mic to speak in ${currentLangObj.label}...`}
                  value={input}
                  onChange={(e) => setInput(e.target.value)}
                  disabled={busy}
                  className="flex-1 h-10 px-3.5 bg-white dark:bg-zinc-800 border border-slate-300 dark:border-zinc-700 rounded-xl text-sm text-slate-900 dark:text-white placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500 shadow-sm transition-all"
                />

                {/* Send button */}
                {busy ? (
                  <button
                    type="button"
                    onClick={stopGeneration}
                    className="h-10 px-4 bg-red-600 hover:bg-red-700 text-white font-semibold rounded-xl text-sm shadow-md transition-all flex items-center justify-center gap-1.5"
                    title="Stop generating"
                  >
                    <span className="w-2.5 h-2.5 bg-white rounded-xs" />
                    <span>Stop</span>
                  </button>
                ) : (
                  <button
                    id="sendButton"
                    type="submit"
                    disabled={!input.trim()}
                    className="h-10 px-4 bg-emerald-600 hover:bg-emerald-700 disabled:opacity-50 disabled:cursor-not-allowed text-white font-semibold rounded-xl text-sm shadow-md shadow-emerald-950/20 active:scale-95 transition-all flex items-center justify-center gap-1.5"
                  >
                    <span>Send</span>
                    <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" width="14" height="14">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.2} d="M4.5 12h15m0 0l-6.75-6.75M19.5 12l-6.75 6.75" />
                    </svg>
                  </button>
                )}
              </form>
            </div>
          </>
        )}
      </div>

      {/* Generate document modal */}
      <Modal
        isOpen={showGenerate}
        title="Generate document artifact"
        onClose={() => { if (!genBusy) setShowGenerate(false); }}
        size="md"
        footer={
          <div className="flex gap-2 justify-end">
            <Button variant="secondary" onClick={() => setShowGenerate(false)} disabled={genBusy}>Cancel</Button>
            <Button variant="primary" onClick={handleGenerate} loading={genBusy} disabled={!genTitle.trim()}>Generate</Button>
          </div>
        }
      >
        {genError && <div className="mb-3"><Alert kind="error">{genError}</Alert></div>}
        <div className="space-y-3">
          <Input label="Title" required value={genTitle} onChange={(e) => setGenTitle(e.target.value)} placeholder="Executive Briefing" />
          <Select
            label="Format"
            value={genType}
            onChange={(e) => setGenType(e.target.value)}
            options={[{ value: "docx", label: "Word Document (.docx)" }, { value: "pptx", label: "PowerPoint Slides (.pptx)" }]}
          />
          {genType === "pptx" && (
            <Input label="Slides" type="number" min={1} max={30} value={genSlides} onChange={(e) => setGenSlides(Number(e.target.value) || 10)} />
          )}
        </div>
      </Modal>
    </div>
  );
}