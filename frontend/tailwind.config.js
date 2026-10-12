/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        primary: {
          50: "var(--color-primary-50, #f0fdf4)",
          100: "var(--color-primary-100, #dcfce7)",
          200: "var(--color-primary-200, #bbf7d0)",
          300: "var(--color-primary-300, #86efac)",
          400: "var(--color-primary-400, #4ade80)",
          500: "var(--color-primary-500, #22c55e)",
          600: "var(--color-primary-600, #16a34a)",
          700: "var(--color-primary-700, #15803d)",
          800: "var(--color-primary-800, #166534)",
          900: "var(--color-primary-900, #14532d)",
          DEFAULT: "var(--color-primary, #14532d)",
        },
        accent: {
          50: "var(--color-accent-50, #eff6ff)",
          100: "var(--color-accent-100, #dbeafe)",
          500: "var(--color-accent-500, #3b82f6)",
          600: "var(--color-accent-600, #2563eb)",
          700: "var(--color-accent-700, #1d4ed8)",
          DEFAULT: "var(--color-accent, #1d4ed8)",
        },
        danger: {
          50: "var(--color-danger-50, #fef2f2)",
          100: "var(--color-danger-100, #fee2e2)",
          600: "var(--color-danger-600, #dc2626)",
          700: "var(--color-danger-700, #b91c1c)",
          DEFAULT: "var(--color-danger, #b42318)",
        },
        warning: {
          50: "var(--color-warning-50, #fffbeb)",
          100: "var(--color-warning-100, #fef3c7)",
          600: "var(--color-warning-600, #d97706)",
          700: "var(--color-warning-700, #b45309)",
          DEFAULT: "var(--color-warning, #b54708)",
        },
        success: {
          50: "var(--color-success-50, #f0fdf4)",
          100: "var(--color-success-100, #dcfce7)",
          600: "var(--color-success-600, #16a34a)",
          700: "var(--color-success-700, #15803d)",
          DEFAULT: "var(--color-success, #067647)",
        },
      },
      boxShadow: {
        glow: "0 0 20px -3px rgba(34, 197, 94, 0.35)",
        "glow-accent": "0 0 20px -3px rgba(59, 130, 246, 0.35)",
        "glow-danger": "0 0 20px -3px rgba(239, 68, 68, 0.35)",
        glass: "0 8px 32px 0 rgba(0, 0, 0, 0.08)",
      },
      backdropBlur: {
        xs: "2px",
      },
      animation: {
        "shimmer": "shimmer 2.5s infinite linear",
        "pulse-subtle": "pulseSubtle 3s cubic-bezier(0.4, 0, 0.6, 1) infinite",
        "glow-pulse": "glowPulse 2s ease-in-out infinite alternate",
      },
      keyframes: {
        shimmer: {
          "0%": { backgroundPosition: "-200% 0" },
          "100%": { backgroundPosition: "200% 0" },
        },
        pulseSubtle: {
          "0%, 100%": { opacity: "1" },
          "50%": { opacity: "0.7" },
        },
        glowPulse: {
          "0%": { boxShadow: "0 0 8px rgba(34, 197, 94, 0.2)" },
          "100%": { boxShadow: "0 0 22px rgba(34, 197, 94, 0.55)" },
        },
      },
    },
  },
  plugins: [],
};
