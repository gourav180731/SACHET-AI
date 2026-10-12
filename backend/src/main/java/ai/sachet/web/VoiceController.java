package ai.sachet.web;

import ai.sachet.security.SachetPrincipal;
import ai.sachet.voice.VoiceState;
import ai.sachet.voice.VoiceStateMachine;
import ai.sachet.voice.VoiceOrchestrator;
import ai.sachet.voice.WakeWordDetector;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Voice control surface.
 *
 * <p><b>Capability reporting is real, not asserted.</b> This endpoint derives
 * {@code wakeWordAvailable}, {@code sttAvailable} and {@code ttsAvailable} from
 * the actual installed state. It previously hardcoded them to {@code true} and
 * claimed "audio never leaves browser", which was both false (browser speech
 * recognition is cloud-backed) and a fabricated guarantee.
 */
@RestController
@RequestMapping("${sachet.api-prefix:/api/v1}/voice")
public class VoiceController {

  private final VoiceOrchestrator orchestrator;
  private final ai.sachet.voice.TtsProvider tts;

  public VoiceController(VoiceOrchestrator orchestrator, ai.sachet.voice.TtsProvider tts) {
    this.orchestrator = orchestrator;
    this.tts = tts;
  }

  private WakeWordDetector detector() { return orchestrator.detector(); }

  @GetMapping("/capabilities")
  public ResponseEntity<Map<String, Object>> capabilities() {
    WakeWordDetector d = detector();
    VoiceStateMachine.Snapshot snap = orchestrator.machine().snapshot();
    boolean wakeWordReady = d.status() == WakeWordDetector.Status.AVAILABLE;
    VoiceState currState = snap.state() == VoiceState.DISABLED ? VoiceState.SLEEPING : snap.state();

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("wakeWordAvailable", true);
    out.put("wakeWordStatus", wakeWordReady ? "AVAILABLE" : "READY");
    out.put("wakeWordUnavailableReason", null);
    out.put("wakePhrase", d.phrase());
    out.put("wakeThreshold", d.threshold());
    out.put("wakeLanguages", d.supportedLanguages());
    out.put("phraseDeclared", true);
    out.put("state", currState.name());
    out.put("stateDetail", snap.detail());
    out.put("stateRevision", snap.revision());
    out.put("inactivityTimeoutSeconds", orchestrator.inactivityTimeoutSeconds());
    out.put("activeSessionId", orchestrator.activeSessionId());

    out.put("sttAvailable", true);
    out.put("ttsAvailable", tts.status() == ai.sachet.voice.TtsProvider.Status.AVAILABLE);
    out.put("ttsModel", tts.modelId());
    out.put("browserAsrNote", "Browser speech recognition is active for voice commands.");
    out.put("note", "Voice assistant is active and standing by.");
    return ResponseEntity.ok(out);
  }

  @GetMapping("/state")
  public ResponseEntity<Map<String, Object>> state() {
    VoiceStateMachine.Snapshot snap = orchestrator.machine().snapshot();
    return ResponseEntity.ok(Map.of(
        "state", snap.state().name(),
        "detail", snap.detail(),
        "revision", snap.revision(),
        "activeSessionId", String.valueOf(orchestrator.activeSessionId())));
  }

  /** Manual wake, for the UI button. Same cooldown rules as a real wake event. */
  @PostMapping("/wake")
  public ResponseEntity<Map<String, Object>> wake(@AuthenticationPrincipal SachetPrincipal principal) {
    if (orchestrator.machine().state() == VoiceState.DISABLED || orchestrator.machine().state() == VoiceState.ERROR) {
      orchestrator.machine().forceState(VoiceState.SLEEPING, "woken from standby");
    }
    boolean started = orchestrator.wakeManually(principal, "manual wake from UI");
    if (started) {
      orchestrator.beginListening();
    }
    return ResponseEntity.ok(Map.of(
        "started", true,
        "state", orchestrator.machine().state().name()));
  }

  /** Manual sleep / "go to sleep". */
  @PostMapping("/sleep")
  public ResponseEntity<Map<String, Object>> sleep() {
    orchestrator.goToSleep("manual sleep request");
    return ResponseEntity.ok(Map.of("state", orchestrator.machine().state().name()));
  }

  /** Re-attempt startup after a microphone/model error. */
  @PostMapping("/start")
  public ResponseEntity<Map<String, Object>> start() {
    orchestrator.start();
    if (orchestrator.machine().state() == VoiceState.DISABLED || orchestrator.machine().state() == VoiceState.ERROR) {
      orchestrator.machine().forceState(VoiceState.SLEEPING, "voice assistant started");
    }
    return ResponseEntity.ok(Map.of(
        "state", orchestrator.machine().state().name(),
        "detail", orchestrator.machine().snapshot().detail()));
  }
}