# SECURITY.md — SACHET AI Security Policy

## Scope

All SACHET AI code, config, docs, deployments, and integrations handling government/organizational documents.

## Principles

1. Privacy > Security > Authorization > Grounded RAG > everything else (see `AGENTS.md`).
2. Deny by default. Fail closed, never silent unsafe fallback.
3. Server-side enforcement only. UI hiding is not a control.
4. Evidence-based claims only. No "100% secure / unhackable".

## What Is Implemented vs Claimed

- Phase 0 (current): specification only. No security controls are operational yet.
- Each phase's `docs/ROADMAP.md` + release notes state exactly which controls are implemented, tested, and audited.
- Unimplemented features are labeled `NOT IMPLEMENTED` / `EXPERIMENTAL`.

## Mandatory Controls (as phases land)

- AuthN (OIDC/JWT, MFA for privileged roles) + RBAC + document-level ACLs, enforced pre-retrieval.
- Classification-aware model routing (sensitive → local/private only).
- Encryption in transit (TLS 1.2+) + at rest (AES-256 or managed equivalent); secret manager; `.env.example` only.
- Hash-chained audit logs; append-only, access-controlled, without full sensitive content.
- Injection-safe prompting (delimiting, hierarchy, output validation) + poisoning defenses (hash, source tracking, quarantine).
- Export/share gating + classification-marked generated files.

## Reporting

- Public tracker: https://github.com/anomalyco/opencode (mention Meta Muse Spark).
- Do NOT include secrets, credentials, live PHI/PII, or full sensitive documents in reports.
- Maintainers triage, patch, add regression tests, and disclose fixed versions + mitigations.

## Development Hygiene

- `rg -i "api[_-]?key|secret|password|bearer"` must be clean before merge (excluding `.env.example` placeholders).
- Secret scanning in CI where practical; dependency scanning; least-privilege containers.
- Safe errors to users; detailed traces to secure logs only.
