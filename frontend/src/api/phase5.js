import { apiFetch } from "./client";

export const auditApi = {
  list: (page = 0, size = 20) => apiFetch(`/audit?page=${page}&size=${size}`),
};

export const studioApi = {
  run: (operation, payload = {}) =>
    apiFetch(`/studio/${operation}`, { method: "POST", body: payload }),
};

export const voiceApi = {
  capabilities: () => apiFetch(`/voice/capabilities`),
  state: () => apiFetch(`/voice/state`),
  wake: () => apiFetch(`/voice/wake`, { method: "POST" }),
  sleep: () => apiFetch(`/voice/sleep`, { method: "POST" }),
  start: () => apiFetch(`/voice/start`, { method: "POST" }),
  ttsCapabilities: () => apiFetch(`/voice/tts/capabilities`),
  sttCapabilities: () => apiFetch(`/voice/stt/capabilities`),
  speak: (text, language) =>
    apiFetch("/voice/speak", { method: "POST", body: { text, language } }),
  transcribe: async (audioBlob, language) => {
    const formData = new FormData();
    formData.append("audio", audioBlob, "recording.webm");
    if (language) formData.append("language", language);
    return apiFetch("/voice/transcribe", {
      method: "POST",
      formData,
    });
  },
};

/**
 * Fetch rendered speech as a WAV blob.
 *
 * Uses a raw fetch rather than apiFetch because apiFetch parses JSON, and this
 * endpoint returns audio bytes. The Authorization header is attached the same
 * way, so authorization is unchanged.
 */
export async function fetchSpeech(text, language, token, signal) {
  const base = import.meta.env.VITE_API_BASE || "http://localhost:8000/api/v1";
  const res = await fetch(`${base}/voice/speak`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify({ text, language }),
    signal,
  });

  if (!res.ok) {
    let message = "Speech synthesis failed.";
    try {
      const j = await res.json();
      message = j?.error?.message || message;
    } catch {
      /* keep default message */
    }
    const err = new Error(message);
    err.status = res.status;
    throw err;
  }
  return res.blob();
}

export const artifactApi = {
  list: (page = 0, size = 20) => apiFetch(`/artifacts?page=${page}&size=${size}`),
  get: (id) => apiFetch(`/artifacts/${id}`),
  remove: (id) => apiFetch(`/artifacts/${id}`, { method: "DELETE" }),
};

export const generationApi = {
  docx: (payload) => apiFetch(`/studio/generate/docx`, { method: "POST", body: payload }),
  pptx: (payload) => apiFetch(`/studio/generate/pptx`, { method: "POST", body: payload }),
};
