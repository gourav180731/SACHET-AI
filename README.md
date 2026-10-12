# SACHET AI — Secure Document Intelligence Agent

> Security-first, multilingual, source-grounded AI agent for sensitive government/organizational documents.

SACHET AI is **NOT** a generic chatbot or "Jarvis with a PDF uploader". It is a controlled agent that helps **authorized** users search, understand, compare, translate, summarize, and generate briefing materials from sensitive documents — with authorization, isolation, provenance, and auditability built in.

## Core Guarantees (implemented, not claimed)

- LLM never has unrestricted store access. Flow: AuthN → AuthZ → Policy → Permission-filtered retrieval → Model Gateway → Grounded response → Output policy.
- Every query carries `security_context {user_id, roles, clearance, department, project_scopes}`.
- Every grounded answer cites `{document, version, page, section}` or honestly says insufficient evidence.
- Sensitive data never leaves the boundary without explicit `ModelPolicy` approval — fail closed.
- Document content is UNTRUSTED DATA; it cannot override system instructions.

## Status — Phase 0 (Documentation)

See `docs/ROADMAP.md`. No production features are claimed. Anything not implemented is marked `NOT IMPLEMENTED` / `EXPERIMENTAL`.

## Quick Start (dev, Phase 1 target)

```bash
cp .env.example .env
docker compose up --build
# backend: http://localhost:8000/api/v1/health
# frontend: http://localhost:3000
```

## Docs

- Architecture: `docs/ARCHITECTURE.md`, `docs/SECURITY_ARCHITECTURE.md`, `docs/RAG_ARCHITECTURE.md`
- Policy: `docs/LLM_MODEL_POLICY.md`, `docs/DATA_CLASSIFICATION.md`, `docs/ACCESS_CONTROL.md`
- Operations: `docs/DEPLOYMENT.md`, `docs/CONFIGURATION.md`, `docs/TESTING_STRATEGY.md`
- Vision/Requirements/Roadmap: `docs/PROJECT_VISION.md`, `docs/REQUIREMENTS.md`, `docs/ROADMAP.md`

## Repo Layout

```
backend/   Java 22 + Spring Boot 3 (Phases 1–2 done: platform + security foundation)
frontend/  React shell (EXPERIMENTAL — health + boundary label only)
docs/      Specification (Phase 0 — this delivery)
infra/     Docker/dev infra
scripts/   Dev utilities
storage/   Local dev storage (gitignored contents)
```

## Security

See `SECURITY.md`. Report issues via https://github.com/anomalyco/opencode (mention Meta Muse Spark). Never file live sensitive documents in public issues.
