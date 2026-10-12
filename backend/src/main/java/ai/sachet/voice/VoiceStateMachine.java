package ai.sachet.voice;

import java.time.Instant;
import java.util.EnumSet;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Single source of truth for the voice lifecycle.
 *
 * <p>Design notes (the non-obvious concurrency decisions):
 * <ul>
 *   <li>The current state lives in an {@link AtomicReference} holding an
 *       immutable {@link Snapshot}. Readers therefore never observe a torn
 *       "state A with timestamp of B" pair, which a pair of separate volatile
 *       fields would allow.</li>
 *   <li>Transitions are serialised on a private monitor. A CAS loop alone would
 *       be enough for state, but we must also run the cooldown check and the
 *       transition listeners under the same lock, otherwise two threads racing on
 *       "hello sachet" could both observe an expired cooldown and both start a
 *       session — the exact duplicate-wake bug this class exists to prevent.</li>
 *   <li>Listeners are invoked <em>outside</em> the lock. A listener that calls
 *       back into the machine (for example the orchestrator reacting to WAKING by
 *       stopping the detector) would otherwise deadlock on a non-reentrant path.</li>
 * </ul>
 */
public class VoiceStateMachine {

  /** Rejected transitions are a programming error, not a runtime condition. */
  public static class InvalidTransitionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public InvalidTransitionException(VoiceState from, VoiceState to) {
      super("Invalid voice state transition: " + from + " -> " + to);
    }
  }

  /** Immutable state + metadata read together. */
  public static final class Snapshot {
    private final VoiceState state;
    private final Instant changedAt;
    private final String detail;
    private final long revision;

    Snapshot(VoiceState state, Instant changedAt, String detail, long revision) {
      this.state = state;
      this.changedAt = changedAt;
      this.detail = detail;
      this.revision = revision;
    }

    public VoiceState state() { return state; }
    public Instant changedAt() { return changedAt; }
    /** Human-readable reason for the current state; never null. */
    public String detail() { return detail == null ? "" : detail; }
    /** Monotonic counter; increments on every accepted transition. */
    public long revision() { return revision; }

    @Override public String toString() {
      return "Snapshot[" + state + " rev=" + revision + " detail=" + detail() + "]";
    }
  }

  private static final Map<VoiceState, Set<VoiceState>> ALLOWED = buildGraph();

  private static Map<VoiceState, Set<VoiceState>> buildGraph() {
    Map<VoiceState, Set<VoiceState>> g = new EnumMap<>(VoiceState.class);
    g.put(VoiceState.DISABLED, EnumSet.of(VoiceState.INITIALIZING, VoiceState.DISABLED));
    g.put(VoiceState.INITIALIZING, EnumSet.of(
        VoiceState.SLEEPING, VoiceState.DISABLED, VoiceState.ERROR));
    g.put(VoiceState.SLEEPING, EnumSet.of(
        VoiceState.WAKING, VoiceState.INITIALIZING, VoiceState.DISABLED, VoiceState.ERROR));
    g.put(VoiceState.WAKING, EnumSet.of(
        VoiceState.LISTENING, VoiceState.SLEEPING, VoiceState.ERROR, VoiceState.DISABLED));
    g.put(VoiceState.LISTENING, EnumSet.of(
        VoiceState.PROCESSING, VoiceState.SPEAKING, VoiceState.SLEEPING,
        VoiceState.ERROR, VoiceState.DISABLED));
    g.put(VoiceState.PROCESSING, EnumSet.of(
        VoiceState.SPEAKING, VoiceState.LISTENING, VoiceState.SLEEPING,
        VoiceState.ERROR, VoiceState.DISABLED));
    g.put(VoiceState.SPEAKING, EnumSet.of(
        VoiceState.LISTENING, VoiceState.SLEEPING, VoiceState.ERROR, VoiceState.DISABLED));
    g.put(VoiceState.ERROR, EnumSet.of(
        VoiceState.INITIALIZING, VoiceState.SLEEPING, VoiceState.DISABLED));
    return Map.copyOf(g);
  }

  private final AtomicReference<Snapshot> current;
  private final CopyOnWriteArrayList<Consumer<Snapshot>> listeners = new CopyOnWriteArrayList<>();
  private final AtomicLong revision = new AtomicLong();
  private final Object lock = new Object();

  public VoiceStateMachine(VoiceState initial) {
    this.current = new AtomicReference<>(
        new Snapshot(initial, Instant.now(), "initial", revision.get()));
  }

  public Snapshot snapshot() { return current.get(); }
  public VoiceState state() { return current.get().state(); }

  public void addListener(Consumer<Snapshot> listener) { listeners.add(listener); }

  public void removeListener(Consumer<Snapshot> listener) { listeners.remove(listener); }

  /** True when {@code from -> to} is a legal edge. */
  public static boolean isAllowed(VoiceState from, VoiceState to) {
    return from == to || ALLOWED.getOrDefault(from, Set.of()).contains(to);
  }

  /**
   * Attempt a transition.
   *
   * @return true when the state changed
   * @throws InvalidTransitionException when the edge is not permitted
   */
  public boolean transitionTo(VoiceState to, String detail) {
    Snapshot next;
    synchronized (lock) {
      Snapshot prev = current.get();
      if (!isAllowed(prev.state(), to)) {
        throw new InvalidTransitionException(prev.state(), to);
      }
      if (prev.state() == to) {
        return false; // idempotent no-op, still legal
      }
      next = new Snapshot(to, Instant.now(), detail, revision.incrementAndGet());
      current.set(next);
    }
    // Outside the lock: listeners may call back into the machine.
    for (Consumer<Snapshot> l : listeners) {
      try {
        l.accept(next);
      } catch (RuntimeException ignored) {
        // A misbehaving listener must not wedge the audio subsystem.
      }
    }
    return true;
  }

  /** Convenience overload. */
  public boolean transitionTo(VoiceState to) { return transitionTo(to, null); }

  /**
   * Force the machine to a state regardless of the edge. Reserved for shutdown
   * and for recovery from an unknown condition, never for normal flow control.
   */
  public void forceState(VoiceState to, String detail) {
    Snapshot next;
    synchronized (lock) {
      next = new Snapshot(to, Instant.now(), detail, revision.incrementAndGet());
      current.set(next);
    }
    for (Consumer<Snapshot> l : listeners) {
      try { l.accept(next); } catch (RuntimeException ignored) { /* see above */ }
    }
  }

  public static Set<VoiceState> allowedTargets(VoiceState from) {
    return ALLOWED.getOrDefault(from, Set.of());
  }
}