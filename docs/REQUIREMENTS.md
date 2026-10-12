# SACHET AI — Requirements (Phase 0 Spec)

> Status: SPECIFICATION ONLY — NOT IMPLEMENTED.
> Every requirement traces to AGENTS.md priority order.
> Conventions: FR = functional, NFR = non-functional.

## 1. Functional Requirements

- **FR-001 Ingestion**: the system accepts PDF,
  scanned-PDF, DOCX, PPTX, TXT, and MD uploads
  with metadata `{title, classification, department,
  project_scope, version, language}`.
  Processing is async with states QUEUED → VALIDATING
  → PARSING → OCR → CHUNKING → EMBEDDING
  → INDEXING → READY | FAILED.
- **FR-002 Validation**: each upload passes type,
  size, and policy checks plus a malware-scan hook.
  The scanner engine itself is NOT IMPLEMENTED;
  only the pluggable interface is specified.
  Failures reject the document; READY is never silent.
- **FR-003 Classification**: five levels —
  PUBLIC / INTERNAL / RESTRICTED / CONFIDENTIAL / SECRET —
  configurable via a policy file.
  Default is deny; upgrades need an authorized role
  and produce an audit event.
- **FR-004 RBAC + document ACLs**: roles plus clearance
  plus department plus project_scopes.
  Every endpoint, retrieval call, export,
  voice request, and tool execution enforces
  server-side checks. Frontend hiding is not security.
- **FR-005 Permission-filtered retrieval**: every query carries
  `security_context = {user_id, roles, clearance,
  department, project_scopes}` compiled server-side.
  Filtering applies BEFORE vector search results reach the LLM.
  No global search ignoring `security_context` is permitted.
- **FR-006 Grounded chat**: every factual answer carries
  citations `{document_id, version, page, section}`.
  On insufficient evidence the system replies exactly:
  "I could not find sufficient supporting information
  in the authorized documents."
- **FR-007 Version and freshness**: superseded versions
  are flagged; the latest effective version is shown by default.
  Stale answers must name the newer version.
- **FR-008 Multilingual text**: 22 scheduled languages + English
  for queries and responses via the Language Gateway.
  Registry plus interfaces are specified; engines NOT IMPLEMENTED.
- **FR-009 Voice**: speech-to-text and text-to-speech
  via the Voice Gateway, gated by ModelPolicy + classification.
  NOT IMPLEMENTED — Phase 0 specifies interfaces only.
- **FR-010 Studio**: report, PPT, and workspace generation
  from authorized context only (MEDIUM risk).
  NOT IMPLEMENTED in Phase 0.
- **FR-011 Agent tools**: four risk tiers —
  LOW (search/summarize/translate),
  MEDIUM (report/PPT/workspace write),
  HIGH (sensitive export/share/external),
  CRITICAL (destructive/credential/delete-protected).
  HIGH and above need explicit confirmation plus audit.
  NOT IMPLEMENTED in Phase 0.
- **FR-012 Export control**: downloads, shares,
  and external calls are gated by classification + ModelPolicy.
  Forbidden exports BLOCK with an explicit error.
  NOT IMPLEMENTED in Phase 0.
- **FR-013 Audit**: every sensitive operation logs
  `{who, when, action, doc_ids, classification,
  pages, model, export/admin events}`
  without storing full sensitive content.
  Logs themselves are protected. NOT IMPLEMENTED.
- **FR-014 Administration**: user/role/scope management,
  policy configuration, reindex controls,
  and FAILED-job retry by operators.

## 2. Non-Functional Requirements

- **NFR-001 Security**: OWASP-aware design,
  server-side AuthZ everywhere, safe errors
  (generic to users, detailed to secure logs),
  and no secrets in code, logs, or git.
- **NFR-002 Privacy**: minimal collection,
  purpose limitation, configurable retention,
  deletion workflows, and no automatic storage
  of document content as personal memory.
- **NFR-003 RAG quality**: 800–1200 token chunks
  with overlap, multilingual embeddings,
  pgvector HNSW plus hybrid FTS, reranking,
  and evidence status
  strong / moderate / limited / insufficient.
- **NFR-004 Multilingual**: a registry of 22 languages,
  graceful degradation for low-resource languages,
  provenance preserved through translation,
  per-language evaluation (NOT IMPLEMENTED).
- **NFR-005 Voice**: latency and noise-robustness targets TBD;
  local STT preferred for SECRET content;
  external STT blocked for RESTRICTED+ by default.
  NOT IMPLEMENTED.
- **NFR-006 Performance (targets, unverified)**:
  p95 chat under 8s, ingestion under 5 min per 100 pages,
  50 concurrent users. Must be load-tested before claiming.
- **NFR-007 Reliability**: idempotent async jobs,
  retry with backoff, FAILED states visible and retryable.
- **NFR-008 Modularity**: gateway abstractions for LLM,
  Embedding, VectorDB, STT, TTS, OCR, Parser,
  Storage, Translation. FastAPI backend.
  PostgreSQL + pgvector default, Qdrant optional.
- **NFR-009 Observability**: health, metrics, and traces
  without sensitive content; audit-log integrity checks.
- **NFR-010 Testability**: security, RAG, parser,
  voice, and language tests required for each major feature.

## 3. Acceptance

These requirements are accepted when each FR/NFR
traces to the architecture and threat mitigations,
risk levels are assigned to every tool,
NOT IMPLEMENTED items are explicitly labeled,
and no operational behavior is claimed.
