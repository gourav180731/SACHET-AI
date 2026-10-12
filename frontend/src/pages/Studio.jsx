import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { documentsApi } from "../api";
import { studioApi, generationApi } from "../api/phase5";
import {
  Card, Button, Tabs, LoadingState, ErrorState, Alert,
  Input, ClassificationBadge, EmptyState, useToasts, ToastContainer,
} from "../components/UI";
import ListenButton from "../components/ListenButton";

const OPS = {
  Summarize: {
    icon: <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" width="16" height="16"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M19.5 14.25v-2.625a3.375 3.375 0 00-3.375-3.375h-1.5A1.125 1.125 0 0113.5 7.125v-1.5a3.375 3.375 0 00-3.375-3.375H8.25" /></svg>,
    ops: [
      { key: "executive-summary", label: "Executive summary", desc: "Top-line outcome for decision makers" },
      { key: "detailed-summary", label: "Detailed summary", desc: "Full section-by-section walkthrough" },
      { key: "key-points", label: "Key points", desc: "Bullet list of the essentials" },
    ],
  },
  Analyze: {
    icon: <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" width="16" height="16"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M3.75 3v11.25A2.25 2.25 0 006 16.5h2.25M3.75 3h-1.5m1.5 0h16.5m0 0h1.5m-1.5 0v11.25A2.25 2.25 0 0118 16.5h-2.25m-7.5 0h7.5m-7.5 0l-1 3m8.5-3l1 3m0 0l.5 1.5m-.5-1.5h-9.5m0 0l-.5 1.5" /></svg>,
    ops: [
      { key: "entities", label: "Extract entities", desc: "People, organisations, locations, dates" },
      { key: "action-items", label: "Action items", desc: "Owners and deadlines stated in the source" },
    ],
  },
  Transform: {
    icon: <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" width="16" height="16"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M7.5 21L3 16.5m0 0L7.5 12M3 16.5h13.5m0-13.5L21 7.5m0 0L16.5 12M21 7.5H7.5" /></svg>,
    ops: [
      { key: "translate", label: "Translate", desc: "Render the content in another language" },
      { key: "explain", label: "Explain", desc: "Plain-language explanation of the topic" },
    ],
  },
  Compare: {
    icon: <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" width="16" height="16"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M9 15L3 9m0 0l6-6M3 9h18M9 9l6 6m0 0l6-6m-6 6H3" /></svg>,
    ops: [{ key: "compare", label: "Compare documents", desc: "Contrast two authorized documents" }],
  },
  Generate: {
    icon: <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" width="16" height="16"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M12 4.5v15m7.5-7.5h-15" /></svg>,
    ops: [],
  },
};

const TARGET_LANGS = ["hi", "bn", "te", "mr", "ta", "gu", "kn", "ml", "pa", "ur", "or", "as", "en"];

