# ROADMAP.md — SACHET AI

> Status: LIVING PLAN. Phase numbering follows the master prompt §68
> (Phase 0 docs → Phase 1 foundation → Phase 2 security → … → Phase 10 hardening).
> No-drift rule: update this file + impacted docs when scope changes; never silently expand.
>
> STACK DECISION (binding): server-side code is Java only (Spring Boot 3, Maven, Java 22).
> The Phase 1 Python prototype was removed and ported. The browser UI (React) is the
> sole non-Java part. Remaining "FastAPI/Python" mentions in Phase 3+ spec docs mean
> their Spring/Java equivalents and will be rewritten when those phases land.

## 1. Status Legend

- `Completed` — code + tests + docs + config green. `In progress` — active unit. `Planned` — scoped, not started. `Future` — beyond Phase 10. `Blocked` — dependency noted.

## 2. Phase Overview (§68 master numbering)

| Phase | Focus | Status |
|---|---|---|
| 0 | Documentation: all specs, AGENTS.md, compose, stubs, config contracts | Completed |
| 1 | Project foundation (Java port): Spring Boot app, config fail-fast, auth foundation, logging, health/ready, dev env | Completed |
| 2 | Security foundation: login/RBAC/permissions, classification, document ACLs, secure storage, hash-chained audit, security middleware, model policy, isolation tests | Completed |
| 3 | Document ingestion: upload (202 async), validation (type/magic/size), PDF/DOCX/PPTX/TXT/MD parsers, Tesseract OCR hook, provenance chunking, versioning + grant inheritance, SHA-256 dedup, jobs + retry | Completed |
| 4 | RAG: local multilingual embeddings (pure-Java), permission-first hybrid retrieval, RRF fusion, citations, evidence status, /search API, EMBEDDING stage | Completed |
| 5 | Text assistant: chat, streaming, multilingual text, citations, conversation context, document Q&A | Planned |
| 6 | Document Studio: summaries, reports, PPTX/DOCX, comparison, translation, briefing notes, citations | Planned |
| 7 | Voice: input/streaming, STT, language detection, TTS, interruption, multilingual conversation (provider abstraction) | Planned |
| 8 | Multilingual expansion: test/harden listed Indian languages, maintain Language Capability Registry | Planned |
| 9 | Controlled agent: tool registry, permissions, risk levels, confirmations, audit, safe workspace actions | Planned |
| 10 | Hardening: security/RAG/injection/isolation/perf/multilingual/failure testing | Planned |

## 3. Sections by Status

- Completed: Phase 0 (specs, AGENTS.md, compose topology); Phase 1 as Java port (app factory, `/health`+`/ready`, request-id + security headers, safe envelopes, bcrypt+HS256 auth foundation, User model, Dockerfiles, frontend EXPERIMENTAL shell); Phase 2 (login with generic failures, DB roles/permissions, document ACLs with clearance-gate-outranks-grants, hash-chained audit + tamper test, ModelPolicy BLOCK, traversal-proof checksummed storage, 49 green tests); Phase 3 (multipart upload → 202 async jobs, extension/magic/size validation, PDFBox/POI/TXT-MD parsers, Tesseract OCR abstraction with clean OCR_UNAVAILABLE, provenance chunking, family versioning + grant inheritance, SHA-256 dedup, `/ingestion/jobs` + retry, permission-filtered listing, 84 green tests); Phase 4 (pure-Java MiniLM embeddings, hybrid permission-first retrieval + RRF, evidence bands, `/search`, pgvector EXPERIMENTAL, 110 green tests).
- In progress: (none — awaiting Phase 5 kickoff).
- Planned: Phases 5–10 per table above.
- Future: cross-tenant federation, advanced analytics, mobile — NOT scoped.
- Blocked: real model-provider credentials (need vault + `ModelPolicy` approval); production TLS certs; Docker daemon unavailable on current dev machine (containers not yet runtime-verified); no tesseract binary on dev machine (OCR path mock-tested; needs engine on any OCR deployment); ONNX Runtime native unusable on Win hosts with inbox System32 onnxruntime.dll (pure-Java inference adopted instead — no capability loss).

## 4. Acceptance Criteria (per phase)

- P0: all docs merged and consistent, compose topology defined, `GET /api/v1/health` contract fixed, secret-scan clean. ✅
- P1: `GET /api/v1/health` + `/ready` live, safe-error envelope, request-ID + security headers, JWT issue/verify + `security_context` shape, config fail-fast guards, User CRUD, `mvn test` green, secret-scan clean. ✅
- P2: login against DB users (generic failures, no enumeration), RBAC roles/permissions + document ACLs enforced server-side pre-retrieval, clearance gate outranks grants, hash-chained audit + tamper test, model-policy BLOCK test, cross-user isolation tests green. ✅ (49 passed)
- P3: PDF/DOCX/PPTX/TXT/MD parsers with generated fixtures; corrupt → `FAILED/PARSE_ERROR`; scanned PDF without OCR → `FAILED/OCR_UNAVAILABLE`, with OCR → READY; async jobs `QUEUED→…→READY|FAILED` with retry; versioning supersedes + copies grants; cross-user 404s. ✅ (84 passed)
- P4: local embeddings real (dims/norm/determinism/Hindi verified); retrieval pre-filtered (zero unauthorized chunks — cross-user + grant-escalation tests); hybrid + RRF; citations `{doc, version, page, section}`; evidence bands + insufficient-evidence phrase exact. ✅ (110 passed)
- P5: streaming chat with citations; conversation retention policy enforced; insufficient-evidence + injection tests green.
- P6: Studio outputs (summary/report/PPTX/DOCX/compare/translate) with classification footer + provenance page; grounded-only labels.
- P7: STT/TTS behind Voice Gateway; SECRET+ blocked externally; voice requests carry same `security_context`.
- P8: language registry enforced per language; unsupported → explicit error; eval per language (Hindi/English/Maithili/Kashmiri first).
- P9: tool registry with risk tiers; HIGH/CRITICAL confirmations; tool activity audited; destructive tools gated.
- P10: full security + RAG-eval + perf gates green in CI; backup/restore drill; pilot checklist signed off.

## 5. No-Drift Rule

- New scope → propose phase amendment here + impacted docs (`API_SPECIFICATION`, `DATABASE_SCHEMA`, `TESTING_STRATEGY`) in same change set.
- Jarvis/Spike repos are reference-only (voice/streaming patterns); do not merge unrelated automation (AGENTS.md Rule 11).
- A phase is done only when: code + tests + docs + config + error handling + no secrets + regression green + criteria pass.
- Small coherent units per phase (§89 workflow); no giant changes spanning phases.
- Every phase exit requires security review + docs update + secret scan clean.

## 6. Dependencies & Gating

- P1 blocks P2+ (no features without app platform + `security_context` shape).
- P2 blocks P3–P4 (no ingestion/retrieval without RBAC + permission filter + audit chain).
- P4 blocks P5–P6 (no chat/generation without permission-filtered retrieval + citations + output policy).
- Gateway abstractions land as interfaces in P2 skeleton, real providers from P3 onward.
- Qdrant remains optional throughout; pgvector default must always pass eval alone.

## 7. References

- `docs/REQUIREMENTS.md`, `docs/TESTING_STRATEGY.md`, `docs/API_SPECIFICATION.md`, `AGENTS.md` §5/§18.
