# SACHET AI — Access Control (Phase 0 Spec)

> Status: Phase 2 implemented (DB roles/permissions/grants, clearance gate, deny-by-default `DocumentAccessService`).
> Enforced server-side on every Spring route, retrieval call, export, voice request, and tool execution.
> Frontend hiding is NOT security; all checks are re-evaluated per request.

## 1. Roles (Configurable)

- Default set: `USER | ANALYST | OFFICER | REVIEWER | ADMIN | SECURITY_ADMIN` — operator-overridable via `auth.roles[]` in `backend/app/core/config.py`.
- `USER`: chat/search over authorized docs, own workspace only.
- `ANALYST`: + bulk search, saved views, Studio MEDIUM-risk generation.
- `OFFICER`: + approve RESTRICTED exports, request downgrade/upgrade review.
- `REVIEWER`: + approve CONFIDENTIAL outputs, template approval, evidence sign-off.
- `ADMIN`: user/role assignment (except SECURITY_ADMIN), template publish, retention config — cannot read SECRET content without grant.
- `SECURITY_ADMIN`: policy/ACL/ModelPolicy changes, SECRET grants, dual-approval second sign — all actions audited, no self-grant.
- Custom roles map to capability bundles; unknown roles default to no-access.

## 2. Clearance vs Department vs Project Scopes

- **Clearance** (dominance lattice, configurable order): `PUBLIC < INTERNAL < RESTRICTED < CONFIDENTIAL < SECRET`. Access needs `user.clearance ≥ doc.clearance`.
- **Department**: set match — `user.departments ∩ doc.departments ≠ ∅` unless doc is cross-department with explicit flag + OFFICER approval.
- **Project scopes**: set match — `user.project_scopes ⊇ doc.projects` required slice (least privilege); expired scopes fail closed.
- All three must pass (AND) plus role check; failure on any dimension denies with a generic 403.

## 3. ACL Model (PostgreSQL-Backed)

- Per-document row: `{doc_id, version, clearance, departments[], projects[], allowed_roles[], owner, embargo_until, cross_tenant: false}`.
- Per-chunk snapshot ref: `chunks.acl_snapshot_ref → acl_snapshots{id, hash}` so version bumps preserve history.
- Deny-list overlays (e.g., named-user exclusion, legal hold) override allows; holds block export/delete regardless of role.
- ACL edits are versioned admin events requiring OFFICER+ (SECRET: SECURITY_ADMIN dual), never inline mutation of history.

## 4. security_context Shape (Server-Side Only)

- Built per request from session/JWT + DB: `{user_id, roles[], clearance, department[], project_scopes[], session_id, mfa_passed}`.
- Client-supplied roles/clearance are ignored; tampered claims fail AuthN.
- Propagated through services → gateways → audit; never serialized to the client except `roles` display subset.
- Compiled to SQL predicates for pgvector + FTS pre-filtering AND re-checked post-retrieval before LLM context.

## 5. Enforcement Points

- Spring Security: JWT filter → `SachetPrincipal`, `@PreAuthorize` role checks on `/auth /documents /chat /studio /voice /admin`.
- RetrievalService: ACL + classification predicates BEFORE vector scoring; unauthorized vectors never scored.
- StudioService/ToolService: per-tool manifest permission check + classification ceiling + confirmation gating.
- StorageGateway: signed-URL issuance checks ACL at issue time with short TTL; direct bucket access denied.
- Every denial returns generic 403/404 (existence hiding for unauthorized docs); details go to the secure log.

## 6. Negative Rules (Never Allowed)

- No global vector search ignoring `security_context`; no client-side-only filtering.
- No cross-tenant retrieval (hardcoded OFF, no config toggle for tenants).
- No ADMIN-or-higher self-escalation; no scope widening via prompt, tool, or translation.
- No export/share/external call above ModelPolicy ceiling, even with user confirmation.
- No caching of authorized chunks in shared caches keyed without `user_id + scopes + classification`.

## 7. Endpoint Matrix and Config Keys

- Matrix (target `backend/app/core/access_matrix.yaml`): each route declares `{path, method, required_roles[], min_clearance, scope_check, mfa_required}`.
- Examples: `POST /chat` needs USER+ with chat scope; `POST /studio/*` needs ANALYST+; `POST /admin/acl` needs SECURITY_ADMIN + MFA.
- Config keys: `auth.roles[]`, `auth.clearance_order[]`, `auth.session_ttl`, `auth.mfa_required_for[]`, `access.embargo_enforced` (default true).
- Secrets via env/secrets manager only; `.env.example` holds placeholders; `rg -i "api[_-]?key|secret|password"` must be clean.

## 8. Testing (NOT IMPLEMENTED)

- Matrix tests: cross-user read, escalation (USER→ADMIN), expired scope, embargo, deny-list, SECRET isolation, voice/studio/tool bypass attempts — all must assert 403 + audit event.
- Regression: every new endpoint adds its matrix row + negative test before merge; audit-log tamper checks run in CI where practical.