export default function Studio() {
  const [tab, setTab] = useState("Summarize");
  const [docs, setDocs] = useState([]);
  const [selected, setSelected] = useState([]);
  const [query, setQuery] = useState("");
  const [targetLang, setTargetLang] = useState("hi");
  const [genTitle, setGenTitle] = useState("");
  const [genSlides, setGenSlides] = useState(10);
  const [artifact, setArtifact] = useState(null);
  const [output, setOutput] = useState(null);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const [searchParams] = useSearchParams();
  const { toasts, dismiss, success, error: fail } = useToasts();

  useEffect(() => {
    documentsApi.list(0, 100).then((d) => setDocs(d.content || [])).catch(() => {});
  }, []);

  useEffect(() => {
    const preset = searchParams.get("docId");
    if (preset) setSelected((s) => (s.includes(preset) ? s : [...s, preset]));
  }, [searchParams]);

  const toggleDoc = (id) => {
    setSelected((s) => (s.includes(id) ? s.filter((x) => x !== id) : [...s, id]));
  };

  const run = async (op) => {
    if (busy) return;
    if (op === "compare" && selected.length < 2) {
      fail("Select two authorized documents to compare.", "Not enough sources");
      return;
    }
    setBusy(true);
    setError(null);
    setOutput(null);
    setArtifact(null);
    try {
      const payload = { query: query || "general summary", docIds: selected.length ? selected : undefined };
      if (op === "translate") payload.targetLang = targetLang;
      if (op === "explain") payload.topic = query;
      if (op === "compare") { payload.topic = query; payload.docIdA = selected[0]; payload.docIdB = selected[1]; }
      const res = await studioApi.run(op, payload);
      setOutput(res.output);
      success("Grounded output generated from authorized sources.", "Operation complete");
    } catch (e) {
      setError(e.message);
      fail(e.message, "Operation failed");
    } finally {
      setBusy(false);
    }
  };

  const generate = async (kind) => {
    if (busy) return;
    if (selected.length === 0) {
      fail("Select at least one authorized source document.", "No source selected");
      return;
    }
    setBusy(true);
    setError(null);
    setOutput(null);
    setArtifact(null);
    try {
      const payload = {
        title: genTitle || "SACHET AI Briefing",
        topic: query || "general summary",
        docIds: selected,
        slides: genSlides,
      };
      const res = kind === "pptx" ? await generationApi.pptx(payload) : await generationApi.docx(payload);
      setArtifact(res);
      success(`${res.type} artifact created.`, "Artifact generated");
    } catch (e) {
      setError(e.message);
      fail(e.message, "Generation failed");
    } finally {
      setBusy(false);
    }
  };

  const current = OPS[tab];
  const selectedDocs = docs.filter((d) => selected.includes(d.id));

  return (
    <div className="ui-stack">
      <ToastContainer toasts={toasts} onDismiss={dismiss} />

      <header className="ui-page-head">
        <div>
          <h1 className="ui-page-head__title">Document Studio</h1>
          <p className="ui-page-head__sub">Grounded outputs from authorized sources. Operations are permission-filtered server-side.</p>
        </div>
      </header>

      <Card
        title="Source documents"
        subtitle={selected.length ? `${selected.length} selected` : "Select the documents this operation may draw from"}
        actions={selected.length > 0 ? <Button variant="ghost" size="sm" onClick={() => setSelected([])}>Clear</Button> : null}
      >
        {docs.length === 0 ? (
          <EmptyState title="No authorized documents" action={<Link className="ui-btn ui-btn--primary ui-btn--sm" to="/knowledge">Upload a document</Link>}>
            You need at least one authorized document before you can run a grounded operation.
          </EmptyState>
        ) : (
          <div style={{ maxHeight: 230, overflowY: "auto" }}>
            {docs.map((d) => (
              <label key={d.id} className="ui-checkbox-row">
                <input type="checkbox" className="ui-checkbox" checked={selected.includes(d.id)} onChange={() => toggleDoc(d.id)} />
                <span style={{ display: "flex", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
                  <span>{d.title || d.sourceFilename}</span>
                  <ClassificationBadge value={d.classification} size="xs" dot={false} />
                </span>
              </label>
            ))}
          </div>
        )}
      </Card>

      <Card>
        <Tabs tabs={Object.keys(OPS)} active={tab} onChange={setTab} ariaLabel="Studio operations" />

        <div className="ui-stack ui-stack--md">
          <Input
            label="Topic / query"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="e.g. emergency communication procedures"
            hint="Used as the instruction for the selected operation."
          />

          {tab === "Transform" && (
            <Input
              label="Target language"
              value={targetLang}
              onChange={(e) => setTargetLang(e.target.value)}
              list="lang-list"
              placeholder="e.g. hi"
              style={{ maxWidth: 200 }}
              hint="ISO 639-1 code, or any of the supported languages."
            />
          )}
          <datalist id="lang-list">
            {TARGET_LANGS.map((l) => <option key={l} value={l} />)}
          </datalist>

          {tab === "Compare" && (
            <Alert kind={selected.length < 2 ? "warning" : "info"}>
              {selected.length < 2
                ? `Select exactly two documents to compare (${selected.length} selected).`
                : `Comparing: ${selectedDocs.map((d) => d.title || d.sourceFilename).join("  vs  ")}`}
            </Alert>
          )}

          {tab === "Analyze" && (
            <Alert kind="info">
              Risk extraction, recommendations and timeline extraction are NOT IMPLEMENTED in the current backend build.
            </Alert>
          )}

          {tab === "Generate" ? (
            <div className="ui-stack ui-stack--md">
              <Input label="Artifact title" value={genTitle} onChange={(e) => setGenTitle(e.target.value)} placeholder="Executive briefing" />
              <Input
                label="Slides (PPTX only)"
                type="number"
                min={1}
                max={30}
                value={genSlides}
                onChange={(e) => setGenSlides(Math.min(30, Math.max(1, Number(e.target.value) || 1)))}
                style={{ maxWidth: 160 }}
              />
              <div className="ui-row">
                <Button variant="primary" onClick={() => generate("docx")} disabled={busy || selected.length === 0} loading={busy}>
                  Generate DOCX
                </Button>
                <Button variant="primary" onClick={() => generate("pptx")} disabled={busy || selected.length === 0} loading={busy}>
                  Generate PPTX
                </Button>
              </div>
              <Alert kind="info">
                Artifacts inherit the strictest classification of their source documents. Generation is audited.
              </Alert>
            </div>
          ) : (
            <div className="ui-grid ui-grid--3">
              {current.ops.map((op) => (
                <button key={op.key} type="button" className="studio-op" onClick={() => run(op.key)} disabled={busy}>
                  <span className="studio-op__title">{current.icon}{op.label}</span>
                  <span className="studio-op__desc">{op.desc}</span>
                </button>
              ))}
            </div>
          )}

          {busy && <LoadingState label="Generating grounded output…" />}
          {error && <ErrorState message={error} onRetry={() => run(OPS[tab].ops[0]?.key)} />}

          {artifact && (
            <Alert kind="success" title={`${artifact.type} created: ${artifact.title}`}>
              {artifact.classification} · {artifact.size} bytes — <Link to="/generated">open in Generated Files</Link>
            </Alert>
          )}

          {output && (
            <div>
              <div className="ui-row ui-row--between" style={{ marginBottom: "var(--space-2)" }}>
                <h3 style={{ fontSize: "var(--font-size-sm)", fontWeight: 600, margin: 0 }}>Result</h3>
                <div className="ui-row" style={{ gap: 8, alignItems: "center" }}>
                  <ListenButton text={output} />
                  <Button variant="ghost" size="sm" onClick={() => navigator.clipboard?.writeText(output)}>Copy</Button>
                </div>
              </div>
              <pre className="ui-output">{output}</pre>
            </div>
          )}
        </div>
      </Card>
    </div>
  );
}