import { useEffect, useState, useCallback } from "react";
import { Link, useNavigate } from "react-router-dom";
import { documentsApi } from "../api";
import { useAuth } from "../context/AuthContext";
import { Card, Button, StatusBadge, ClassificationBadge, ErrorState, EmptyState, Skeleton, StatCard } from "../components/UI";

const Icon = {
  chat: <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M8.625 12a.375.375 0 11-.75 0 .375.375 0 01.75 0zm3.75 0a.375.375 0 11-.75 0 .375.375 0 01.75 0zm3.75 0a.375.375 0 11-.75 0 .375.375 0 01.75 0zM21 12c0 4.556-4.03 8.25-9 8.25a9.764 9.764 0 01-2.555-.337A5.972 5.972 0 015.41 20.97a5.969 5.969 0 01-.474-.065 4.48 4.48 0 00.978-2.025c.09-.457-.133-.901-.467-1.226C3.93 16.178 3 14.189 3 12c0-4.556 4.03-8.25 9-8.25s9 3.694 9 8.25z" /></svg>,
  upload: <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M3 16.5v2.25A2.25 2.25 0 005.25 21h13.5A2.25 2.25 0 0021 18.75V16.5m-13.5-9L12 3m0 0l4.5 4.5M12 3v13.5" /></svg>,
  search: <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M21 21l-5.197-5.197m0 0A7.5 7.5 0 105.196 5.196a7.5 7.5 0 0010.607 10.607z" /></svg>,
  spark: <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09z" /></svg>,
  docs: <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M19.5 14.25v-2.625a3.375 3.375 0 00-3.375-3.375h-1.5A1.125 1.125 0 0113.5 7.125v-1.5a3.375 3.375 0 00-3.375-3.375H8.25m0 12.75h7.5m-7.5 3H12M10.5 2.25H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 00-9-9z" /></svg>,
};

const QUICK = [
  { to: "/assistant", label: "Ask SACHET AI", desc: "Grounded Q&A over your documents", icon: Icon.chat, tone: "accent" },
  { to: "/knowledge", label: "Upload Document", desc: "Ingest PDF, DOCX, PPTX, TXT or MD", icon: Icon.upload, tone: "success" },
  { to: "/search", label: "Search Knowledge", desc: "Retrieve authorized passages", icon: Icon.search, tone: "warning" },
  { to: "/studio", label: "Document Studio", desc: "Summarize, translate, generate", icon: Icon.spark, tone: "primary" },
];

const CONTROLS = [
  { label: "Authentication", value: "JWT Bearer tokens enforced", state: "Enforced" },
  { label: "Authorization", value: "Server-side RBAC + document permissions", state: "Enforced" },
  { label: "Document isolation", value: "Permission-filtered retrieval", state: "Enforced" },
  { label: "External model transfer", value: "Policy-controlled, see Security & Privacy", state: "Controlled" },
  { label: "Audit logging", value: "Server-side events, see Audit & Activity", state: "Active" },
];

const STATE_TONE = { Enforced: "success", Controlled: "warning", Active: "info" };

