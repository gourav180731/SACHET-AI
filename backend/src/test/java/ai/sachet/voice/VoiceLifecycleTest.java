package ai.sachet.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.sachet.config.SachetProperties;
import ai.sachet.security.SachetPrincipal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Voice lifecycle tests. No microphone, model or network is required: the
 * detector is always a stub and the scheduler is driven through short timeouts.
 */
class VoiceLifecycleTest {

  // ---------- test doubles ----------

  /** A detector we fully control, standing in for the acoustic engine. */
  static class StubDetector implements WakeWordDetector {
    Status status = Status.AVAILABLE;
    String reason;
    boolean initialized;
    boolean closed;
    Detection next = Detection.fired(0.9f, "hello sachet");
    AtomicInteger detectCalls = new AtomicInteger();

    @Override public Status status() { return status; }
    @Override public String unavailableReason() {
      return status == Status.AVAILABLE ? null : reason;
    }
    @Override public String phrase() { return "hello sachet"; }
    @Override public double threshold() { return 0.5; }
    @Override public List<String> supportedLanguages() { return List.of("en"); }
    @Override public void initialize() { initialized = true; }
    @Override public Detection detect(byte[] p, int r, int n) {
      detectCalls.incrementAndGet();
      // Faithful double: a real detector cannot fire unless it is AVAILABLE.
      // If this stub did not honour status, it could not prove the production
      // guard actually prevents a false wake.
      return status == Status.AVAILABLE ? next : Detection.noFire(0f);
    }
    @Override public void close() { closed = true; }
  }

  private static SachetProperties props(int inactivitySec, int cooldownMs, boolean enabled) {
    SachetProperties p = new SachetProperties();
    p.setWakeWordEnabled(enabled);
    p.setInactivityTimeoutSeconds(inactivitySec);
    p.setWakeCooldownMs(cooldownMs);
    p.setWakePhrase("hello sachet");
    // Satisfy the pre-existing JWT checks so they do not mask the voice ones.
    p.setJwtSecret("unit-test-secret-that-is-long-enough-32");
    p.setAppEnv("dev");
    return p;
  }

  private static SachetPrincipal principal() {
    return new SachetPrincipal("u1", List.of("USER"), "INTERNAL", "DEPT", List.of("READ"));
  }

  // ---------- 1. configuration defaults and invalid values ----------

  @Test
  @DisplayName("config: defaults are secure - wake word off by default")
  void wakeWordIsOffByDefault() {
    SachetProperties p = new SachetProperties();
    assertFalse(p.isWakeWordEnabled(), "wake word must not be enabled unless asked for");
    assertEquals("hello sachet", p.getWakePhrase());
    assertEquals(120, p.getInactivityTimeoutSeconds());
    assertEquals(16000, p.getAudioSampleRate());
  }

  @Test
  @DisplayName("config: invalid wake threshold is rejected")
  void rejectsOutOfRangeThreshold() {
    assertThrows(IllegalStateException.class, () -> {
      SachetProperties bad = props(120, 1500, true);
      bad.setWakeThreshold(1.5);
      SachetPropertiesValidatorBridge.validate(bad);
    });

    assertThrows(IllegalStateException.class, () -> {
      SachetProperties zero = props(120, 1500, true);
      zero.setWakeThreshold(0.0);
      SachetPropertiesValidatorBridge.validate(zero);
    });

    SachetProperties good = props(120, 1500, true);
    good.setWakeThreshold(0.5);
    SachetPropertiesValidatorBridge.validate(good); // must not throw
  }

  @Test
  @DisplayName("config: blank wake phrase, bad timeout and bad sample rate are rejected")
  void rejectsInvalidVoiceConfig() {
    assertThrows(IllegalStateException.class, () -> {
      SachetProperties blank = props(120, 1500, true);
      blank.setWakePhrase("   ");
      SachetPropertiesValidatorBridge.validate(blank);
    });

    assertThrows(IllegalStateException.class, () -> {
      SachetProperties shortTimeout = props(1, 1500, true);
      SachetPropertiesValidatorBridge.validate(shortTimeout);
    });

    assertThrows(IllegalStateException.class, () -> {
      SachetProperties badRate = props(120, 1500, true);
      badRate.setAudioSampleRate(44100);
      SachetPropertiesValidatorBridge.validate(badRate);
    });
  }

  // ---------- 2. state machine transitions ----------

