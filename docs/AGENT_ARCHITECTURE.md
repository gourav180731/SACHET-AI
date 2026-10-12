# SACHET AI — Agent Architecture (Phase 0 Spec)

> Status: SPECIFICATION ONLY — NOT IMPLEMENTED.
> Stack: FastAPI `/chat` + ToolService, PostgreSQL jobs/audit, ModelGateway + tool gateways.
> Controlled agency only; HIGH+ tools need confirmation + audit.

## 1. Roles: Orchestrator, Planner, Tools

- **Orchestrator** (ChatService): owns the turn — AuthN/AuthZ → Query Analysis → plan → permission-filtered retrieval → tool loop → grounded answer → Output Policy → audit.
- **Planner** (target): decomposes user intent into a DAG of tool calls with explicit inputs, expected classifications, and citation requirements. Plans stay within `security_context` scope.
- **Tools**: narrow, typed functions with no ambient authority; each receives only the authorized slice it needs.
- Memory separation holds: conversation context ≠ preferences ≠ document knowledge; tool outputs are TOOL_OUTPUT (untrusted until validated).

## 2. Tool Manifest Schema (Config-Driven)

- Registry file (target): `backend/app/tools/manifest.yaml`, loaded at startup, versioned, admin-readable.
- Required fields per tool:
  `name: str` (unique, e.g., `doc.search`, `report.build`, `studio.export_docx`) |
  `purpose: str` (one-line contract) |
  `permissions: {roles[], min_clearance, departments[], scopes[]}` |
  `inputs: JSONSchema` / `outputs: JSONSchema` |
  `risk: LOW | MEDIUM | HIGH | CRITICAL` |
  `confirmation: none | explicit | dual` |
  `classification_ceiling: PUBLIC..SECRET` |
  `external: bool` (whether it calls ModelPolicy-gated providers) |
  `audit: bool` (HIGH+ always true).
- Unknown tools, missing schema, or risk mismatch → plan rejected before execution.

## 3. Tool Inventory (Initial)

- LOW (no confirmation): `doc.search` (permission-filtered RAG), `doc.summarize` (cited), `lang.translate` (policy-gated), `lang.detect`.
- MEDIUM (explicit confirm in sensitive context): `report.build`, `studio.export_docx/pptx/pdf/csv` (see `DOCUMENT_GENERATION.md`), `workspace.write` (scoped folder only).
- HIGH (explicit confirmation + reason + audit): `studio.export` at RESTRICTED+, `share.link`, `external.web_search` (opt-in, no doc text by default), any external STT/TTS/LLM call.
- CRITICAL (dual approval + SECURITY_ADMIN + audit, default DENY): `docs.delete_protected`, `acl.rewrite`, `retention.purge`, credential-adjacent ops. No destructive tool runs silently.

## 4. Lifecycle: Approve → Check → Confirm → Execute → Audit

1. **Approve plan**: Orchestrator validates tool names exist in manifest and scopes ⊆ `security_context`.
2. **Check policy**: PolicyService evaluates role/clearance/department/scope + classification ceiling + ModelPolicy for external tools. Fail → BLOCK naming the rule.
3. **Confirm**: LOW executes; MEDIUM confirms when classification ≥ RESTRICTED or target is shared; HIGH/CRITICAL always require typed confirmation + reason string.
4. **Execute**: least-privilege call with timeouts, idempotency keys, and classification-tagged I/O; retrieval tools re-apply pre-retrieval ACL predicates.
5. **Audit**: append `{who, when, tool, inputs_hash, outputs_ref, classification, doc_ids, confirmation_id}`; failures audited with error codes, no sensitive content.

## 5. Prompt and Output Handling

- Planner/orchestrator prompts keep SYSTEM ≠ USER ≠ DOCUMENT ≠ TOOL sections delimited (see `PROMPT_SECURITY.md`); tool outputs never become instructions.
- Every tool result that makes factual claims carries citations or triggers the insufficient-evidence refusal.
- Generated artifacts carry SOURCE-DERIVED / USER-PROVIDED / MODEL-INTERPRETATION labels plus classification footer (see `DOCUMENT_GENERATION.md`).

## 6. Failure and Abuse Controls

- Timeouts, step budgets (max N tool calls/turn, TBD), and output-size caps per risk tier.
- Injection in tool output → quarantine + drop span + audit; never propagate as instruction.
- Rate limits per tool × classification; cross-tenant calls hardcoded OFF.
- Rejected plans return a safe generic message; details go to the secure log.

## 7. Testing and NOT IMPLEMENTED

- Tests (NOT IMPLEMENTED): manifest schema validation, AuthZ bypass per tool, escalation to HIGH/CRITICAL without confirmation, injection-via-tool-output, export-bypass via agent path.
- NOT IMPLEMENTED: planner model, streaming tool loop, dual-approval UX, SIEM forwarding.
