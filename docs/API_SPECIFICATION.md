# API_SPECIFICATION.md — SACHET AI

> Status: NORMATIVE stub (Phase 0). Base URL: `/api/v1`. All endpoints enforce server-side RBAC + document-level checks.
> Flow: `AuthN → AuthZ → Security Policy → Permission-filtered Retrieval → Model Gateway → Output Policy`.

## 1. Conventions

- Versioning: `/api/v1/...`; breaking changes → `/api/v2`, never silent v1 breakage.
- Auth: `Authorization: Bearer <JWT>`; all calls carry `security_context = {user_id, roles, clearance, department, project_scopes}` resolved server-side.
- Errors (safe): `{error: {code, message, request_id}}` — generic to client, detail to secure log. Codes: `400, 401, 403, 404, 409, 422, 429, 500`.
- Pagination: `?limit&offset`; streaming via SSE.
- Rate limits (default): auth 10/min/IP, search 60/min/user, chat 30/min/user, exports 5/hr/user, admin 30/min.
- Audit: every denial, HIGH+ tool, export, admin, and external call emits audit event (see `docs/AUDIT_LOGGING.md`).

## 2. Auth — `/api/v1/auth`

| Method/Path | Notes |
|---|---|
| `POST /auth/login` | AuthN; auth/authz: public + rate-limit; errors: 401/429; audit: `auth.login*`. |
| `POST /auth/refresh` | AuthN: refresh token rotation; audit: `auth.token_refresh`. |
| `POST /auth/logout` | AuthN: revoke; audit: `auth.logout`. |
| `GET /auth/me` | AuthN required; returns roles/clearance/scopes; errors: 401. |

## 3. Documents — `/api/v1/documents` (Phase 3: IMPLEMENTED)

- `POST /documents` (multipart: file + `{title, classification, department}`) → `202 {duplicate, documentId, jobId, status}` (or `200 {duplicate:true, ...}` when the same owner re-uploads identical bytes). Authz: active user + classification ≤ clearance. Errors: 400 (type/magic/empty), 403 (ceiling), 413 (size). Audit: `document.uploaded`. Starts async job `QUEUED`.
- `GET /documents?page=&size=` → paginated CURRENT docs the caller may read (ownership/grants + clearance bands). Uniform 404 NOT used here — absent rows simply don't appear.
- `GET /documents/{id}` → metadata (no content); 404 when missing OR no read access (existence hiding).
- `GET /documents/{id}/versions` / `POST /documents/{id}/versions` (new file → version+1, old → SUPERSEDED, grants copied to the new row). Authz: owner or ADMIN; readable-but-not-owned → 403.
- `GET /documents/{id}/chunks?page=&size=` → paginated `{chunkIndex, pageNumber, section, text}`; same 404 rule as metadata.
- `DELETE /documents/{id}` → NOT IMPLEMENTED (Phase 9 controlled ops: CRITICAL + confirmation + `doc.delete` audit).

## 4. Ingestion Jobs — `/api/v1/ingestion/jobs` (Phase 3: IMPLEMENTED)

- `GET /ingestion/jobs/{job_id}` → `{status, progress, errorCode, errorMessage, attempts}`. States: `QUEUED → VALIDATING → PARSING → OCR → CHUNKING → EMBEDDING → INDEXING → READY | FAILED` (Phase 3 transitions CHUNKING → READY; EMBEDDING/INDEXING land in Phase 4). Jobs visible to owner or ADMIN only, else 404.
- `POST /ingestion/jobs/{job_id}/retry` → `202` re-queue (attempts+1, blob must still exist). Only FAILED jobs: else 409. Audit: `job.retry`.
- Errors: 404 (missing/foreign), 409 (not FAILED / blob gone). Never auto-mark READY on failure; every failure carries a stable `errorCode` (`VALIDATION_FAILED, POLICY_DENIED, MALWARE_SUSPECT, SCAN_UNAVAILABLE, PARSE_ERROR, OCR_REQUIRED, OCR_UNAVAILABLE, LIMIT_EXCEEDED, INTERNAL`) with a generic message.