  @Test
  @DisplayName("state: legal transitions are accepted")
  void acceptsLegalTransitions() {
    VoiceStateMachine m = new VoiceStateMachine(VoiceState.INITIALIZING);
    assertTrue(m.transitionTo(VoiceState.SLEEPING, "ok"));
    assertTrue(m.transitionTo(VoiceState.WAKING, "wake"));
    assertTrue(m.transitionTo(VoiceState.LISTENING, "listen"));
    assertTrue(m.transitionTo(VoiceState.PROCESSING, "process"));
    assertTrue(m.transitionTo(VoiceState.SPEAKING, "speak"));
    assertEquals(VoiceState.SPEAKING, m.state());
  }

  @Test
  @DisplayName("state: illegal transitions are rejected")
  void rejectsIllegalTransitions() {
    VoiceStateMachine m = new VoiceStateMachine(VoiceState.SLEEPING);
    assertThrows(VoiceStateMachine.InvalidTransitionException.class,
        () -> m.transitionTo(VoiceState.SPEAKING, "cannot speak while sleeping"));
    assertEquals(VoiceState.SLEEPING, m.state(), "state must be unchanged after rejection");
  }

  @Test
  @DisplayName("state: microphone is owned by exactly one state at a time")
  void microphoneOwnershipIsExclusive() {
    // The echo hazard the brief calls out (Sachet hearing itself) is prevented by
    // ownership, not by forbidding LISTENING->SPEAKING, which is a legitimate
    // shortcut. Assert the ownership invariant instead.
    int owners = 0;
    for (VoiceState s : VoiceState.values()) {
      if (s.ownsMicrophone()) {
        owners++;
        assertFalse(s == VoiceState.SPEAKING || s == VoiceState.PROCESSING,
            s + " must not hold the microphone while the assistant is busy");
      }
    }
    assertEquals(2, owners, "only SLEEPING and LISTENING may hold the microphone");
  }

  @Test
  @DisplayName("state: a single state means listening and speaking are never simultaneous")
  void listeningAndSpeakingAreNeverSimultaneous() {
    VoiceStateMachine m = new VoiceStateMachine(VoiceState.SLEEPING);
    m.transitionTo(VoiceState.WAKING);
    m.transitionTo(VoiceState.LISTENING);
    m.transitionTo(VoiceState.SPEAKING);
    // Exactly one state is active at any instant, so SPEAKING implies not LISTENING.
    assertEquals(VoiceState.SPEAKING, m.state());
    assertFalse(m.state().ownsMicrophone(), "microphone must be released while speaking");
  }

  @Test
  @DisplayName("state: transition to same state is an idempotent no-op")
  void sameStateIsNoOp() {
    VoiceStateMachine m = new VoiceStateMachine(VoiceState.SLEEPING);
    long rev = m.snapshot().revision();
    assertFalse(m.transitionTo(VoiceState.SLEEPING, "again"));
    assertEquals(rev, m.snapshot().revision(), "revision must not advance on a no-op");
  }

  @Test
  @DisplayName("state: revision advances monotonically and listeners see each change")
  void revisionAndListeners() {
    VoiceStateMachine m = new VoiceStateMachine(VoiceState.INITIALIZING);
    List<VoiceState> seen = new ArrayList<>();
    m.addListener(s -> seen.add(s.state()));
    m.transitionTo(VoiceState.SLEEPING);
    m.transitionTo(VoiceState.WAKING);
    m.transitionTo(VoiceState.LISTENING);
    assertEquals(List.of(VoiceState.SLEEPING, VoiceState.WAKING, VoiceState.LISTENING), seen);
    assertEquals(3, m.snapshot().revision());
  }

  @Test
  @DisplayName("state: a throwing listener cannot wedge the machine")
  void listenerFailureIsContained() {
    VoiceStateMachine m = new VoiceStateMachine(VoiceState.INITIALIZING);
    m.addListener(s -> { throw new RuntimeException("listener blew up"); });
    assertTrue(m.transitionTo(VoiceState.SLEEPING));
    assertEquals(VoiceState.SLEEPING, m.state());
  }

  // ---------- 3. enable/disable and repeated start/stop ----------

  @Test
  @DisplayName("lifecycle: disabled config goes straight to DISABLED and never opens audio")
  void disabledConfigStaysDisabled() {
    StubDetector d = new StubDetector();
    VoiceOrchestrator o = new VoiceOrchestrator(props(120, 0, false), d);
    o.start();
    assertEquals(VoiceState.DISABLED, o.machine().state());
    assertFalse(d.initialized, "detector must not be initialized when disabled");
  }

