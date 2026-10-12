import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

/**
 * Content-Security-Policy is injected only for production builds.
 * The dev server relies on an inline module script for React Fast Refresh,
 * which a strict `script-src 'self'` policy would block, so the policy is
 * deliberately not applied during development.
 */
const csp = [
  "default-src 'self'",
  "img-src 'self' data: blob:",
  "style-src 'self' 'unsafe-inline'",
  "script-src 'self'",
  "connect-src 'self' http://localhost:8000 https:",
  "base-uri 'self'",
  "form-action 'self'",
  "frame-ancestors 'none'",
  "object-src 'none'",
].join("; ");

function cspPlugin() {
  return {
    name: "sachet-csp",
    apply: "build",
    transformIndexHtml(html) {
      return {
        html,
        tags: [
          {
            tag: "meta",
            attrs: { "http-equiv": "Content-Security-Policy", content: csp },
            injectTo: "head-prepend",
          },
        ],
      };
    },
  };
}

export default defineConfig({
  plugins: [tailwindcss(), react(), cspPlugin()],
  server: {
    host: "0.0.0.0",
    port: 3000,
    strictPort: true,
  },
  preview: {
    host: "0.0.0.0",
    port: 3000,
    strictPort: true,
  },
  build: {
    outDir: "dist",
    sourcemap: false,
    chunkSizeWarningLimit: 900,
  },
});