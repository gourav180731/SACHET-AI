# SACHET AI — Privacy Model (Phase 0 Spec)

> Status: SPECIFICATION ONLY — NOT IMPLEMENTED.
> Privacy is priority #1 per AGENTS.md.
> Minimal collection, purpose limitation, deletability.

## 1. Data Inventory

| Category | Examples | Handling |
|---|---|---|
| Identity | user_id, roles, clearance, dept, scopes | INTERNAL; session-scoped |
| Auth artifacts | password hashes, tokens, MFA seeds | SECRET(system); hashed/vaulted |
| Document bytes | PDF, scans, DOCX, PPTX, TXT, MD | per-document classification |
| Metadata + ACLs | title, version, clearance, ACL lists | INTERNAL to SECRET |
| Chunks + embeddings | 800–1200 token spans + vectors | inherit document classification |
| Conversations | prompts, answers, citations | max classification of cited docs |
| Preferences | language, UI settings | INTERNAL; never mined |
| Audit events | who/when/action/doc_ids/pages/model | INTERNAL; integrity-protected |
| Voice artifacts | audio clips, transcripts (future) | inherit classification; NOT IMPLEMENTED |

## 2. Where Data Is Stored (Separation)

- Document bytes live in private Object Storage,
  encrypted at rest (target), signed URLs only.
- Metadata, ACLs, and ingestion jobs live in PostgreSQL.
- Vectors live in pgvector by default
  (same PostgreSQL, HNSW index)
  or in Qdrant as an optional backend.
- Audit events live in an append-only log store
  with separate credentials and longer retention.
- Memory is separated three ways:
  conversation context is distinct from
  user preferences, which are distinct from
  document knowledge.
- The system never auto-stores document content
  as personal memory.

## 3. Who May Access (Need-to-Know)

- Retrieval filters by `security_context` pre-search;
  the LLM sees authorized chunks only.
- Admins manage accounts and policy
  but do not bypass document ACLs;
  any admin read is itself audited.
- OCR, parser, and embedding workers receive
  scoped, short-lived access to one job payload.
- External providers receive data ONLY when
  ModelPolicy explicitly allows the classification;
  otherwise the call is BLOCKED, never silently retried
  through another path.

## 4. Retention and Deletion

- Retention is configurable per category:
  conversations ~90 days (TBD), jobs ~180 days,
  audit logs for years under legal hold.
- Deletion requests purge conversations and preferences.
- Document deletion purges bytes, chunks, and vectors,
  and tombstones existing citations.
- Backup expiry follows a defined schedule (target).
- All retention/deletion automation is NOT IMPLEMENTED;
  Phase 0 defines the policy hooks and state transitions.

## 5. Egress Rules

- External model, STT, TTS, and search calls need
  an explicit ModelPolicy ALLOW for the classification.
- Default posture (configurable):
  PUBLIC/INTERNAL may use approved externals,
  RESTRICTED and above stay internal-only.
- Cross-tenant retrieval is always OFF.
- The web-search tool never receives raw document text,
  only user-approved query substrings (target).
- Exports are watermarked, labeled, and audited;
  SECRET export targets an approval workflow.

## 6. Model Processing Rules

- Internal models are preferred for RESTRICTED and above.
- External calls carry the minimal authorized context only;
  bulk corpus upload is forbidden.
- No training on customer data
  (contractual target with providers).
- Translation preserves citations end to end;
  low-confidence translations are labeled as such.

## 7. Logging and Monitoring Privacy

- Logs carry IDs, page numbers, and model names —
  never full chunk text, audio, or secrets.
- Verbose sensitive logging is OFF by default.
- Audit logs are readable only by the auditor role
  and are integrity-checked (hash chain target).
- User-facing errors are generic;
  details go to the secure log under a correlation ID.

## 8. Open Items (NOT IMPLEMENTED)

Consent flows, DSAR automation,
a PII-redaction engine, backup-purge verification,
and a formal DPIA with legal review
are all deferred to Phase 1 and beyond.
Nothing in this file claims a running control.