export default function Dashboard() {
  const { auth } = useAuth();
  const navigate = useNavigate();
  const [docs, setDocs] = useState(null);
  const [error, setError] = useState(null);
  const [retrying, setRetrying] = useState(false);

  const load = useCallback(() => {
    setRetrying(true);
    setError(null);
    setDocs(null);
    documentsApi.list(0, 5)
      .then(setDocs)
      .catch((e) => setError(e.message))
      .finally(() => setRetrying(false));
  }, []);

  useEffect(() => { load(); }, [load]);

  const items = docs?.content || [];
  const stats = docs
    ? {
        total: docs.totalElements ?? items.length,
        ready: items.filter((d) => d.status === "READY").length,
        processing: items.filter((d) => !["READY", "FAILED"].includes(d.status)).length,
        failed: items.filter((d) => d.status === "FAILED").length,
      }
    : null;

  return (
    <div className="ui-stack">
      <header className="ui-page-head">
        <div>
          <h1 className="ui-page-head__title">Welcome back, {auth?.userId}</h1>
          <p className="ui-page-head__sub">Work securely with your authorized knowledge.</p>
        </div>
        <Button variant="primary" leftIcon={Icon.chat} onClick={() => navigate("/assistant")}>
          Ask SACHET AI
        </Button>
      </header>

      {/* Primary actions */}
      <div className="ui-grid ui-grid--4">
        {QUICK.map((q) => (
          <Link key={q.to} to={q.to} className="ui-tile">
            <span className={`ui-tile__icon ui-stat__icon--${q.tone}`} aria-hidden="true">{q.icon}</span>
            <span className="ui-tile__body">
              <span className="ui-tile__title">{q.label}</span>
              <span className="ui-tile__desc">{q.desc}</span>
            </span>
            <span className="ui-tile__arrow" aria-hidden="true">
              <svg fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8.25 4.5l7.5 7.5-7.5 7.5" /></svg>
            </span>
          </Link>
        ))}
      </div>

      {/* Metrics */}
      <div className="ui-grid ui-grid--4">
        <StatCard label="Documents" value={stats ? stats.total : "—"} icon={Icon.docs} tone="primary" />
        <StatCard label="Ready" value={stats ? stats.ready : "—"} icon={Icon.docs} tone="success" hint="On this page" />
        <StatCard label="Processing" value={stats ? stats.processing : "—"} icon={Icon.docs} tone="warning" hint="On this page" />
        <StatCard label="Failed" value={stats ? stats.failed : "—"} icon={Icon.docs} tone="danger" hint="On this page" />
      </div>

      <div className="ui-grid ui-grid--2">
        <Card title="Security controls" subtitle="Configured backend behaviour">
          <ul className="ui-stack ui-stack--sm" style={{ listStyle: "none", margin: 0, padding: 0 }}>
            {CONTROLS.map((c) => (
              <li key={c.label} className="ui-row ui-row--between" style={{ padding: "10px 12px", background: "var(--color-bg)", borderRadius: "var(--radius-md)", gap: "var(--space-3)" }}>
                <span style={{ minWidth: 0 }}>
                  <span style={{ display: "block", fontSize: "var(--font-size-xs)", fontWeight: 600 }}>{c.label}</span>
                  <span className="ui-small ui-muted">{c.value}</span>
                </span>
                <span className={`ui-badge ui-badge--xs ui-badge--${STATE_TONE[c.state]}`}>{c.state}</span>
              </li>
            ))}
          </ul>
          <div style={{ marginTop: "var(--space-4)" }}>
            <Button variant="secondary" size="sm" onClick={() => navigate("/security")}>
              View security details
            </Button>
          </div>
        </Card>

        <Card
          title="Recent documents"
          subtitle={stats ? `${stats.total} authorized in your workspace` : "Loading"}
          actions={<Link to="/documents" className="ui-small">View all</Link>}
        >
          {error && <ErrorState message={error} onRetry={load} />}
          {!docs && !error && (
            <div className="ui-stack ui-stack--sm">
              {[0, 1, 2].map((i) => <Skeleton key={i} variant="row" />)}
            </div>
          )}
          {docs && items.length === 0 && (
            <EmptyState title="No documents yet" action={<Button variant="primary" size="sm" onClick={() => navigate("/knowledge")}>Upload your first document</Button>}>
              Upload an authorized document to start building your knowledge workspace.
            </EmptyState>
          )}
          {docs && items.length > 0 && (
            <div className="ui-table-wrap">
              <table className="ui-table ui-table--compact">
                <thead>
                  <tr><th>Title</th><th>Class.</th><th>Ver.</th><th>Status</th><th>Created</th></tr>
                </thead>
                <tbody>
                  {items.map((d) => (
                    <tr key={d.id}>
                      <td><Link className="ui-table__title" to={`/documents/${d.id}`}>{d.title || d.sourceFilename}</Link></td>
                      <td><ClassificationBadge value={d.classification} size="sm" /></td>
                      <td className="ui-mono">{d.version ?? "—"}</td>
                      <td><StatusBadge status={d.status} size="sm" /></td>
                      <td className="ui-small ui-muted">{d.createdAt ? new Date(d.createdAt).toLocaleDateString() : "—"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      </div>

      <Card title="Recent activity" subtitle="Backend audit trail">
        <EmptyState icon={false} title="No activity available on this endpoint" action={<Link to="/audit" className="ui-small">Open Audit &amp; Activity</Link>}>
          Audit events are recorded server-side and require an administrator role to read.
        </EmptyState>
      </Card>
    </div>
  );
}