package ai.sachet.voice;

import ai.sachet.config.SachetProperties;
import ai.sachet.security.SachetPrincipal;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Owns the voice lifecycle: wake events, session start/end, inactivity sleep and
 * shutdown.
 *
 * <p>Concurrency decisions worth knowing:
 * <ul>
 *   <li>Wake events are deduplicated with {@link AtomicLong} {@code lastWakeAtNs}
 *       rather than a boolean flag, so two audio threads firing simultaneously
 *       cannot both pass the cooldown check.</li>
 *   <li>The inactivity timer is a single reusable scheduled task that is
 *       <i>cancelled and rescheduled</i> on each reset rather than a new task per
 *       reset. Long conversations would otherwise pile up scheduled work and
 *       eventually leak tasks.</li>
 *   <li>The machine is locked around the cooldown test and the transition so that
 *       check-and-act is atomic.</li>
 * </ul>
 *
 * <p>Privacy: this class never buffers, logs or transmits audio. It only ever
 * sees a boolean "a wake event occurred", which the detector derived locally.
 */
@Service
public class VoiceOrchestrator {

  private static final Logger log = LoggerFactory.getLogger(VoiceOrchestrator.class);

  private final SachetProperties props;
  private final VoiceStateMachine machine;
  private final WakeWordDetector detector;
  private final ScheduledExecutorService scheduler;

  private final AtomicLong lastWakeAtNs = new AtomicLong(Long.MIN_VALUE);
  private final AtomicLong lastActivityNs = new AtomicLong(System.nanoTime());
  private final AtomicReference<String> activeSessionId = new AtomicReference<>(null);
  private final AtomicReference<ScheduledFuture<?>> inactivityTimer = new AtomicReference<>(null);
  private final Object wakeLock = new Object();

