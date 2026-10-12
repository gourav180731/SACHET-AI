# SACHET AI — Document Generation / Studio (Phase 0 Spec)

> Status: SPECIFICATION ONLY — NOT IMPLEMENTED.
> Stack: FastAPI `/studio` + StudioService, PostgreSQL jobs, template store + generation gateways.
> Grounded-only: every factual claim traces to authorized sources or is labeled.

## 1. Scope and Formats

- Studio produces DOCX, PPTX, PDF, TXT, CSV from authorized RAG context + explicit user inputs.
- Endpoint family: `POST /studio/{docx,pptx,pdf,txt,csv}` → async job `QUEUED → RENDERING → POLICY_CHECK → READY | FAILED`.
- Generation is MEDIUM risk by default, HIGH when classification ≥ RESTRICTED or output is shared/exported (confirmation + audit required).
- No format bypasses AuthZ, ModelPolicy, or Output Policy; frontend export buttons are not enforcement.

## 2. Templates

- Versioned templates in a controlled store: `{template_id, version, format, locale, styles, header/footer slots, provenance-page layout}`.
- Templates are config, not code: layout changes need REVIEWER approval + regression render test.
- Multilingual templates per `languages.supported[]`; fallback to English template with explicit notice, never silent restyle.
- Malware/active-content rules: no macros in DOCX, no embedded executables, PDF linearized, CSV formula-prefix sanitized (`=,+,-,@` escaped).

## 3. Grounded-Only Rule

- Every factual sentence/number/table cell in output must cite `{document_id, version, page, section}` or carry an interpretation label (§4).
- Citation check runs pre-render: claims without sources → block or force the standard refusal text into the output.
- Retrieval for Studio reuses permission-filtered RAG (pgvector + FTS with ACL predicates BEFORE scoring); post-retrieval ACL re-check precedes context assembly.
- Version skew is stamped inline ("Source v2, current v3 — see §…") rather than silently updated.

## 4. Content Labels (Mandatory)

- `SOURCE-DERIVED`: verbatim or close paraphrase with citation attached.
- `USER-PROVIDED`: text supplied by the user in this session (rendered distinctly, never cited as archive evidence).
- `MODEL-INTERPRETATION`: synthesis, inference, or formatting judgment — requires REVIEWER sign-off at CONFIDENTIAL+ and always labeled.
- Mixed paragraphs carry the strictest applicable label; unlabeled factual claims fail validation.

## 5. Classification Footer + Provenance Page

- Every page/slide carries a footer: `Classification: <LEVEL> | Doc: <output_id> | <date> | INTERNAL KNOWLEDGE`.
- Final provenance page lists all sources `{doc_id, version, page, section, classification}`, models used `{llm, embedding}`, translation pairs if any, and generating user/roles.
- Export filename encodes classification (e.g., `report_RESTRICTED_v3.pdf`); downgraded copies are new outputs, never renames.

## 6. Export Permission Checks

- Enforced server-side in StudioService: role ∈ template.allowed_roles AND clearance dominates output classification AND department/scope match AND export quota OK.
- HIGH-risk exports need explicit confirmation + reason; SECRET needs OFFICER/REVIEWER approval per `ACCESS_CONTROL.md`.
- ModelPolicy gates any external model/translation in the render path; BLOCK names the rule, never silently falls back to external.
- All exports audited with `{who, when, output_id, format, classification, source_doc_ids, pages, model}` without full content.

## 7. Failure Handling and Multilingual Notes

- Renderer failure → job FAILED with explicit error code + retry once; never returns a half-rendered file as READY.
- Policy BLOCK mid-render discards partial bytes, audits `{output_id, rule_id, classification}`, and notifies with the generic safe message.
- Multilingual: layout mirrors locale reading order where templates support it; transliterated names keep original script alongside; translation provenance logged per §5.
- CSV/TXT outputs carry a header comment line with classification + output_id; PPTX speaker notes carry citations, not just slide body.

## 8. Testing and NOT IMPLEMENTED

- Tests (NOT IMPLEMENTED): cross-user source leak via generation, citation-drop under translation/rerank, CSV formula injection, macro rejection, footer/provenance presence, classification-escalation bypass.
- Regression: template version bump re-renders golden fixtures across DOCX/PPTX/PDF/TXT/CSV before publish.
- Audit retention follows source ceiling level; SECRET Studio jobs need isolated render workers per `DATA_CLASSIFICATION.md`.
- NOT IMPLEMENTED: renderer library choice, visual diff harness, batch bulk-export, e-sign integration.
