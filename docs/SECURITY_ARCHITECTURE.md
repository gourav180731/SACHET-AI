# SACHET AI — Security Architecture (Phase 0 Spec)

> Status: SPECIFICATION ONLY — NOT IMPLEMENTED.
> No guarantees claimed; controls and limits are listed.
> Deny by default; explicit failure beats unsafe fallback.

## 1. Trust Boundaries

1. **Internet ↔ API**: TLS 1.2+, short-lived JWTs
   with refresh rotation, rate limits,
   and a WAF in front (WAF NOT IMPLEMENTED).
2. **API ↔ Services**: `security_context`
   propagates server-side only;
   client-supplied roles are ignored.
3. **Services ↔ Stores**: distinct least-privilege
   credentials per store; no direct UI-to-database path.
4. **App ↔ External providers**
   (LLM / STT / TTS / search): every call passes
   the ModelPolicy + classification gate first.
   Default is DENY for RESTRICTED and above.
   Forbidden calls BLOCK with an explicit error.
5. **Ingestion ↔ Parsers/OCR**: untrusted bytes
   are sandboxed with size, time, and page caps,
   plus a malware-scan hook.

## 2. Authentication (AuthN)

- OIDC/OAuth2 or an internal IdP (choice TBD).
- MFA required for admins and SECRET handlers
  (NOT IMPLEMENTED in Phase 0).
- Passwords hashed with Argon2/bcrypt;
  sessions use httpOnly secure cookies
  or short-TTL Bearer JWTs.
- Service-to-service auth uses mTLS
  or scoped API keys from the secrets manager
  (NOT IMPLEMENTED).

## 3. Authorization (AuthZ)

- **RBAC model**: roles (viewer, editor,
  uploader, auditor, admin) intersected with
  clearance (PUBLIC → SECRET), department,
  and project_scopes.
- Access needs clearance dominance AND scope match.
- **Document ACLs**: each document stores
  `{clearance, departments[], projects[], allowed_roles[]}`
  in PostgreSQL.
- ACLs are enforced as SQL predicates pre-retrieval
  AND re-checked post-retrieval before LLM context assembly.
- Every route declares its required role/clearance
  in an endpoint matrix; escalation tests must assert
  403 responses (matrix specified, tests NOT IMPLEMENTED).

## 4. Model, Storage, and Network Boundaries

- **ModelPolicy**: maps each classification to
  `{internal_only | approved_external[] | blocked}`.
  Checked before ModelGateway, VoiceGateway,
  TranslationGateway, and the web-search tool.
- **Storage**: object bytes encrypted at rest
  (AES-256; KMS/HSM integration TBD, NOT IMPLEMENTED);
  metadata in PostgreSQL; vectors in pgvector/Qdrant
  carrying tenant and scope filter fields.
- **Network**: databases, vector stores, and object
  storage live in private subnets with an egress allowlist.
  Document text is never sent to search engines.
  Web access is a separate opt-in tool (NOT IMPLEMENTED).

## 5. Encryption and Secrets

- TLS everywhere in transit,
  including internal service traffic (target).
- Encryption at rest for storage, database,
  and backups (provider TBD, NOT IMPLEMENTED).
- Secrets come from env/secrets manager only.
- `.env.example` carries placeholders, never real values.
- CI runs secret scanning where practical;
  `rg -i "api[_-]?key|secret|password"` must be clean.

## 6. DLP and Output Policy

- Redaction patterns cover ID numbers,
  credentials, and key-like strings.
- Every response carries its classification label.
- Citation integrity is checked:
  factual claims need sources or the standard refusal
  ("I could not find sufficient supporting information…").
- Document content stays delimited;
  instruction-like spans inside docs are treated as data.
- Outputs are validated before returning to the user.

## 7. Export Control and External API Policy

- Export, share, and external calls are HIGH risk:
  they need explicit user confirmation, a reason,
  classification labeling, and an audit event.
- Calls above the policy threshold BLOCK
  with a message naming the violated rule.
- Tool risk tiers: LOW (search/summarize/translate),
  MEDIUM (report/PPT/workspace write),
  HIGH (sensitive export/share/external),
  CRITICAL (destructive/credential/delete-protected).
- HIGH and above require confirmation + audit;
  CRITICAL additionally targets dual approval.

## 8. Honest Limitations

- No certified robustness against targeted extraction;
  rate limits and auditing mitigate but do not prove safety.
- The malware-scan hook is an interface;
  the actual AV engine is NOT IMPLEMENTED.
- KMS/HSM, WAF, SIEM forwarding, and MFA rollout
  are NOT IMPLEMENTED in Phase 0.
- Never claim "100% secure" or "unhackable".
