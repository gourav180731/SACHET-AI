import { useEffect, useState, useCallback } from "react";
import { artifactApi } from "../api/phase5";
import { apiDownload } from "../api/client";
import {
  Card, EmptyState, ErrorState, ClassificationBadge,
  Pagination, Button, ConfirmDialog, useToasts, ToastContainer, Skeleton,
} from "../components/UI";

const PAGE_SIZE = 20;

export default function Generated() {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [confirm, setConfirm] = useState(null);
  const [busyId, setBusyId] = useState(null);
  const { toasts, dismiss, success, error: fail } = useToasts();

  const load = useCallback((p = 0) => {
    setLoading(true);
    setError(null);
    artifactApi.list(p, PAGE_SIZE)
      .then((d) => {
        setItems(d.content || []);
        setPage(d.page ?? p);
        setTotalPages(d.totalPages ?? 1);
      })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => { load(0); }, [load]);

  const download = async (a) => {
    setBusyId(a.id);
    try {
      const ext = a.type === "PPTX" ? "pptx" : "docx";
      await apiDownload(`/artifacts/${a.id}/download`, `${a.title.replace(/[^A-Za-z0-9._-]/g, "_")}.${ext}`);
      success(`"${a.title}" download started and was audited.`, "Download started");
    } catch (e) {
      fail(e.message, "Download failed");
    } finally {
      setBusyId(null);
    }
  };

  const remove = async () => {
    if (!confirm) return;
    setBusyId(confirm.id);
    try {
      await artifactApi.remove(confirm.id);
      success(`"${confirm.title}" was deleted.`, "Artifact deleted");
      setConfirm(null);
      load(page);
    } catch (e) {
      fail(e.message, "Delete failed");
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div className="ui-stack">
      <ToastContainer toasts={toasts} onDismiss={dismiss} />

      <header className="ui-page-head">
        <div>
          <h1 className="ui-page-head__title">Generated files</h1>
          <p className="ui-page-head__sub">Artifacts inherit the strictest classification of their source documents. Downloads are authorized and audited.</p>
        </div>
        <Button variant="secondary" size="sm" onClick={() => load(page)} disabled={loading}>Refresh</Button>
      </header>

      <Card padding="none" elevated>
        {loading && (
          <div style={{ padding: "var(--space-5)" }} className="ui-stack ui-stack--sm">
            {[0, 1, 2].map((i) => <Skeleton key={i} variant="row" />)}
          </div>
        )}

        {error && <div style={{ padding: "var(--space-5)" }}><ErrorState message={error} onRetry={() => load(page)} /></div>}

        {!loading && !error && items.length === 0 && (
          <div style={{ padding: "var(--space-5)" }}>
            <EmptyState title="No generated files yet">
              Generate DOCX or PPTX artifacts from the Document Studio.
            </EmptyState>
          </div>
        )}

        {!loading && !error && items.length > 0 && (
          <>
            <div className="ui-table-wrap">
              <table className="ui-table">
                <thead>
                  <tr>
                    <th>File name</th><th>Type</th><th>Sources</th><th>Created</th><th>Classification</th>
                    <th style={{ textAlign: "right" }}>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {items.map((a) => (
                    <tr key={a.id}>
                      <td className="ui-table__title">{a.title}</td>
                      <td><span className="ui-badge ui-badge--xs ui-badge--default">{a.type}</span></td>
                      <td className="ui-small ui-muted">{(a.sourceDocIds || []).length} document(s)</td>
                      <td className="ui-small ui-muted">{a.createdAt ? new Date(a.createdAt).toLocaleString() : "—"}</td>
                      <td><ClassificationBadge value={a.classification} size="sm" /></td>
                      <td>
                        <div className="ui-table__actions">
                          <Button variant="secondary" size="sm" onClick={() => download(a)} loading={busyId === a.id}>Download</Button>
                          <Button variant="danger-outline" size="sm" onClick={() => setConfirm(a)} disabled={busyId === a.id}>Delete</Button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div style={{ padding: "0 var(--space-5) var(--space-4)" }}>
              <Pagination page={page} totalPages={totalPages} onChange={(p) => { setPage(p); load(p); }} />
            </div>
          </>
        )}
      </Card>

      <ConfirmDialog
        isOpen={!!confirm}
        title="Delete this artifact?"
        message={`"${confirm?.title}" will be permanently removed. This cannot be undone and the action is audited.`}
        confirmLabel="Delete permanently"
        loading={busyId === confirm?.id}
        onConfirm={remove}
        onCancel={() => setConfirm(null)}
      />
    </div>
  );
}