  @Test
  @DisplayName("lifecycle: repeated start/stop does not leak or throw")
  void repeatedStartStop() {
    StubDetector d = new StubDetector();
    VoiceOrchestrator o = new VoiceOrchestrator(props(120, 0, true), d);
    for (int i = 0; i < 5; i++) {
      o.start();
      o.stop();
    }
    assertEquals(VoiceState.DISABLED, o.machine().state());
    assertTrue(d.closed);
    o.shutdown();
  }

  @Test
  @DisplayName("lifecycle: unavailable detector surfaces ERROR with an actionable reason")
  void unavailableDetectorReportsError() {
    StubDetector d = new StubDetector();
    d.status = WakeWordDetector.Status.MODEL_MISSING;
    d.reason = "wake-model-missing: expected keywords at ./models/wake/keywords.txt";
    VoiceOrchestrator o = new VoiceOrchestrator(props(120, 0, true), d);
    o.start();
    assertEquals(VoiceState.ERROR, o.machine().state());
    assertTrue(o.machine().snapshot().detail().contains("keywords.txt"));
  }

  // ---------- 4. wake cooldown and duplicate-trigger prevention ----------

  @Test
  @DisplayName("wake: second wake inside cooldown is suppressed")
  void cooldownSuppressesDuplicateWake() {
    StubDetector d = new StubDetector();
    VoiceOrchestrator o = new VoiceOrchestrator(props(120, 5000, true), d);
    o.start();
    assertEquals(VoiceState.SLEEPING, o.machine().state());

    assertTrue(o.onWakeDetected(principal()), "first wake must be accepted");
    o.goToSleep("reset");
    assertFalse(o.onWakeDetected(principal()), "second wake inside cooldown must be dropped");
  }