  public VoiceOrchestrator(SachetProperties props, WakeWordDetector detector) {
    this.props = props;
    this.detector = detector;
    this.machine = new VoiceStateMachine(
        props.isWakeWordEnabled() ? VoiceState.SLEEPING : VoiceState.DISABLED);
    this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "sachet-voice-timer");
      t.setDaemon(true);
      return t;
    });
  }

  public VoiceStateMachine machine() { return machine; }
  public WakeWordDetector detector() { return detector; }
  public String activeSessionId() { return activeSessionId.get(); }

  public int inactivityTimeoutSeconds() { return props.getInactivityTimeoutSeconds(); }

  /** Visible for tests: has the timer executor been shut down? */
  boolean isSchedulerShutdown() { return scheduler.isShutdown(); }

  /** Visible for tests: is an inactivity timer currently pending? */
  boolean hasPendingInactivityTimer() { return inactivityTimer.get() != null; }

  /** Loads the detector and moves to SLEEPING when genuinely ready. */
  public void start() {
    if (!props.isWakeWordEnabled()) {
      machine.forceState(VoiceState.DISABLED, "disabled by configuration");
      return;
    }
    machine.forceState(VoiceState.INITIALIZING, "opening audio subsystem");
    try {
      detector.initialize();
      if (detector.status() == WakeWordDetector.Status.AVAILABLE) {
        machine.transitionTo(VoiceState.SLEEPING, "wake-word detection active");
        armInactivityTimer();
      } else {
        machine.forceState(VoiceState.ERROR, detector.unavailableReason());
        log.warn("Voice subsystem not available: {}", detector.unavailableReason());
      }
    } catch (RuntimeException e) {
      machine.forceState(VoiceState.ERROR, "startup failed: " + e.getMessage());
      log.error("Voice subsystem startup failed", e);
    }
  }

  public void stop() {
    cancelInactivityTimer();
    activeSessionId.set(null);
    try {
      detector.close();
    } catch (RuntimeException e) {
      log.warn("Detector close failed: {}", e.toString());
    }
    machine.forceState(VoiceState.DISABLED, "stopped");
  }

  /**
   * Feed a local detection result from the audio worker.
   *
   * @return true when the wake was accepted (a session was started)
   */
  public boolean onWakeDetected(SachetPrincipal principal) {
    // Defence in depth: never honour a wake event unless the detector is genuinely
    // AVAILABLE. A stubbed or mis-configured detector must not be able to open a
    // session, otherwise the UI could show a false "listening" state.
    if (detector.status() != WakeWordDetector.Status.AVAILABLE) {
      return false;
    }
    VoiceState s = machine.state();
    if (s != VoiceState.SLEEPING) {
      // Suppress triggers while listening/processing/speaking so the assistant
      // cannot wake on its own TTS output or mid-conversation.
      return false;
    }
    long now = System.nanoTime();
    long cooldownNs = Duration.ofMillis(props.getWakeCooldownMs()).toNanos();
    synchronized (wakeLock) {
      long last = lastWakeAtNs.get();
      if (last != Long.MIN_VALUE && now - last < cooldownNs) {
        return false;
      }
      lastWakeAtNs.set(now);
      try {
        machine.transitionTo(VoiceState.WAKING, "wake phrase detected");
      } catch (VoiceStateMachine.InvalidTransitionException e) {
        return false;
      }
    }
    String sessionId = "vs-" + Long.toHexString(System.nanoTime());
    activeSessionId.set(sessionId);
    log.info("Wake word '{}' detected by {} -> session {}",
        props.getWakePhrase(), principal.getUserId(), sessionId);
    return true;
  }

  /** Manual wake, for the UI button. Bypasses the audio path, not the cooldown. */
  public boolean wakeManually(SachetPrincipal principal, String reason) {
    VoiceState s = machine.state();
    if (s != VoiceState.SLEEPING) {
      if (s == VoiceState.DISABLED || s == VoiceState.INITIALIZING || s == VoiceState.ERROR) {
        machine.forceState(VoiceState.SLEEPING, "reset to standby for wake");
      } else {
        return false;
      }
    }
    long now = System.nanoTime();
    long cooldownNs = Duration.ofMillis(props.getWakeCooldownMs()).toNanos();
    synchronized (wakeLock) {
      if (lastWakeAtNs.get() != Long.MIN_VALUE && now - lastWakeAtNs.get() < cooldownNs) {
        return false;
      }
      lastWakeAtNs.set(now);
      try {
        machine.transitionTo(VoiceState.WAKING, reason == null ? "manual wake" : reason);
      } catch (VoiceStateMachine.InvalidTransitionException e) {
        return false;
      }
    }
    String sessionId = "vs-" + Long.toHexString(System.nanoTime());
    activeSessionId.set(sessionId);
    return true;
  }

  public boolean beginListening() {
    try {
      machine.transitionTo(VoiceState.LISTENING, "capturing command");
      resetInactivity();
      return true;
    } catch (VoiceStateMachine.InvalidTransitionException e) {
      return false;
    }
  }

  public boolean beginProcessing() {
    try {
      machine.transitionTo(VoiceState.PROCESSING, "generating response");
      resetInactivity();
      return true;
    } catch (VoiceStateMachine.InvalidTransitionException e) {
      return false;
    }
  }

  public boolean beginSpeaking() {
    try {
      machine.transitionTo(VoiceState.SPEAKING, "speaking response");
      resetInactivity();
      return true;
    } catch (VoiceStateMachine.InvalidTransitionException e) {
      return false;
    }
  }

  /** Return to sleep. Safe from any state. */
  public void goToSleep(String reason) {
    cancelInactivityTimer();
    activeSessionId.set(null);
    try {
      if (machine.state() == VoiceState.SLEEPING || machine.state() == VoiceState.DISABLED) {
        return;
      }
      machine.transitionTo(VoiceState.SLEEPING, reason == null ? "sleep" : reason);
      armInactivityTimer();
    } catch (VoiceStateMachine.InvalidTransitionException e) {
      machine.forceState(VoiceState.SLEEPING, "forced sleep: " + reason);
      armInactivityTimer();
    }
  }

  public void reportError(String detail) {
    cancelInactivityTimer();
    activeSessionId.set(null);
    machine.forceState(VoiceState.ERROR, detail);
  }

  private void armInactivityTimer() {
    cancelInactivityTimer();
    if (!props.isWakeWordEnabled()) {
      return;
    }
    long ms = TimeUnit.SECONDS.toMillis(props.getInactivityTimeoutSeconds());
    ScheduledFuture<?> f = scheduler.schedule(() -> {
      if (machine.state().isActive()) {
        log.info("Inactivity timeout after {}s -> sleep", props.getInactivityTimeoutSeconds());
        goToSleep("inactivity timeout");
      }
    }, ms, TimeUnit.MILLISECONDS);
    inactivityTimer.set(f);
  }

  /**
   * Cancels the pending timer and clears the slot under a compare-and-set so a
   * concurrent reset cannot leave a stale future behind.
   */
  private void cancelInactivityTimer() {
    ScheduledFuture<?> f = inactivityTimer.getAndSet(null);
    if (f != null) {
      f.cancel(false);
    }
  }

  private void resetInactivity() {
    lastActivityNs.set(System.nanoTime());
    if (machine.state().isActive()) {
      armInactivityTimer();
    }
  }

  @PreDestroy
  void shutdown() {
    cancelInactivityTimer();
    scheduler.shutdownNow();
    try {
      detector.close();
    } catch (RuntimeException ignored) {
      // shutdown path: nothing useful to do
    }
  }
}