# SACHET AI — frontend

> Status: EXPERIMENTAL shell (Phase 1). Backend-health check + security-boundary
> placeholder only. Chat / Vault / Studio land in later phases.

## Purpose

Multilingual, source-grounded UI for authorized users working with
sensitive organizational knowledge. Frontend hiding is NOT security —
every call enforces server-side RBAC + document-level checks.

## Planned Stack

- Vite + React + TypeScript (to be scaffolded Phase 1+).
- Calls `VITE_API_BASE` (default `http://localhost:8000/api/v1`).
- SSE client for `POST /chat/completions`; citation renderer
  `{document_id, version, page, section}`; explicit insufficient-evidence state.

## Security Notes

- Never stores tokens in URL; short-lived JWT in memory.
- HIGH+ actions (export/share) require explicit confirmation screen.
- Labels INTERNAL KNOWLEDGE vs EXTERNAL WEB; web search opt-in only.

## Docs

Start with `docs/PROJECT_VISION.md`, `docs/API_SPECIFICATION.md`,
`docs/ROADMAP.md`, `AGENTS.md`.
