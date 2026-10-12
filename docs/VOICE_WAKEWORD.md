# Voice / Wake-Word Subsystem — "Hello Sachet"

**Status: architecture complete and tested. Acoustic wake-word detection is NOT yet
active — it requires an optional dependency and a model download, and has NOT been
verified on real hardware.**

Read this page before assuming the wake word works.

---

## 1. What is implemented today

| Piece | State |
|---|---|
| `VoiceState` lifecycle enum | Done |
| `VoiceStateMachine` (thread-safe, rejects invalid edges) | Done, 27 tests |
| Configuration + startup validation | Done |
| Wake-word cooldown / duplicate-trigger prevention | Done, tested under 16-thread race |
| Inactivity timeout (default 120 s) + auto-sleep | Done |
| Manual wake / sleep / start controls (real backend calls) | Done |
| REST surface (`/voice/capabilities`, `/state`, `/wake`, `/sleep`, `/start`) | Done |
| Frontend panel driven by **real** backend state | Done |
| **Acoustic detection of "Hello Sachet"** | **NOT DONE — needs setup, see §4** |
| Speech-to-text provider | Not configured (`none`) |
| Text-to-speech provider | Not configured (`none`) |

## 2. Why detection is not active yet

`ConfiguredWakeWordDetector` validates the installation and reports an honest
status. It reports `AVAILABLE` only when **all** of these hold:

1. `SACHET_WAKE_WORD_ENABLED=true`
2. the model directory and `keywords.txt` exist
3. the configured phrase is **actually declared** in `keywords.txt`
4. the sherpa-onnx engine jar is on the classpath

Until the engine jar is added it reports `NOT_CONFIGURED` and `detect()` can never
fire. This is deliberate: a detector that reported "working" without a model would
let the UI display a false "listening" state.

## 3. Why sherpa-onnx and not the alternatives

| Engine | Verdict for this project |
|---|---|
| **openWakeWord** | Python-only (violates the Java-only backend rule). Ships 6 fixed phrases — `alexa`, `hey_mycroft`, `hey_jarvis`, `hey_rhasspy`, `timer`, `weather` — **none is "Hello Sachet"**. Pretrained weights are CC BY-NC-SA (non-commercial). Would also require synthetic-data training. |
| **Porcupine (Picovoice)** | Has a Java SDK and a custom-phrase console, but requires an AccessKey credential, the model/engine are proprietary, usage is metered per monthly active user, and Picovoice states there are **no personal or non-commercial plans**. Poor fit for a government-adjacent deployment. |
| **Vosk** | Java binding exists but it is full dictation ASR, not a keyword spotter — roughly an order of magnitude more compute for always-on use. |
| **sherpa-onnx `KeywordSpotter`** | **Selected.** Apache-2.0, no account, no credential, and *open-vocabulary*: `HELLO SACHET` is a line in a text keywords file. No retraining and no synthetic data collection needed. This is the only option found that detects the exact required phrase with no cloud calls and no licensing encumbrance. |

Note the distinction the brief asks for: sherpa-onnx performs **trained
keyword spotting**, not approximate ASR and not a hardcoded string comparison.
`ConfiguredWakeWordDetector` does compare text — but only to verify the phrase is
*declared in the keywords file*, never to decide a wake event from a transcript.

## 4. Installation steps (required to enable the wake word)

### 4.1 Add the optional engine dependency

sherpa-onnx is published on **JitPack**, not Maven Central. Add to `backend/pom.xml`
inside `<dependencies>`:

```xml
<dependency>
  <groupId>com.github.k2-fsa.sherpa-onnx</groupId>
  <artifactId>sherpa-onnx</artifactId>
  <version>v1.13.8</version>
</dependency>
```

Build with the JitPack repo enabled:

```bash
mvn -Dmaven.repo.remote=https://jitpack.io clean package
```

### 4.2 Download the model

English keyword-spotting model (Apache-2.0), ~19 MB total / ~5 MB int8:

- https://k2-fsa.github.io/sherpa/onnx/kws/pretrained_models.html
- Model id: `sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01`

Place the files under the configured model directory (default `./models/wake`):

```
models/wake/
  encoder.onnx        # acoustic model
  decoder.onnx        # if shipped separately
  keywords.txt        # keyword list
```

### 4.3 Declare the phrase

`keywords.txt` (tabs/spaces both work). The `#` prefix is sherpa's token marker:

```
# format: @TOKENIZED_PHRASE :BOOST :THRESHOLD
@HELLO SACHET :2.0 :0.25
@HEY SACHET :1.0 :0.35
```

The phrase must be **BPE-tokenized**. sherpa provides
`generate-kws-test-files.py` for this. The detector matches your configured
phrase against this file case-insensitively, ignoring underscores and the `▁`
word marker, so `"hello sachet"` matches `@HELLO SACHET`.

### 4.4 Enable

