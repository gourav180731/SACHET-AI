import { useEffect, useState, useCallback } from "react";
import { Link } from "react-router-dom";
import { documentsApi } from "../api";
import {
  Card, ClassificationBadge, StatusBadge, ErrorState, EmptyState,
  Skeleton, Pagination, Button, ConfirmDialog, useToasts, ToastContainer, Tooltip,
} from "../components/UI";

const PAGE_SIZE = 20;

const TrashIcon = <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" width="15" height="15" aria-hidden="true"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M14.74 9l-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 01-2.244 2.077H8.084a2.25 2.25 0 01-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 00-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 013.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 00-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 00-7.5 0" /></svg>;

export default function Documents() {
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [confirm, setConfirm] = useState(null);
  const [deletingId, setDeletingId] = useState(null);
  const { toasts, dismiss, success, error: fail } = useToasts();

  const load = useCallback((p) => {
    setData(null);
    setError(null);
    documentsApi.list(p, PAGE_SIZE).then(setData).catch((e) => setError(e.message));
  }, []);

  useEffect(() => { load(page); }, [page, load]);

  const handleDelete = async () => {
    if (!confirm) return;
    const id = confirm.id;
    setDeletingId(id);
    try {
      await documentsApi.delete(id);
      setData((prev) => (prev ? { ...prev, content: prev.content.filter((d) => d.id !== id), totalElements: (prev.totalElements || 1) - 1 } : prev));
      success(`"${confirm.name}" and all versions were deleted.`, "Document deleted");
      setConfirm(null);
    } catch (e) {
      fail(e.message, "Delete failed");
    } finally {
      setDeletingId(null);
    }
  };

  const items = data?.content || [];

  return (
    <div className="ui-stack">
      <ToastContainer toasts={toasts} onDismiss={dismiss} />

      <header className="ui-page-head">
        <div>
          <h1 className="ui-page-head__title">Documents</h1>
          <p className="ui-page-head__sub">Authorized documents in your knowledge workspace.</p>
        </div>
        <div className="ui-row" style={{ gap: "var(--space-2)" }}>
          <Button variant="secondary" size="sm" onClick={() => load(page)} disabled={!data}>Refresh</Button>
          <Link className="ui-btn ui-btn--primary ui-btn--sm" to="/knowledge">Upload document</Link>
        </div>
      </header>

      <Card padding="none" elevated>
        {error && <div style={{ padding: "var(--space-5)" }}><ErrorState message={error} onRetry={() => load(page)} /></div>}

        {!data && !error && (
          <div style={{ padding: "var(--space-5)" }} className="ui-stack ui-stack--sm">
            {[0, 1, 2, 3, 4].map((i) => <Skeleton key={i} variant="row" />)}
          </div>
        )}

        {data && items.length === 0 && (
          <div style={{ padding: "var(--space-5)" }}>
            <EmptyState
              title="No authorized documents"
              action={<Link className="ui-btn ui-btn--primary ui-btn--sm" to="/knowledge">Upload a document</Link>}
            >
              Nothing is available at your current clearance, or the workspace is empty.
            </EmptyState>
          </div>
        )}

        {data && items.length > 0 && (
          <>
            <div className="ui-table-wrap">
              <table className="ui-table">
                <thead>
                  <tr>
                    <th>Title</th>
                    <th>Classification</th>
                    <th>Owner</th>
                    <th>Ver.</th>
                    <th>Status</th>
                    <th>Language</th>
                    <th>Updated</th>
                    <th style={{ textAlign: "right" }}>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {items.map((d) => (
                    <tr key={d.id}>
                      <td>
                        <Link className="ui-table__title" to={`/documents/${d.id}`}>{d.title || d.sourceFilename}</Link>
                        <div className="ui-table__meta ui-truncate" style={{ maxWidth: 280 }}>{d.sourceFilename}</div>
                      </td>
                      <td><ClassificationBadge value={d.classification} size="sm" /></td>
                      <td className="ui-small ui-muted">{d.ownerId || "—"}</td>
                      <td className="ui-mono">{d.version ?? "—"}</td>
                      <td><StatusBadge status={d.status} size="sm" /></td>
                      <td className="ui-small ui-muted">{d.language || "—"}</td>
                      <td className="ui-small ui-muted">{d.createdAt ? new Date(d.createdAt).toLocaleDateString() : "—"}</td>
                      <td>
                        <div className="ui-table__actions">
                          <Tooltip content="Open document">
                            <Link className="ui-btn ui-btn--secondary ui-btn--sm" to={`/documents/${d.id}`}>Open</Link>
                          </Tooltip>
                          <Tooltip content="Delete permanently">
                            <button
                              type="button"
                              className="ui-btn ui-btn--danger-outline ui-btn--sm"
                              onClick={() => setConfirm({ id: d.id, name: d.title || d.sourceFilename })}
                              disabled={deletingId === d.id}
                              aria-label={`Delete ${d.title || d.sourceFilename}`}
                            >
                              {TrashIcon}
                            </button>
                          </Tooltip>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div style={{ padding: "0 var(--space-5) var(--space-4)" }}>
              <Pagination page={page} totalPages={data.totalPages} onChange={setPage} />
            </div>
          </>
        )}
      </Card>

      <ConfirmDialog
        isOpen={!!confirm}
        title="Delete this document?"
        message={`"${confirm?.name}" and all of its versions will be permanently removed. This cannot be undone and the action is audited.`}
        confirmLabel="Delete permanently"
        loading={deletingId === confirm?.id}
        onConfirm={handleDelete}
        onCancel={() => setConfirm(null)}
      />
    </div>
  );
}