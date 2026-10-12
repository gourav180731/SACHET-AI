import React from "react";

/**
 * Cybernetic Fractal Noise & Grid Pattern Background
 * Built per user specification:
 * - Obsidian base (#000)
 * - Fractal turbulence SVG filter (feTurbulence baseFrequency 19.5, numOctaves 10)
 * - Cyan & electric blue matrix grid lines (#26b, #48f, #48f5)
 * - Subtle linear gradient mask (linear-gradient(#0004, #000))
 */
export default function CyberPattern({ className = "", fixed = true }) {
  return (
    <div
      className={`cyber-pattern-container ${fixed ? "cyber-pattern--fixed" : "cyber-pattern--absolute"} ${className}`}
      aria-hidden="true"
    />
  );
}

export { CyberPattern as Pattern };
