# AUDIT_LOGGING.md — SACHET AI

> Status: NORMATIVE stub (Phase 0). Implements AGENTS.md Rule 13.
> Every sensitive operation MUST be auditable without storing full sensitive content.

## 1. Purpose

Provide tamper-evident, queryable traceability for: who accessed what,
when, under which authorization, via which model/tool, and with what outcome.
Audit logs are a security control, not application telemetry.

## 2. Event Taxonomy

| Category | Events |
|---|---|
| Auth | `auth.login`, `auth.logout`, `auth.login_failed`, `auth.token_refresh`, `auth.mfa_challenge` |
| AuthZ | `authz.denied`, `authz.escalation_attempt`, `role.assigned`, `role.revoked` |
| Documents | `doc.upload`, `doc.view`, `doc.download`, `doc.delete`, `doc.share`, `doc.version_restore` |
| Ingestion | `ingest.queued`, `ingest.state_change`, `ingest.failed`, `ingest.ready` |
| Retrieval/RAG | `retrieval.query`, `chat.grounded_answer`, `search.executed` |
| Voice | `voice.stt_request`, `voice.tts_request`, `voice.blocked_policy` |
| Generation | `studio.report_generated`, `studio.pptx_generated`, `generation.blocked_grounding` |
| Export/Share | `export.requested`, `export.approved`, `export.denied`, `export.completed` |
| External | `external.model_call`, `external.model_blocked`, `external.web_search` |
| Admin | `admin.config_change`, `admin.user_created`, `admin.retention_purge`, `audit.viewed` |

HIGH/CRITICAL tool executions (export/share/external/delete) always emit events.
LOW/MEDIUM tools emit events when accessing classified documents.

## 3. Event Schema (PostgreSQL `audit_logs`)

```json
{
  "event_id": "uuid7",
  "occurred_at": "2026-01-01T00:00:00Z",
  "actor": {"user_id": "uuid", "roles": ["analyst"], "department": "finance"},
  "action": "doc.view",
  "doc_ids": ["uuid"],
  "classification": "CONFIDENTIAL",
  "pages": [3, 4],
  "model": {"provider": "internal-llm-a", "model_id": "sachet-1.0", "external": false},
  "tool": {"name": "rag.search", "risk_level": "LOW"},
  "result": "allow | deny | error",
  "request_id": "uuid",
  "prev_hash": "sha256...",
  "event_hash": "sha256..."
}
```

Required fields: `who (actor.user_id), when (occurred_at), action, doc_ids,
classification, pages, model`. Optional: query hash, latency, error code.
FORBIDDEN fields: full document text, chunk text, secrets, tokens, biometrics.

## 4. No-Full-Content Rule

- Never store prompt text, retrieved chunk text, or generated file bytes in audit logs.
- Store references only: `document_id + version + page + chunk_id`.
- If debugging needs content, log a salted hash (`sha256(content + salt)`) — NOT IMPLEMENTED in Phase 0.
- Audit API redacts `query_text` by default; privileged viewers need `audit:view_sensitive`.

## 5. Hash-Chained Append-Only

- `event_hash = sha256(prev_hash || canonical_json(event))`.
- Table is INSERT-only; no UPDATE/DELETE grants for app role. Purge only via `retention_purge` job with dual approval.
- Chain verification job runs daily: `scripts/verify_audit_chain.py` — NOT IMPLEMENTED (Phase 3).
- Any gap/break raises `audit.chain_broken` alert and blocks compliance export.

## 6. Separation & Protection

- Audit logs live in dedicated schema/table (`audit.audit_logs`), separate from metadata DB writes where practical.
- In production, ship to append-only store (WORM volume or separate Postgres role). App service account has INSERT-only.
- Transport over TLS; at-rest encryption via Postgres TDE/volume encryption.
- Admin reads via `GET /api/v1/audit` require `audit:read` role + MFA; every read itself is audited (`audit.viewed`).

## 7. Retention

| Classification touched | Retention | Notes |
|---|---|---|
| TOP SECRET / SECRET | 7 years | Legal hold overrides purge |
| CONFIDENTIAL | 3 years | Configurable per tenant |
| INTERNAL / PUBLIC | 1 year | Configurable |
| Auth failures / denials | 2 years | Never purged early |

Purge is a scheduled job emitting `admin.retention_purge` with count + range, never content.

## 8. Admin Audit & Monitoring

- Dashboards (NOT IMPLEMENTED): denials spike, cross-department access, external-block rate, export volume.
- Alerts: `authz.escalation_attempt`, `export.denied` burst, `audit.chain_broken`.
- Compliance export: signed JSONL/CSV filtered by time/action/actor; includes chain hashes.

## 9. Acceptance (Phase 0)

- [ ] Schema + taxonomy reviewed against AGENTS.md Rule 13.
- [ ] No code path stores chunk text in `audit_logs`.
- [ ] `audit.viewed` event defined for admin reads.

## 10. References

- `docs/DATABASE_SCHEMA.md` § audit_logs, `docs/API_SPECIFICATION.md` § audit, `docs/SECURITY_ARCHITECTURE.md`.
