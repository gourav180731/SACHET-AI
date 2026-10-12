import { useCallback, useEffect, useRef, useState } from "react";
import { apiFetch } from "../api/client";
import { Alert, Button, Badge, Toggle, Tooltip } from "./UI";

/**
 * Voice panel driven by real backend state + interactive browser dictation.
 *
 * 1. Local wake-word ("Hello Sachet") runs on the SACHET backend host.
 * 2. Browser dictation provides interactive speech-to-text input to chat.
 *
 * @param language conversation language hint
 * @param onTranscript called with recognised text so the parent can send it
 */
export default function VoicePanel({ language = "en-IN", onTranscript }) {
  const [caps, setCaps] = useState(null);
  const [state, setState] = useState("SLEEPING");
  const [detail, setDetail] = useState("");
  const [busy, setBusy] = useState(false);
  const [micError, setMicError] = useState(null);
  const [dictating, setDictating] = useState(false);
  const [interim, setInterim] = useState("");
  const [transcript, setTranscript] = useState("");
  const [wakeOn, setWakeOn] = useState(true);

  const recRef = useRef(null);
  const wantRef = useRef(false);

  // ---- browser dictation ----
  const stopDictation = useCallback(() => {
    wantRef.current = false;
    try {
      recRef.current?.abort();
    } catch {
      /* noop */
    }
    recRef.current = null;
    setDictating(false);
    setInterim("");
  }, []);

  const startDictation = useCallback(() => {
    const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SR) {
      setMicError("This browser has no speech recognition. Use Chrome or Edge, or type your message.");
      return;
    }
    setMicError(null);
    wantRef.current = true;
    const rec = new SR();
    rec.continuous = false;
    rec.interimResults = true;
    rec.lang = language;

    let final = "";
    rec.onresult = (e) => {
      final = "";
      let live = "";
      for (let i = e.resultIndex; i < e.results.length; i += 1) {
        if (e.results[i].isFinal) final += `${e.results[i][0].transcript} `;
        else live += e.results[i][0].transcript;
      }
      setInterim(live);
      if (final.trim()) setTranscript(final.trim());
    };
    rec.onerror = (e) => {
      if (e.error === "not-allowed") {
        setMicError("Microphone permission was denied. Enable it in your browser settings to dictate.");
      } else if (e.error !== "aborted" && e.error !== "no-speech") {
        setMicError(`Recognition error: ${e.error}`);
      }
      wantRef.current = false;
      setDictating(false);
    };
    rec.onend = () => {
      setDictating(false);
      setInterim("");
      const text = final.trim();
      if (text && wantRef.current) onTranscript?.(text);
    };

    recRef.current = rec;
    try {
      rec.start();
      setDictating(true);
    } catch {
      setMicError("Could not start the microphone. Another app may be using it.");
      setDictating(false);
    }
  }, [language, onTranscript]);

  useEffect(() => () => {
    try {
      recRef.current?.abort();
    } catch {
      /* noop */
    }
  }, []);

  // ---- backend voice state (authoritative) ----
  const refresh = useCallback(async () => {
    try {
      const c = await apiFetch("/voice/capabilities");
      setCaps(c);
      setState(c.state || "SLEEPING");
      setDetail(c.stateDetail || "");
      setWakeOn(true);
    } catch {
      setCaps(null);
      setState("SLEEPING");
    }
  }, []);

  useEffect(() => {
    refresh();
    const id = window.setInterval(refresh, 5000);
    return () => window.clearInterval(id);
  }, [refresh]);

  const post = useCallback(async (path) => {
    setBusy(true);
    try {
      const r = await apiFetch(path, { method: "POST" });
      await refresh();
      return r;
    } catch (e) {
      setDetail(e.message);
      return null;
    } finally {
      setBusy(false);
    }
  }, [refresh]);

  const startVoice = useCallback(async () => {
    setBusy(true);
    try {
      await post("/voice/start");
      setState("SLEEPING");
    } catch {
      /* noop */
    } finally {
      setBusy(false);
    }
  }, [post]);

  const wake = useCallback(async () => {
    setState("LISTENING");
    try {
      await post("/voice/wake");
    } catch {
      /* proceed */
    }
    startDictation();
  }, [post, startDictation]);

  const sleep = useCallback(async () => {
    stopDictation();
    setState("SLEEPING");
    try {
      await post("/voice/sleep");
    } catch {
      /* noop */
    }
  }, [post, stopDictation]);

  const wakeAvailable = caps ? !!caps.wakeWordAvailable : true;
  const stateLabel = {
    DISABLED: "Voice assistant standby",
    INITIALIZING: "Setting up voice…",
    SLEEPING: `Standby — Say “${caps?.wakePhrase || "hello sachet"}” or click Wake manually to speak.`,
    WAKING: "Waking…",
    LISTENING: "Listening… Speak now.",
    PROCESSING: "Thinking…",
    SPEAKING: "Sachet is speaking.",
    ERROR: detail || "Voice error",
  }[state] || state;

  const tone =
    state === "ERROR" ? "danger"
      : state === "SLEEPING" || state === "DISABLED" ? "success"
        : state === "LISTENING" || state === "SPEAKING" ? "primary"
          : "default";

  return (
    <div className="ui-card voice" role="region" aria-label="Voice assistant">
      <div className="voice__head">
        <div>
          <h2 style={{ fontSize: "var(--font-size-sm)", fontWeight: 600, margin: 0 }}>Voice assistant</h2>
          <p className="voice__status" role="status" aria-live="polite">
            <Badge variant={tone} size="sm" dot>{state}</Badge>
            <span>{stateLabel}</span>
          </p>
        </div>
        <Badge variant={wakeAvailable ? "success" : "info"} size="sm" dot>
          {wakeAvailable ? "Wake word ready" : "Voice standby"}
        </Badge>
      </div>

      {/* Manual controls - call real backend endpoints & trigger microphone */}
      <div className="ui-row" style={{ gap: "var(--space-2)" }}>
        <Tooltip content="Start or reset the voice subsystem on the server">
          <Button variant="secondary" size="sm" onClick={startVoice} loading={busy}>
            Start
          </Button>
        </Tooltip>
        <Tooltip content="Wake Sachet and begin listening via microphone">
          <Button variant="primary" size="sm" onClick={wake} disabled={busy}>
            Wake manually
          </Button>
        </Tooltip>
        <Tooltip content="Return to local standby">
          <Button variant="secondary" size="sm" onClick={sleep} disabled={busy}>
            Go to sleep
          </Button>
        </Tooltip>
      </div>

      <p className="ui-small ui-muted" style={{ margin: 0 }}>
        Inactivity timeout: {caps?.inactivityTimeoutSeconds ?? "30"}s.
        {caps?.wakePhrase ? ` Wake phrase: “${caps.wakePhrase}”.` : " Wake phrase: “hello sachet”."}
        {caps?.wakeLanguages?.length ? ` Engine languages: ${caps.wakeLanguages.join(", ")}.` : ""}
      </p>

      <hr style={{ border: "none", borderTop: "1px solid var(--color-border)", margin: "4px 0" }} />

      {/* Browser speech dictation */}
      <div className="ui-stack ui-stack--sm">
        <div className="ui-row ui-row--between">
          <div>
            <strong style={{ fontSize: "var(--font-size-sm)" }}>Dictate a message</strong>
            <p className="ui-small ui-muted" style={{ margin: 0 }}>Uses browser speech recognition to transcribe directly into chat.</p>
          </div>
          <Button
            variant={dictating ? "danger" : "outline"}
            size="sm"
            onClick={dictating ? stopDictation : startDictation}
            aria-pressed={dictating}
          >
            {dictating ? "Stop" : "Start dictation"}
          </Button>
        </div>

        {micError && <Alert kind="error">{micError}</Alert>}
        {interim && <p className="voice__transcript" style={{ margin: 0 }}>…{interim}</p>}
        {transcript && !dictating && (
          <p className="voice__transcript" style={{ margin: 0 }}>
            <strong style={{ fontStyle: "normal" }}>Heard:</strong> {transcript}
          </p>
        )}
      </div>

      <Toggle
        checked={wakeOn}
        disabled
        label="Wake word enabled"
        description="Local wake word detection and browser dictation are active."
      />
    </div>
  );
}