## 5. Search — `/api/v1/search` (Phase 4: IMPLEMENTED; answer generation is Phase 5)

- `POST /search {query (1..2000 chars), topK?, docIds?}` → `{results: [{chunkId, documentId, documentTitle, documentVersion, page, section, score, vectorScore}], evidenceStatus, message}` — permission-filtered hybrid (vector + lexical, RRF), citations attached, evidence graded; insufficient → exact insufficient-evidence sentence. Errors: 400/401/503 (engine down). Audit: `search.executed` (actor + returned doc IDs + count; query text never logged).
- AuthZ: permission filter applied BEFORE scoring (readable-IDs scoping + bands; docIds intersect readable). Errors: 401/503. Audit: `search.executed`.
- No global search bypass; `top_k` capped (default 10, max 50).

## 6. Chat (streaming) — `/api/v1/chat`

- `POST /chat/completions` (SSE: `text/event-stream`) `{conversation_id?, message, language}` → streams `delta` events + final `{answer, citations: [{document_id, version, page, section}]}`.
- Pipeline: AuthZ → Query Analysis → Permission-filtered Retrieval → Model Gateway → Grounded Response → Output Policy. Untrusted doc content delimited, never overrides policy.
- Insufficient evidence → `"I could not find sufficient supporting information in the authorized documents."` No fake citations. Errors: 403/429/500. Audit: `chat.grounded_answer`.
- `GET /chat/conversations`, `GET /chat/conversations/{id}/messages` — owner or authorized role only.

## 7. Voice — `/api/v1/voice`

- `POST /voice/stt` (audio → text, `language` code) and `POST /voice/tts` (text → audio). Check `ModelPolicy + classification + language_capabilities` BEFORE any external STT/TTS; forbidden → `403 external_blocked`, never silent fallback.
- Authz: `voice:use`; rate-limit 20/min. Audit: `voice.stt_request / tts_request / blocked_policy`. No audio stored beyond retention.

## 8. Studio / Generation — `/api/v1/studio`

- `POST /studio/reports` / `POST /studio/presentations` `{prompt, doc_ids, template}` → `202 {generated_file_id}` (async MEDIUM tool). Citations preserved into output metadata.
- Authz: `studio:generate` + `doc:read` on every `doc_id`. Errors: 403/422. Audit: `studio.report_generated`.

## 9. Exports — `/api/v1/exports` (HIGH risk)

- `POST /exports {doc_ids, format, reason}` → `202 {export_id, status: pending_approval}`; `POST /exports/{id}/confirm` (explicit confirmation); `GET /exports/{id}/download` (short-lived signed URL).
- Authz: `export:high` + per-doc `doc:read` + classification policy; cross-tenant always denied. Rate-limit 5/hr. Audit: `export.requested/approved/denied/completed`. Bypass attempts → `authz.escalation_attempt`.

## 10. Audit & Admin — `/api/v1/audit`, `/api/v1/admin`

- `GET /audit?from&to&action&actor` → requires `audit:read` + MFA; every read emits `audit.viewed`. No full content in payloads.
- `GET /admin/users`, `POST /admin/roles/assign`, `GET /admin/config`, `PUT /admin/config` (CRITICAL, dual-control where configured). Authz: `admin:*`. Audit: `admin.*`.
- `GET /security/policy` → effective `ModelPolicy` + feature flags for caller (no secrets).

## 11. Health — `/api/v1/health`

- `GET /health` (unauthenticated, no sensitive info) → `{status, version, deps: {db, redis, vector}}`. Implemented stub in `backend/app/main.py`.

## 12. Acceptance (Phase 0)

- [ ] All groups specify auth/authz/errors/rate-limit/audit.
- [ ] Pre-retrieval filtering + `/api/v1` versioning consistent across docs.
- [ ] No endpoint leaks existence of unauthorized docs.