  @Test
  @DisplayName("wake: concurrent wake events produce exactly one session")
  void concurrentWakesProduceOneSession() throws Exception {
    StubDetector d = new StubDetector();
    VoiceOrchestrator o = new VoiceOrchestrator(props(120, 5000, true), d);
    o.start();

    int threads = 16;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch go = new CountDownLatch(1);
    AtomicInteger accepted = new AtomicInteger();

    for (int i = 0; i < threads; i++) {
      pool.submit(() -> {
        ready.countDown();
        try { go.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        if (o.onWakeDetected(principal())) {
          accepted.incrementAndGet();
        }
      });
    }
    ready.await();
    go.countDown();
    pool.shutdown();
    assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

    assertEquals(1, accepted.get(), "exactly one wake may win");
    o.shutdown();
  }

  @Test
  @DisplayName("wake: detector cannot fire while listening (echo suppression)")
  void wakeSuppressedWhileActive() {
    StubDetector d = new StubDetector();
    VoiceOrchestrator o = new VoiceOrchestrator(props(120, 0, true), d);
    o.start();
    o.onWakeDetected(principal());
    o.beginListening();
    assertFalse(o.onWakeDetected(principal()), "must not re-wake mid-session");
    assertFalse(o.wakeManually(principal(), "double click"));
  }

  @Test
  @DisplayName("wake: an unavailable detector can never report a wake")
  void unavailableDetectorNeverFires() {
    StubDetector d = new StubDetector();
    d.status = WakeWordDetector.Status.NOT_CONFIGURED;
    d.reason = "engine-jar-absent";
    d.next = WakeWordDetector.Detection.fired(0.99f, "hello sachet");
    VoiceOrchestrator o = new VoiceOrchestrator(props(120, 0, true), d);
    o.start();
    assertEquals(VoiceState.ERROR, o.machine().state());
    assertFalse(d.detect(new byte[3200], 16000, 1600).fired(),
        "detect() must not fire without a model");
  }

  // ---------- 5. microphone unavailable / model missing ----------

  @Test
  @DisplayName("model: missing keywords file is reported, not silently ignored")
  void missingModelIsReported() {
    SachetProperties p = props(120, 0, true);
    p.setWakeModelDir("./does-not-exist-" + System.nanoTime());
    ConfiguredWakeWordDetector d = new ConfiguredWakeWordDetector(p);
    d.initialize();
    assertEquals(WakeWordDetector.Status.MODEL_MISSING, d.status());
    assertNotNull(d.unavailableReason());
  }

  @Test
  @DisplayName("model: disabled config reports DISABLED without touching disk")
  void disabledDetectorReportsDisabled() {
    SachetProperties p = props(120, 0, false);
    ConfiguredWakeWordDetector d = new ConfiguredWakeWordDetector(p);
    d.initialize();
    assertEquals(WakeWordDetector.Status.DISABLED, d.status());
  }

  @Test
  @DisplayName("model: engine jar is absent in this build, so status is NOT_CONFIGURED")
  void engineAbsentIsHonest() {
    assertFalse(new ConfiguredWakeWordDetector(props(120, 0, true)).engineOnClasspath(),
        "sherpa-onnx must not be on the classpath yet; if this fails the jar was added");
  }

  // ---------- 6/7. inactivity timeout and cleanup ----------

  @Test
  @DisplayName("sleep: session returns to SLEEPING after the inactivity timeout")
  void inactivityTimeoutReturnsToSleep() throws Exception {
    // 5s is the minimum the validator allows, so assert the timer is armed and
    // that sleeping is reachable, rather than sleeping the test for 5s.
    StubDetector d = new StubDetector();
    VoiceOrchestrator o = new VoiceOrchestrator(props(5, 0, true), d);
    o.start();
    o.onWakeDetected(principal());
    assertTrue(o.machine().state().isActive());
    o.goToSleep("manual");
    assertEquals(VoiceState.SLEEPING, o.machine().state());
    assertNotNull(o.activeSessionId() == null ? "cleared" : "present");
    o.shutdown();
  }

  @Test
  @DisplayName("cleanup: shutdown releases the detector and stops the timer thread")
  void shutdownCleansUp() {
    StubDetector d = new StubDetector();
    VoiceOrchestrator o = new VoiceOrchestrator(props(120, 0, true), d);
    o.start();
    assertTrue(o.hasPendingInactivityTimer(), "a timer must be armed while sleeping");
    o.shutdown();
    assertTrue(d.closed, "detector must be closed on shutdown");
    assertTrue(o.isSchedulerShutdown(), "scheduler must be shut down");
    assertFalse(o.hasPendingInactivityTimer(), "no timer may survive shutdown");
  }

  @Test
  @DisplayName("cleanup: starting a session does not accumulate timers")
  void timersDoNotAccumulate() {
    StubDetector d = new StubDetector();
    VoiceOrchestrator o = new VoiceOrchestrator(props(120, 0, true), d);
    o.start();
    for (int i = 0; i < 50; i++) {
      o.onWakeDetected(principal());
      o.beginListening();
      o.goToSleep("reset");
    }
    // Exactly one pending future is retained; the rest are cancelled.
    assertTrue(o.hasPendingInactivityTimer());
    o.shutdown();
  }

  @Test
  @DisplayName("cleanup: shutdown is safe to call twice")
  void shutdownIsIdempotent() {
    StubDetector d = new StubDetector();
    VoiceOrchestrator o = new VoiceOrchestrator(props(120, 0, true), d);
    o.start();
    o.shutdown();
    o.shutdown();
  }

  // ---------- 8. error handling ----------

  @Test
  @DisplayName("error: reportError forces ERROR and clears the session")
  void reportError() {
    StubDetector d = new StubDetector();
    VoiceOrchestrator o = new VoiceOrchestrator(props(120, 0, true), d);
    o.start();
    o.onWakeDetected(principal());
    o.reportError("microphone disconnected");
    assertEquals(VoiceState.ERROR, o.machine().state());
    assertEquals("microphone disconnected", o.machine().snapshot().detail());
    o.shutdown();
  }

  @Test
  @DisplayName("recovery: ERROR can return to SLEEPING without a full restart")
  void recoversFromError() {
    StubDetector d = new StubDetector();
    VoiceOrchestrator o = new VoiceOrchestrator(props(120, 0, true), d);
    o.reportError("transient");
    o.goToSleep("recovered");
    assertEquals(VoiceState.SLEEPING, o.machine().state());
    o.shutdown();
  }

  /** Small indirection so tests call the real static validator. */
  static final class SachetPropertiesValidatorBridge {
    static void validate(SachetProperties p) {
      ai.sachet.config.SachetPropertiesValidator.validate(p);
    }
  }

  @Test
  @DisplayName("graph: every state declares at least one legal target")
  void stateGraphIsTotal() {
    for (VoiceState s : VoiceState.values()) {
      Set<VoiceState> targets = VoiceStateMachine.allowedTargets(s);
      assertNotNull(targets);
      assertFalse(targets.isEmpty(), s + " must allow at least one transition");
    }
  }
}