package ai.sachet.web;

import ai.sachet.domain.Classification;
import ai.sachet.security.SachetPrincipal;
import ai.sachet.service.ModelPolicy;
import ai.sachet.voice.SttProvider;
import ai.sachet.voice.TtsProvider;
import java.util.Base64;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Voice audio processing: text-to-speech (synthesis) and speech-to-text (transcription).
 *
 * <p><b>Security gate.</b> Both synthesis and transcription send audio or text derived from
 * user interactions to external model providers (e.g. Gemini). That is an external transfer,
 * so it is checked against {@link ModelPolicy} and authenticated via {@link SachetPrincipal}.
 * The request is refused with an explicit 403 rather than silently downgraded.
 */
@RestController
@RequestMapping("${sachet.api-prefix:/api/v1}/voice")
public class VoiceSpeakController {

  /** Bound so a runaway answer cannot be turned into a huge bill. */
  private static final int MAX_CHARS = 4000;

  private final TtsProvider tts;
  private final SttProvider stt;
  private final ModelPolicy policy;

  public VoiceSpeakController(TtsProvider tts, SttProvider stt, ModelPolicy policy) {
    this.tts = tts;
    this.stt = stt;
    this.policy = policy;
  }

  /** Record for speech synthesis request. */
  public record SpeakRequest(String text, String language) {}

  /** Record for JSON-based speech transcription request. */
  public record TranscribeJsonRequest(String audio, String mimeType, String language) {}

  @GetMapping("/tts/capabilities")
  public ResponseEntity<Map<String, Object>> ttsCapabilities() {
    return ResponseEntity.ok(Map.of(
        "available", tts.status() == TtsProvider.Status.AVAILABLE,
        "status", tts.status().name(),
        "reason", String.valueOf(tts.unavailableReason()),
        "model", tts.modelId(),
        "languages", tts.supportedLanguages()));
  }

  @GetMapping("/stt/capabilities")
  public ResponseEntity<Map<String, Object>> sttCapabilities() {
    return ResponseEntity.ok(Map.of(
        "available", stt.status() == SttProvider.Status.AVAILABLE,
        "status", stt.status().name(),
        "reason", String.valueOf(stt.unavailableReason()),
        "model", stt.modelId(),
        "languages", stt.supportedLanguages()));
  }

  @PostMapping(value = "/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<?> transcribeMultipart(
      @AuthenticationPrincipal SachetPrincipal principal,
      @RequestParam("audio") MultipartFile file,
      @RequestParam(value = "language", required = false) String language) {

    if (stt.status() != SttProvider.Status.AVAILABLE) {
      return ResponseEntity.status(503).body(Map.of(
          "error", Map.of("code", "STT_UNAVAILABLE", "message",
              stt.unavailableReason() == null ? "Speech transcription is unavailable." : stt.unavailableReason())));
    }

    if (file == null || file.isEmpty()) {
      return ResponseEntity.badRequest().body(Map.of(
          "error", Map.of("code", "EMPTY_AUDIO", "message", "No audio recorded.")));
    }

    Classification worst = Classification.INTERNAL;
    try {
      policy.requireExternalAllowed(worst);
    } catch (ModelPolicy.ExternalTransferBlockedException e) {
      return ResponseEntity.status(403).body(Map.of(
          "error", Map.of("code", "EXTERNAL_TRANSFER_BLOCKED", "message",
              "Audio transcription with Gemini is blocked while sensitive mode is enabled."
                  + " Enable external models only if you accept external voice processing.")));
    }

    try {
      byte[] bytes = file.getBytes();
      String mimeType = file.getContentType();
      SttProvider.Transcription res = stt.transcribe(bytes, mimeType, language);
      return ResponseEntity.ok(Map.of(
          "text", res.text(),
          "language", res.detectedLanguage()));
    } catch (Exception e) {
      return ResponseEntity.status(500).body(Map.of(
          "error", Map.of("code", "TRANSCRIPTION_FAILED", "message", e.getMessage())));
    }
  }

