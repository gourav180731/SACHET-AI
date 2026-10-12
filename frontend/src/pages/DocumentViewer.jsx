import { useEffect, useState, useMemo, useCallback } from "react";
import { useParams, useSearchParams, Link } from "react-router-dom";
import { documentsApi } from "../api";
import { Card, LoadingState, ErrorState, Button, EmptyState, Skeleton, Badge } from "../components/UI";

const escapeRegExp = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

export default function DocumentViewer() {
  const { id } = useParams();
  const [params, setParams] = useSearchParams();
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [filter, setFilter] = useState("");
  const [pageIdx, setPageIdx] = useState(0);
  const [zoom, setZoom] = useState(100);

  const initialPage = Number(params.get("page")) || null;

  const load = useCallback(() => {
    setData(null);
    setError(null);
    documentsApi.chunks(id, 0, 100).then(setData).catch((e) => setError(e.message));
  }, [id]);

  useEffect(() => { load(); }, [load]);

  const pages = useMemo(() => {
    if (!data) return [];
    const map = new Map();
    for (const c of data.content || []) {
      const p = c.pageNumber ?? 0;
      map.set(p, [...(map.get(p) || []), c]);
    }
    return [...map.entries()].sort((a, b) => a[0] - b[0]);
  }, [data]);

  // Jump to the page requested via the citation deep link.
  useEffect(() => {
    if (initialPage && pages.length) {
      const idx = pages.findIndex(([p]) => p === initialPage);
      if (idx >= 0) setPageIdx(idx);
    }
  }, [pages, initialPage]);

  if (error) return <ErrorState message={error} onRetry={load} />;
  if (!data) return <LoadingState label="Loading document…" />;

  if (pages.length === 0) {
    return (
      <Card>
        <EmptyState title="No content available" action={<Link className="ui-btn ui-btn--secondary ui-btn--sm" to="/documents">Back to documents</Link>}>
          This document has no indexed text to display.
        </EmptyState>
      </Card>
    );
  }

  const safeIdx = Math.min(pageIdx, pages.length - 1);
  const [pageNum, chunks] = pages[safeIdx];
  const term = filter.trim();
  const visible = term ? chunks.filter((c) => c.text?.toLowerCase().includes(term.toLowerCase())) : chunks;
  const totalMatches = term
    ? pages.reduce((sum, [, cs]) => sum + cs.filter((c) => c.text?.toLowerCase().includes(term.toLowerCase())).length, 0)
    : 0;

  const goto = (next) => {
    const i = Math.max(0, Math.min(pages.length - 1, next));
    setPageIdx(i);
    setParams({ page: String(pages[i][0]) }, { replace: true });
  };

  const highlight = (text) => {
    if (!term || !text) return text;
    const re = new RegExp(`(${escapeRegExp(term)})`, "ig");
    return text.split(re).map((part, i) =>
      part.toLowerCase() === term.toLowerCase() ? <mark key={i}>{part}</mark> : part
    );
  };

  return (
    <div className="ui-stack">
      <nav aria-label="Breadcrumb" className="ui-small ui-muted">
        <Link to="/documents">Documents</Link>
        <span aria-hidden="true" style={{ margin: "0 6px" }}>/</span>
        <Link to={`/documents/${id}`}>Details</Link>
        <span aria-hidden="true" style={{ margin: "0 6px" }}>/</span>
        <span>Reader</span>
      </nav>

      <Card padding="none" elevated>
        <div className="viewer-toolbar" style={{ padding: "var(--space-4) var(--space-5) 0", marginBottom: 0, borderBottom: "none" }}>
          <Button variant="secondary" size="sm" onClick={() => goto(safeIdx - 1)} disabled={safeIdx === 0}>
            <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" width="15" height="15" aria-hidden="true"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15.75 19.5L8.25 12l7.5-7.5" /></svg>
            Previous
          </Button>
          <Badge variant="primary" size="sm">Page {pageNum} · {safeIdx + 1}/{pages.length}</Badge>
          <Button variant="secondary" size="sm" onClick={() => goto(safeIdx + 1)} disabled={safeIdx >= pages.length - 1}>
            Next
            <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" width="15" height="15" aria-hidden="true"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8.25 4.5l7.5 7.5-7.5 7.5" /></svg>
          </Button>

          <div className="ui-spacer" />

          <div className="ui-row" style={{ gap: 6 }}>
            <Button variant="ghost" size="sm" onClick={() => setZoom((z) => Math.max(70, z - 10))} disabled={zoom <= 70} aria-label="Zoom out">A−</Button>
            <span className="ui-small ui-muted" style={{ minWidth: 42, textAlign: "center" }} aria-live="polite">{zoom}%</span>
            <Button variant="ghost" size="sm" onClick={() => setZoom((z) => Math.min(200, z + 10))} disabled={zoom >= 200} aria-label="Zoom in">A+</Button>
          </div>
        </div>

        <div style={{ padding: "var(--space-4) var(--space-5)" }}>
          <div className="ui-input-wrap">
            <label htmlFor="within" className="ui-sr-only">Search within this document</label>
            <input
              id="within"
              type="search"
              className="ui-input"
              placeholder="Search within this document…"
              value={filter}
              onChange={(e) => setFilter(e.target.value)}
            />
          </div>
          {term && (
            <p className="ui-small ui-muted" style={{ margin: "8px 0 0" }} role="status">
              {totalMatches === 0
                ? `No matches for "${term}" in this document.`
                : `${totalMatches} match(es) for "${term}" in this document.`}
            </p>
          )}
        </div>

        <div style={{ padding: "0 var(--space-5) var(--space-5)" }}>
          {term && visible.length === 0 && (
            <div className="ui-stack ui-stack--sm" style={{ marginTop: "var(--space-4)" }}>
              <Skeleton variant="block" />
            </div>
          )}
          {visible.map((c) => (
            <div key={c.id} className="viewer-chunk">
              {c.section && <p className="ui-small ui-muted" style={{ margin: 0 }}>{c.section}</p>}
              <p className="viewer-page" style={{ margin: c.section ? "6px 0 0" : 0, fontSize: `${zoom}%` }}>
                {highlight(c.text)}
              </p>
            </div>
          ))}
        </div>
      </Card>
    </div>
  );
}