```bash
SACHET_WAKE_WORD_ENABLED=true
SACHET_WAKE_PHRASE=hello sachet
SACHET_WAKE_MODEL_DIR=./models/wake
```

Restart the backend. `GET /api/v1/voice/capabilities` will then report
`wakeWordAvailable: true`.

## 5. The one seam left to implement

`ConfiguredWakeWordDetector.detect()` currently returns "not fired" even when the
engine is present. That method is the single documented place to call the
engine's streaming decode (`acceptWaveform` → `decode` → `isEndpoint` →
`getKeyword`). It is intentionally left unimplemented rather than filled with
reflection code that has never been executed.

## 6. Privacy: what is local and what is not

**Local (never leaves the host):**
- Wake-word detection — the microphone is open while sleeping, but audio is scored
  on the server host and is never transmitted or logged.
- Document storage, embeddings, vector search, retrieval, and the extractive RAG
  fallback.

**External, and explicitly labelled:**
- **Browser dictation.** `SpeechRecognition` in Chrome/Edge streams microphone
  audio to the browser vendor's cloud ASR. This is an external transfer, is NOT
  covered by the server-side `sensitive-mode` block, and is called out in the UI
  with a warning. Use only when you accept that transfer.
- **Gemini text generation**, only if you set `SACHET_GEMINI_API_KEY` *and*
  explicitly disable sensitive mode. Not configured here.

## 7. Configuration reference

| Env var | Default | Meaning |
|---|---|---|
| `SACHET_WAKE_WORD_ENABLED` | `false` | Master switch |
| `SACHET_WAKE_PHRASE` | `hello sachet` | Phrase to detect |
| `SACHET_WAKE_THRESHOLD` | `0.5` | Trigger threshold, `(0,1)` exclusive |
| `SACHET_WAKE_COOLDOWN_MS` | `1500` | Suppresses repeat triggers from one utterance |
| `SACHET_INACTIVITY_TIMEOUT_SECONDS` | `120` | Auto-sleep delay, `5..3600` |
| `SACHET_LISTEN_TIMEOUT_SECONDS` | `15` | Max command capture, `1..300` |
| `SACHET_MICROPHONE_DEVICE` | `default` | Stable device name/id |
| `SACHET_STT_PROVIDER` | `none` | No provider wired |
| `SACHET_TTS_PROVIDER` | `none` | No provider wired |
| `SACHET_WAKE_MODEL_DIR` | `./models/wake` | Model directory |
| `SACHET_AUDIO_SAMPLE_RATE` | `16000` | Must be 8000 or 16000 |
| `SACHET_AUDIO_FRAME_MILLIS` | `100` | Frame size, `20..1000` |

Invalid values abort startup via `SachetPropertiesValidator` rather than running
misconfigured.

## 8. Language support — honest position

The selected KWS model is **English-only**. Maithili is therefore **not**
supported for wake-word detection.

Maithili *retrieval* works to a degree: the extractive RAG fallback returns your
original Devanagari text verbatim because it is language-agnostic. But:

- No Maithili TTS voice is installed on this machine (Windows reports 3 voices:
  `en-US David`, `en-GB Hazel`, `en-US Zira` — no Indic voices).
- The embedding model is multilingual but not Maithili-specialised; retrieval
  quality for Maithili is **unmeasured**.

Supporting Maithili wake-word detection needs a KWS model trained for it. That is
out of scope here and must not be assumed.

## 9. Manual hardware test procedure (NOT yet performed)

Requires a microphone and a physical voice. None of this has been executed.

1. Install the engine (§4). Confirm `GET /api/v1/voice/capabilities` reports
   `wakeWordAvailable: true` and `state: SLEEPING`.
2. Say **"Hello Sachet"** from ~2 m. Confirm the state moves `SLEEPING → WAKING`.
3. Confirm no microphone indicator or request occurs while sleeping that would
   indicate outbound audio; verify with a network capture that nothing is sent.
4. Have background conversation and television audio playing for several minutes;
   record any false activations.
5. Ask a question; confirm the transcript reaches the existing chat pipeline and
   the answer is grounded and cited.
6. Wait for the inactivity timeout; confirm the state returns to `SLEEPING`.
7. Say a sleep command or press "Go to sleep"; confirm the return to `SLEEPING`.
8. Unplug the microphone mid-session; confirm `ERROR` and recovery via "Start".
9. Measure wake latency and CPU on the target hardware.

## 10. Known limitations

- Acoustic detection is **not implemented** (see §5); the wake word cannot fire.
- No STT or TTS provider is configured, so the voice round trip is incomplete.
- Browser dictation is cloud-backed and is not the local wake-word path.
- Maithili and other regional languages are unsupported for wake-word detection.
- No audio capture device enumeration is implemented in the backend yet; the
  `microphone_device` setting is reserved.
- False-accept rate and wake latency are **unmeasured**; k2-fsa does not publish
  figures, so benchmark against your own audio.