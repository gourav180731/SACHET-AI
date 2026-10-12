# SACHET AI — Voice Architecture (Phase 0 Spec)

> Status: SPECIFICATION ONLY — NOT IMPLEMENTED.
> Stack: FastAPI `/voice` routers, VoiceService, STTGateway / TTSGateway / ModelGateway abstractions.
> Voice enforces identical AuthZ + ModelPolicy as text chat; no bypass path.

## 1. Pipeline Overview

- Batch path: `audio upload → AuthN/AuthZ → Policy check → STT → text query → permission-filtered RAG → LLM → text answer → TTS → audio reply`.
- Realtime path (target): `streaming mic ↔ VAD → incremental STT → partial RAG → streaming LLM tokens → incremental TTS → speaker`.
- Both paths share `security_context = {user_id, roles, clearance, department, project_scopes}` compiled server-side from session.
- Document bytes and RAG chunks never leave the permission-filtered path; STT/TTS providers receive only the minimum spans allowed by classification.
- Every voice turn is audited with `{who, when, lang, classification, stt_model, llm_model, tts_model, doc_ids}` — no full transcripts in audit log.

## 2. Endpoints and Modes

- `POST /voice/transcribe` (batch STT), `POST /voice/synthesize` (batch TTS), `POST /voice/query` (audio-in → grounded answer out), WebSocket `/voice/stream` (realtime, target NOT IMPLEMENTED).
- FastAPI handlers run AuthN → AuthZ → Security Policy before touching any gateway.
- Batch limits (configurable): max 10 min/audio, 50 MB, WAV/MP3/WebM; larger jobs become async jobs with `QUEUED → TRANSCRIBING → READY | FAILED`.
- Realtime is opt-in per session; interrupted turns cancel downstream LLM/TTS work immediately.

## 3. STT and Language Detection

- Interface: `STTGateway.transcribe(audio, hint_lang, classification, security_context) → {text, lang, segments[]}`.
- Language hint from client is advisory only; server-side `LanguageService.detect` confirms and tags `lang_confidence: high|low`.
- Code-switched speech keeps segment-level language tags; translation of transcript preserves segment provenance.
- STT output is treated as USER_INSTRUCTIONS (untrusted for prompt purposes, authorized as user identity for retrieval).

## 4. LLM Step (Same as Chat)

- Transcribed text enters the standard flow: Query Analysis → permission-filtered retrieval (pgvector HNSW + FTS pre-filtered by ACL) → ModelGateway → Output Policy.
- Voice queries never call the LLM with unfiltered context and never skip citation validation.
- Insufficient evidence rule applies identically: speak + display "I could not find sufficient supporting information in the authorized documents."

## 5. TTS and Streaming

- Interface: `TTSGateway.synthesize(text, lang, voice_id, classification, security_context) → audio bytes + marks`.
- Streaming: sentence-level TTS chunks; barge-in (user interruption) cancels playback and truncates the turn — partial audio is labeled truncated.
- Voice IDs are non-identifying defaults; no voice cloning without explicit SECURITY_ADMIN approval + audit (default DENY).
- TTS text is the post-policy final answer including classification label and INTERNAL KNOWLEDGE marker.

## 6. Provider Abstraction (Options, All Gated)

- Contracts: `STTGateway`, `TTSGateway` with `LocalSTT/TTS`, `BhashiniSTT/TTS`, `GeminiLiveVoice` adapters (all NOT IMPLEMENTED; selection is config).
- `Gemini Live` is a reference pattern for realtime session handling only (per AGENTS.md §11); no unrelated automation is adopted.
- `BHASHINI` is the preferred option for Indian-language STT/TTS where policy allows; local models preferred for RESTRICTED and above.
- Config keys: `voice.stt_provider`, `voice.tts_provider`, `voice.realtime_enabled`, `voice.max_audio_mb`, `voice.retention_days`.

## 7. Privacy and Security Rules

- ModelPolicy gates every external STT/TTS/LLM call by classification: default ALLOW external for PUBLIC/INTERNAL only, DENY for RESTRICTED/CONFIDENTIAL/SECRET (configurable, see `LLM_MODEL_POLICY.md`).
- Forbidden external call → BLOCK with explicit error naming the rule; never silent fallback to external, never downgrade to unfiltered local path.
- Raw audio retained only per `voice.retention_days` (default 7, configurable); transcripts inherit source classification retention.
- No audio, transcript, or chunk text is sent to web search or analytics; verbose sensitive logging OFF by default.
- Voice uses the same endpoint AuthZ matrix, export confirmations, and DLP redaction as text.

## 8. Testing and NOT IMPLEMENTED

- Tests (NOT IMPLEMENTED): AuthZ bypass via voice endpoint, external-call block per classification, interruption correctness, citation speech fidelity, 22-language STT/TTS matrix per registry.
- NOT IMPLEMENTED: realtime streaming loop, speaker diarization, wake-word, voice biometrics for AuthN, KMS-backed audio encryption.