  @PostMapping(value = "/transcribe", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> transcribeJson(
      @AuthenticationPrincipal SachetPrincipal principal,
      @RequestBody TranscribeJsonRequest req) {

    if (stt.status() != SttProvider.Status.AVAILABLE) {
      return ResponseEntity.status(503).body(Map.of(
          "error", Map.of("code", "STT_UNAVAILABLE", "message",
              stt.unavailableReason() == null ? "Speech transcription is unavailable." : stt.unavailableReason())));
    }

    if (req.audio() == null || req.audio().isBlank()) {
      return ResponseEntity.badRequest().body(Map.of(
          "error", Map.of("code", "EMPTY_AUDIO", "message", "No audio recorded.")));
    }

    Classification worst = Classification.INTERNAL;
    try {
      policy.requireExternalAllowed(worst);
    } catch (ModelPolicy.ExternalTransferBlockedException e) {
      return ResponseEntity.status(403).body(Map.of(
          "error", Map.of("code", "EXTERNAL_TRANSFER_BLOCKED", "message",
              "Audio transcription with Gemini is blocked while sensitive mode is enabled."
                  + " Enable external models only if you accept external voice processing.")));
    }

    try {
      byte[] bytes = Base64.getDecoder().decode(req.audio());
      SttProvider.Transcription res = stt.transcribe(bytes, req.mimeType(), req.language());
      return ResponseEntity.ok(Map.of(
          "text", res.text(),
          "language", res.detectedLanguage()));
    } catch (Exception e) {
      return ResponseEntity.status(500).body(Map.of(
          "error", Map.of("code", "TRANSCRIPTION_FAILED", "message", e.getMessage())));
    }
  }

  @PostMapping("/speak")
  public ResponseEntity<?> speak(@AuthenticationPrincipal SachetPrincipal principal,
      @RequestBody SpeakRequest req) {

    if (tts.status() != TtsProvider.Status.AVAILABLE) {
      return ResponseEntity.status(503).body(Map.of(
          "error", Map.of("code", "TTS_UNAVAILABLE", "message",
              tts.unavailableReason() == null ? "Speech synthesis is unavailable." : tts.unavailableReason())));
    }

    String text = req.text() == null ? "" : req.text().trim();
    if (text.isEmpty()) {
      return ResponseEntity.badRequest().body(Map.of(
          "error", Map.of("code", "EMPTY_TEXT", "message", "Nothing to speak.")));
    }
    if (text.length() > MAX_CHARS) {
      text = text.substring(0, MAX_CHARS);
    }

    String lang = req.language();
    if (lang != null && !lang.isBlank()) {
      String base = lang.split("-")[0].toLowerCase();
      boolean supported = tts.supportedLanguages().stream()
          .anyMatch(l -> l.equalsIgnoreCase(base));
      if (!supported) {
        return ResponseEntity.badRequest().body(Map.of(
            "error", Map.of("code", "LANGUAGE_UNSUPPORTED",
                "message", "This voice provider cannot render '" + lang + "'.")));
      }
    }

    // External-transfer gate. Synthesising an answer derived from documents is
    // document-derived egress, so it obeys the same policy as generation.
    Classification worst = Classification.INTERNAL;
    try {
      policy.requireExternalAllowed(worst);
    } catch (ModelPolicy.ExternalTransferBlockedException e) {
      return ResponseEntity.status(403).body(Map.of(
          "error", Map.of("code", "EXTERNAL_TRANSFER_BLOCKED", "message",
              "Speech synthesis is blocked while sensitive mode is enabled, because the"
                  + " answer text is derived from your documents. Enable external models"
                  + " only if you accept that transfer.")));
    }

    TtsProvider.Audio audio = tts.synthesize(text, lang);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(audio.mimeType()))
        .body(audio.bytes());
  }
}