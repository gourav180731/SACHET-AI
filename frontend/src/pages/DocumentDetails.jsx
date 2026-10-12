import { useEffect, useState, useCallback } from "react";
import { useParams, Link } from "react-router-dom";
import { documentsApi } from "../api";
import { apiDownload } from "../api/client";
import {
  Card, ClassificationBadge, StatusBadge, LoadingState, ErrorState,
  Button, DefList, Skeleton, EmptyState, useToasts, ToastContainer,
} from "../components/UI";

const ACTION = {
  open: { label: "Open reader", desc: "Read the indexed content page by page", to: (id) => `/documents/${id}/view` },
  ask: { label: "Ask AI", desc: "Query grounded answers against this document", to: (id) => `/assistant?docId=${id}` },
  summarize: { label: "Summarize", desc: "Generate a grounded summary", to: (id) => `/studio?docId=${id}` },
  compare: { label: "Compare", desc: "Compare against another authorized document", to: (id) => `/studio?docId=${id}` },
  translate: { label: "Translate", desc: "Translate to a target language", to: (id) => `/studio?docId=${id}` },
  brief: { label: "Generate briefing", desc: "Produce a briefing artifact", to: (id) => `/studio?docId=${id}` },
};

export default function DocumentDetails() {
  const { id } = useParams();
  const [doc, setDoc] = useState(null);
  const [chunks, setChunks] = useState(null);
  const [error, setError] = useState(null);
  const [downloading, setDownloading] = useState(false);
  const { toasts, dismiss, success, error: fail } = useToasts();

  const load = useCallback(() => {
    setDoc(null);
    setChunks(null);
    setError(null);
    documentsApi.get(id).then(setDoc).catch((e) => setError(e.message));
    documentsApi.chunks(id, 0, 50).then(setChunks).catch(() => setChunks({ content: [] }));
  }, [id]);

  useEffect(() => { load(); }, [load]);

  const download = async () => {
    setDownloading(true);
    try {
      await apiDownload(`/documents/${id}/download`, doc?.sourceFilename || "document");
      success("Download started. The action is recorded in the audit log.", "Download authorized");
    } catch (e) {
      fail(e.message, "Download failed");
    } finally {
      setDownloading(false);
    }
  };

  if (error) return <ErrorState message={error} onRetry={load} />;
  if (!doc) return <LoadingState label="Loading document…" />;

  const chunkList = chunks?.content || [];

  return (
    <div className="ui-stack">
      <ToastContainer toasts={toasts} onDismiss={dismiss} />

      <nav aria-label="Breadcrumb" className="ui-small ui-muted">
        <Link to="/documents">Documents</Link>
        <span aria-hidden="true" style={{ margin: "0 6px" }}>/</span>
        <span>{doc.title || doc.sourceFilename}</span>
      </nav>

      <header className="ui-page-head">
        <div style={{ minWidth: 0 }}>
          <h1 className="ui-page-head__title ui-truncate">{doc.title || doc.sourceFilename}</h1>
          <div className="ui-row" style={{ marginTop: 8, gap: "var(--space-2)" }}>
            <ClassificationBadge value={doc.classification} />
            <StatusBadge status={doc.status} />
          </div>
        </div>
        <Button variant="primary" onClick={download} loading={downloading}>
          Download original
        </Button>
      </header>

      <div className="ui-grid ui-grid--2">
        <Card title="Actions" subtitle="Every action below is permission-checked server-side">
          <div className="ui-stack ui-stack--sm">
            {Object.entries(ACTION).map(([key, a]) => (
              <Link key={key} to={a.to(id)} className="ui-tile">
                <span className="ui-tile__body">
                  <span className="ui-tile__title">{a.label}</span>
                  <span className="ui-tile__desc">{a.desc}</span>
                </span>
                <span className="ui-tile__arrow" aria-hidden="true">
                  <svg fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8.25 4.5l7.5 7.5-7.5 7.5" /></svg>
                </span>
              </Link>
            ))}
          </div>
        </Card>

        <Card title="Metadata" subtitle="Integrity and provenance">
          <DefList items={[
            ["Filename", doc.sourceFilename],
            ["Owner", doc.ownerId],
            ["Department", doc.department],
            ["Classification", doc.classification],
            ["Status", doc.status],
            ["Version", doc.version],
            ["SHA-256", <span className="ui-mono ui-break">{doc.sha256 || "—"}</span>],
            ["MIME type", doc.mimeType],
            ["Pages", doc.pageCount ?? "—"],
            ["Chunks", doc.chunkCount ?? "—"],
            ["Created", doc.createdAt ? new Date(doc.createdAt).toLocaleString() : "—"],
          ]} />
        </Card>
      </div>

      <Card title="Indexed content" subtitle={chunks ? `${chunkList.length} chunk(s) on this page` : "Loading"}>
        {!chunks && (
          <div className="ui-stack ui-stack--sm">
            {[0, 1, 2, 3].map((i) => <Skeleton key={i} variant="block" />)}
          </div>
        )}
        {chunks && chunkList.length === 0 && (
          <EmptyState title="No indexed content" action={<Link className="ui-btn ui-btn--primary ui-btn--sm" to="/documents">Back to documents</Link>}>
            The document may still be processing, or it produced no extractable text.
          </EmptyState>
        )}
        {chunkList.map((c) => (
          <div key={c.id} className="viewer-chunk">
            <p className="ui-small ui-muted" style={{ margin: 0 }}>
              Chunk {c.chunkIndex} · Page {c.pageNumber ?? "—"}{c.section ? ` · ${c.section}` : ""}
            </p>
            <p className="viewer-page" style={{ margin: "6px 0 0" }}>{c.text}</p>
          </div>
        ))}
      </Card>
    </div>
  );
}