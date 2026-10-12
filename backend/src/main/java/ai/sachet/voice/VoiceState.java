package ai.sachet.voice;

/**
 * Central voice lifecycle states.
 *
 * <p>The assistant owns exactly one of these at a time. All transitions go
 * through {@link VoiceStateMachine}, which rejects invalid edges, so it is
 * impossible to be simultaneously listening and speaking.
 *
 * <p>Audio ownership by state:
 * <pre>
 *   SLEEPING   wake-word detector holds the microphone, audio never leaves the host
 *   LISTENING  speech recogniser holds the microphone
 *   PROCESSING microphone is released while the AI pipeline runs
 *   SPEAKING   microphone is released while audio is played
 * </pre>
 */
public enum VoiceState {

  /** Feature switched off by configuration or by the user. No audio resources. */
  DISABLED,

  /** Audio device and detector models are being opened. Not yet accepting input. */
  INITIALIZING,

  /** Local wake-word detection only. Microphone is live but audio is not transmitted. */
  SLEEPING,

  /** Wake phrase recognised; transitioning into an active session. */
  WAKING,

  /** Capturing the user's command. */
  LISTENING,

  /** Speech recognition and/or the AI pipeline is running. */
  PROCESSING,

  /** Generating or playing the spoken response. */
  SPEAKING,

  /** Recoverable audio or model failure. Manual retry, or automatic sleep. */
  ERROR;

  /** True when a wake event should start an interactive session. */
  public boolean isActive() {
    return this == WAKING || this == LISTENING || this == PROCESSING || this == SPEAKING;
  }

  /** True when the assistant should be actively capturing microphone audio. */
  public boolean ownsMicrophone() {
    return this == SLEEPING || this == LISTENING;
  }
}