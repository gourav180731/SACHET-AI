# SACHET AI — Architecture (Phase 0 Spec)

> Status: Phases 0–2 implemented (Java backend).
> Stack: Spring Boot 3 backend (Java only, server side), PostgreSQL + pgvector default,
> Qdrant optional. Gateways via interfaces + DI.

## 1. Layered View (C4-inspired)

- **L1 Presentation**: web UI (chat, studio, admin)
  plus a future voice client (NOT IMPLEMENTED).
  This layer makes no authorization decisions;
  hiding elements here is not security.
- **L2 API**: Spring MVC controllers for
  `/auth`, `/documents`, `/chat`,
  `/studio`, `/voice`, `/admin`.
  Each handler runs AuthN → AuthZ → Security Policy
  before calling any service, and maps errors safely.
- **L3 Services**: AuthService, PolicyService
  (RBAC + ModelPolicy), IngestionService,
  RetrievalService, ChatService,
  StudioService (NOT IMPLEMENTED),
  ToolService with risk gating (NOT IMPLEMENTED),
  AuditService, LanguageService, VoiceService.
- **L4 Gateways (interfaces + DI)**:
  ModelGateway for LLMs, EmbeddingGateway,
  VectorDBGateway (pgvector default, Qdrant optional),
  STTGateway, TTSGateway, OCRGateway, ParserGateway,
  StorageGateway, TranslationGateway.
  Application code depends on abstractions, never vendors.
- **L5 Stores (strictly separated)**:
  Object Storage for bytes is distinct from
  PostgreSQL for metadata/ACLs/jobs,
  distinct from the Vector Index,
  distinct from append-only Audit Logs.
  Credentials are never shared across stores.

## 2. Enforced Request Flow

The only permitted flow is:
`User → AuthN → AuthZ → Security Policy → Query Analysis
→ Permission-filtered Retrieval → Authorized Context
→ Model Gateway → Grounded Response → Output Policy → User.`
The forbidden shortcut
`User → LLM → unrestricted store` is NEVER allowed.
The permission filter uses
`security_context = {user_id, roles, clearance,
department, project_scopes}` compiled server-side
from the session, never from client claims.

## 3. Core Services and Responsibilities

- **PolicyService**: evaluates RBAC, clearance dominance,
  department and scope matching, classification rules,
  and ModelPolicy (which classifications may use
  external models, STT, TTS, or web search).
  All rules are configuration-driven and auditable.
- **IngestionService**: orchestrates async jobs
  across VALIDATING → PARSING → OCR → CHUNKING
  → EMBEDDING → INDEXING; dispatches parser, OCR,
  chunker, embedder, indexer workers;
  never marks READY on partial failure.
- **RetrievalService**: builds filtered queries
  (ACL predicate + classification predicate
  + hybrid vector/FTS), reranks candidates,
  attaches citations, and computes evidence status.
- **ChatService**: composes delimited prompts
  with SYSTEM / USER / DOCUMENT / TOOL sections kept apart,
  calls the ModelGateway, validates citations,
  and applies output policy (labels, redaction).
- **AuditService**: appends tamper-evident events
  with IDs and page references but no full content.
  Hash-chaining is a target (NOT IMPLEMENTED).

## 4. Chat Query Sequence

1. `POST /chat` arrives with a JWT;
   AuthN resolves user, roles, clearance, scopes.
2. PolicyService checks chat permission
   and model eligibility for the classification ceiling.
3. Query Analysis detects language, intent,
   and the maximum classification in scope.
4. RetrievalService applies SQL/ACL pre-filters,
   runs hybrid search (pgvector HNSW + FTS),
   and reranks to top-n.
5. Context assembly keeps authorized chunks only,
   each tagged `{doc_id, version, page, section}`.
6. ModelGateway generates from the delimited prompt;
   an internal model is forced when policy forbids externals.
7. Output Policy checks citations and screens
   for injection patterns, then labels the answer
   INTERNAL KNOWLEDGE (vs EXTERNAL WEB).
8. The API returns answer + citations + evidence status,
   and AuditService records the event.

## 5. Technology Choices and Reasons

- **Spring Boot 3 (Java)**: typed, DI-friendly;
  Security filter chain + method security
  enforce the service boundaries of AGENTS.md §4.
- **PostgreSQL + pgvector (default)**:
  one transactional store for metadata, ACLs, and vectors;
  HNSW index plus row-level predicates
  for pre-retrieval filtering in a single query path.
- **Qdrant (optional)**: scale-out vector backend
  implementing the same VectorDBGateway contract
  with scope filters; chosen only when pgvector limits hit.
- **Async workers (Celery/RQ, TBD)**:
  long OCR/embed/index work stays off the request path;
  a jobs table tracks states with idempotent retries.
- **S3-compatible / local object storage**:
  encrypted at rest, private buckets, signed URLs only.
- **Centralized config** at `backend/app/core/config.py`
  plus env/secrets manager; `.env.example` holds placeholders.

## 6. Explicitly NOT IMPLEMENTED

Voice pipelines, Studio generation,
HIGH/CRITICAL agent tools, SIEM export,
KMS/HSM-backed encryption, and WAF rules
exist as interfaces and targets only.
No deployment topology is claimed in Phase 0.
