import React from "react";

/**
 * Animated iridescent chromatic Voice Orb.
 *
 * Implements the Uiverse glowing orb loader with dynamic state-aware letter animations
 * and rotating chromatic shadow fields.
 *
 * @param {string} text - Word to render letter-by-letter (e.g. "Generating", "Listening", "Speaking")
 * @param {string} mode - "listening" | "generating" | "speaking"
 */
export default function VoiceOrb({ text = "Generating", mode = "generating" }) {
  const letters = Array.from(text);

  const spinnerClass =
    mode === "listening"
      ? "voice-orb-spinner voice-orb-spinner--listening"
      : mode === "speaking"
      ? "voice-orb-spinner voice-orb-spinner--speaking"
      : mode === "sleeping"
      ? "voice-orb-spinner voice-orb-spinner--sleeping"
      : "voice-orb-spinner";

  return (
    <div className="relative flex items-center justify-center p-6">
      <div className="voice-orb-wrapper">
        {letters.map((char, index) => (
          <span
            key={`${char}-${index}`}
            className="voice-orb-letter"
            style={{ animationDelay: `${(index * 0.08).toFixed(2)}s` }}
          >
            {char === " " ? "\u00A0" : char}
          </span>
        ))}
        <div className={spinnerClass} />
      </div>
    </div>
  );
}
