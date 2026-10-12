# TESTING_STRATEGY.md — SACHET AI

> Status: NORMATIVE stub (Phase 0). Every major feature requires tests (AGENTS.md Rule 14). Evidence before claims.

## 1. Pyramid

- Unit (fast, mocked gateways) → Integration (Postgres+pgvector, redis, storage) → Security (adversarial) → RAG-eval → Multilingual/Voice → Perf → E2E → Regression.
- Backend: `pytest`; frontend: `vitest`/`playwright` (when implemented). Coverage gate: ≥80% on `services/`, `api/`, `core/` (authz/policy paths 100%).

## 2. Unit

- Config validation (secure defaults, fail-fast on `JWT_SECRET=CHANGE-ME` in prod).
- Permission filter builder: `security_context → SQL/payload filter` matrix.
- Citation preservation through chunk/rerank/translate/summarize helpers.
- Output-policy redaction + safe-error formatter (no stack/secret leak).

## 3. Integration

- Auth → upload → ingest states (`QUEUED→…→READY`) → search → chat → export happy path on compose stack.
- Version bump creates `document_versions` row + re-embed; `current_version_id` moves only on `READY`.
- pgvector HNSW recall smoke + Qdrant parity (when enabled). Storage gateway local↔S3 parity.

## 4. Security (mandatory, blocking)

| Test | Expectation |
|---|---|
| cross-user isolation | User A cannot list/get/search User B's docs (404, not 403-leak) |
| privilege escalation | `viewer` cannot assign roles / confirm exports / read audit |
| prompt injection | Doc `"Ignore previous instructions..."` treated as content; policy intact |
| external-model-block | SECRET doc + external provider → `403 external_blocked`, audit `external.model_blocked` |
| export-bypass | Direct `GET /exports/{id}/download` without `export:high` + confirmation → denied + audited |
| secret exposure | `rg -i "api[_-]?key\|secret\|password"` clean; error responses contain no trace/secret |

## 5. RAG Eval

- Golden set (NOT IMPLEMENTED data): Q/A pairs with `(document_id, version, page)` labels.
- Metrics: citation accuracy (exact doc+version+page), answer groundedness (entailment), refusal precision (insufficient-evidence → canonical phrase), filter precision (zero unauthorized docs in context).
- Regression: any model/prompt/chunker change re-runs eval; drop >2pp blocks merge.

## 6. Multilingual / Voice

- Per `language_capabilities`: rag/stt/tts matrix; unsupported language → explicit error, never silent fallback.
- Voice: STT accuracy smoke per language, TTS intelligibility stub, policy-block parity (external STT/TTS denied for SECRET+).

## 7. Parsers

- PDF (text + scanned + encrypted-reject), DOCX, PPTX, TXT, MD fixtures; OCR path mocked (`ocrmypdf/unstructured` optional). Malformed files → `FAILED` with `error_code`, never `READY`.

## 8. Perf / E2E / Regression

- Perf: p95 search <2s (top_k=10, 100k chunks, pgvector), chat first-token <3s (stub), ingestion 100-page PDF <5min (worker).
- E2E (Playwright, when UI exists): login → upload → wait READY → chat with citations → export with confirmation.
- Regression: preserved-functionality suite per release; no breaking `/api/v1` change without migration + test.

## 9. CI Gates (target)

`lint → unit → secret-scan → integration (compose) → security suite → rag-eval → build`. Fail on any mandatory security test or eval regression.

## 10. Acceptance (Phase 0)

- [ ] Mandatory test list codified here and referenced from `docs/ROADMAP.md`.
- [ ] Test skeletons exist under `backend/tests/` (NOT IMPLEMENTED cases marked `pytest.mark.skip` with reason).
