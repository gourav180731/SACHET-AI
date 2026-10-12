# SACHET AI — Data Classification (Phase 0 Spec)

> Status: SPECIFICATION ONLY — NOT IMPLEMENTED.
> Levels and handling rules are operator-configurable; defaults below are secure-by-default.
> Classification travels with bytes → chunks → vectors → answers → exports.
> See `ACCESS_CONTROL.md` for enforcement and `LLM_MODEL_POLICY.md` for routing.

## 1. Levels (Configurable)

- Default lattice: `PUBLIC < INTERNAL < RESTRICTED < CONFIDENTIAL < SECRET` defined in `classification.levels[]` with `{name, rank, color, marking}`.
- Operators may rename/extend levels via config migration; rank order drives dominance checks in PolicyService.
- Every document, chunk, vector row, transcript, and generated artifact carries exactly one level; unlabeled content defaults to RESTRICTED (fail closed).
- Marking strings (headers/footers/filenames) derive from the same registry so UI, storage, and audit agree.

## 2. Handling Rules per Level

| Level | Storage | Model routing | Export/share | Retention |
|---|---|---|---|---|
| PUBLIC | standard encrypted bucket, CDN allowed | internal or approved_external | self-approved, labeled | default schedule |
| INTERNAL | private bucket, no public links | internal preferred, external allowed | labeled, audited | default + 1y audit |
| RESTRICTED | private + signed-URL only, no external STT/TTS | internal_only | confirmation + reason + audit | extended, OFFICER review to purge |
| CONFIDENTIAL | isolated prefix, tighter key scope | internal_only, reduced context | REVIEWER approval + audit | extended, dual-control purge |
| SECRET | isolated pool/prefix, MFA-gated access | isolated internal pool only | SECURITY_ADMIN dual approval | longest, legal-hold aware |

- Network: RESTRICTED+ never sent to external models/search; SECRET stays in isolated subnets (targets; see `SECURITY_ARCHITECTURE.md`).
- Display: every view renders the level banner; INTERNAL KNOWLEDGE vs EXTERNAL WEB labeling is orthogonal and always shown.

## 3. Marking and Propagation

- Ingestion inherits uploader-proposed level, clamped upward by auto-scan (e.g., ID/key patterns) pending REVIEWER confirmation.
- Chunking/embedding/indexing copy `{classification}` immutably; translation and summarization must not drop or lower it.
- Query ceiling = max(level in scope, levels of retrieved chunks); answers and Studio outputs are stamped at the ceiling.
- Filenames, footers, and provenance pages encode the ceiling level.

## 4. Downgrade / Upgrade Workflow

- **Upgrade** (raise level): uploader, OFFICER+, or auto-scan may raise immediately; takes effect on next retrieval; audited.
- **Downgrade** (lower level): forbidden by single user. Requires written reason + REVIEWER approval (CONFIDENTIAL+: SECURITY_ADMIN dual) + re-marking of all versions + audit event.
- Downgraded copies are new versioned outputs; original history retains its level.
- Embargo/legal-hold blocks downgrade and purge regardless of approvals.

## 5. Enforcement and Audit

- PolicyService evaluates level dominance + ModelPolicy before retrieval, generation, translation, voice, and export.
- Violations BLOCK with explicit rule IDs; silent fallback to a more permissive path is forbidden.
- Audit per event: `{who, when, action, doc_ids, old_level, new_level, approvers, reason}` without full content.

## 6. Config Keys and Ingestion Defaults

- Keys: `classification.levels[]`, `classification.default_level` (RESTRICTED), `classification.auto_upgrade_patterns[]`, `classification.downgrade_approvers{CONFIDENTIAL, SECRET}`, `retention.schedule[{level}]`.
- IngestionService proposes uploader level, auto-scan can only raise, REVIEWER confirms; disputes queue for OFFICER review, never silently lower.
- Vector rows in pgvector carry `classification` + `tenant/scope` filter columns; Qdrant adapter mirrors the same fields via VectorDBGateway.
- `.env.example` carries placeholders only; real thresholds live in versioned config, changes audited.

## 7. Testing and NOT IMPLEMENTED (NOT IMPLEMENTED)

- Tests: default-to-RESTRICTED on unlabeled input, propagation through translate/rerank/summarize, downgrade-without-approval rejected, SECRET isolation, footer/marking presence.
- Cross-tier leak tests: RESTRICTED chunk must never score for PUBLIC-only user even on exact-match query; paraphrase-via-translation must not bypass filters.
- Audit review cadence: level-change and downgrade events reviewed by SECURITY_ADMIN monthly; anomalies trigger quarantine per `PROMPT_SECURITY.md`.
- NOT IMPLEMENTED: auto-classifier model choice, KMS/HSM key scoping, SIEM forwarding of level-change events.
