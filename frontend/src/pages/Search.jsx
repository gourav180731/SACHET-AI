import { useEffect, useState, useCallback } from "react";
import { useSearchParams, Link } from "react-router-dom";
import { searchApi } from "../api";
import { Card, Button, Select, ErrorState, EmptyState, Skeleton, Alert } from "../components/UI";

const CLASSIFICATIONS = ["PUBLIC", "INTERNAL", "RESTRICTED", "CONFIDENTIAL", "SECRET"];

const SearchIcon = <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" width="17" height="17" aria-hidden="true"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M21 21l-5.197-5.197m0 0A7.5 7.5 0 105.196 5.196a7.5 7.5 0 0010.607 10.607z" /></svg>;

export default function Search() {
  const [params, setParams] = useSearchParams();
  const initial = params.get("q") || "";
  const [query, setQuery] = useState(initial);
  const [classification, setClassification] = useState("");
  const [results, setResults] = useState(null);
  const [evidence, setEvidence] = useState(null);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const run = useCallback(async (q, cls = classification) => {
    const text = (q ?? "").trim();
    if (!text) return;
    setBusy(true);
    setError(null);
    setResults(null);
    setEvidence(null);
    try {
      const res = await searchApi.search(text, { classification: cls || undefined });
      setResults(res.results || []);
      setEvidence(res.evidenceStatus);
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }, [classification]);

  // Run immediately when navigated here from the global search box.
  useEffect(() => {
    const q = params.get("q");
    if (q) { setQuery(q); run(q, classification); }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const submit = (e) => {
    e.preventDefault();
    const text = query.trim();
    if (!text || busy) return;
    setParams({ q: text }, { replace: true });
    run(text);
  };

  const items = results || [];
  const insufficient = String(evidence || "").toLowerCase().includes("insufficient");

  return (
    <div className="ui-stack">
      <header className="ui-page-head">
        <div>
          <h1 className="ui-page-head__title">Search</h1>
          <p className="ui-page-head__sub">Retrieval only returns documents your account is authorized to read.</p>
        </div>
      </header>

      <Card elevated>
        <form onSubmit={submit} className="ui-stack ui-stack--md">
          <div className="ui-input-wrap">
            <span className="ui-input-icon ui-input-icon--left">{SearchIcon}</span>
            <label htmlFor="q" className="ui-sr-only">Search query</label>
            <input
              id="q"
              className="ui-input ui-input--icon-left"
              type="search"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search authorized knowledge…"
              disabled={busy}
            />
          </div>
          <div className="ui-row" style={{ gap: "var(--space-3)" }}>
            <div style={{ minWidth: 190 }}>
              <Select
                label="Classification filter"
                value={classification}
                onChange={(e) => setClassification(e.target.value)}
                options={[{ value: "", label: "Any classification" }, ...CLASSIFICATIONS.map((c) => ({ value: c, label: c }))]}
              />
            </div>
            <Button type="submit" variant="primary" loading={busy} disabled={!query.trim()}>
              Search
            </Button>
          </div>
        </form>
      </Card>

      {busy && (
        <Card>
          <div className="ui-stack ui-stack--sm">
            {[0, 1, 2].map((i) => <Skeleton key={i} variant="block" />)}
          </div>
        </Card>
      )}

      {error && <Card><ErrorState message={error} onRetry={() => run(query)} /></Card>}

      {evidence && !busy && (
        <Alert kind={insufficient ? "warning" : "success"} title={`Evidence: ${String(evidence).replace(/_/g, " ").toLowerCase()}`}>
          {insufficient
            ? "The backend could not find sufficient supporting passages in your authorized documents."
            : "Results below are drawn only from documents you are authorized to read."}
        </Alert>
      )}

      {!busy && !error && results !== null && items.length === 0 && (
        <Card><EmptyState title="No results">No authorized content matches this query. Try different terms or widen the classification filter.</EmptyState></Card>
      )}

      {items.length > 0 && (
        <div className="ui-stack ui-stack--md" aria-live="polite">
          <p className="ui-small ui-muted" style={{ margin: 0 }}>{items.length} result(s)</p>
          {items.map((r, i) => (
            <Card key={r.chunkId || i} hoverable>
              <div className="ui-row ui-row--between" style={{ alignItems: "flex-start" }}>
                <div style={{ minWidth: 0, flex: 1 }}>
                  <h2 style={{ fontSize: "var(--font-size-base)", fontWeight: 600, margin: 0 }}>
                    <Link to={`/documents/${r.documentId}/view?page=${r.page ?? 1}`}>
                      {r.documentTitle || r.documentId}
                    </Link>
                  </h2>
                  <p className="ui-small ui-muted" style={{ margin: "4px 0 0" }}>
                    Page {r.page ?? "—"} · Section: {r.section || "—"} · v{r.documentVersion ?? "—"}
                  </p>
                </div>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => run(r.documentTitle || query)}
                  title="Search within this document"
                >
                  Within doc
                </Button>
              </div>
            </Card>
          ))}
        </div>
      )}

      {!results && !busy && !error && (
        <Card>
          <EmptyState title="Search your authorized knowledge">
            Enter a query above. Results are grounded in your permitted documents and always carry document, page and version references.
          </EmptyState>
        </Card>
      )}
    </div>
  );
}