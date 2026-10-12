import { useRef, useState } from "react";
import Button from "./Button";

/* ---------------- Tabs ---------------- */
export const Tabs = ({ tabs, active, onChange, ariaLabel = "Sections" }) => {
  const refs = useRef([]);
  const onKeyDown = (e) => {
    const idx = tabs.indexOf(active);
    if (e.key === "ArrowRight") { e.preventDefault(); const n = (idx + 1) % tabs.length; onChange(tabs[n]); refs.current[n]?.focus(); }
    if (e.key === "ArrowLeft") { e.preventDefault(); const n = (idx - 1 + tabs.length) % tabs.length; onChange(tabs[n]); refs.current[n]?.focus(); }
    if (e.key === "Home") { e.preventDefault(); onChange(tabs[0]); refs.current[0]?.focus(); }
    if (e.key === "End") { e.preventDefault(); const l = tabs.length - 1; onChange(tabs[l]); refs.current[l]?.focus(); }
  };
  return (
    <div className="ui-tabs" role="tablist" aria-label={ariaLabel} onKeyDown={onKeyDown}>
      {tabs.map((tab, i) => {
        const key = typeof tab === "string" ? tab : tab.key;
        const label = typeof tab === "string" ? tab : tab.label;
        const isActive = key === active;
        return (
          <button
            key={key}
            ref={(el) => { refs.current[i] = el; }}
            role="tab"
            type="button"
            id={`tab-${key}`}
            aria-selected={isActive}
            aria-controls={`panel-${key}`}
            tabIndex={isActive ? 0 : -1}
            className={`ui-tabs__tab ${isActive ? "ui-tabs__tab--active" : ""}`}
            onClick={() => onChange(key)}
          >
            {label}
          </button>
        );
      })}
    </div>
  );
};

export const TabPanel = ({ tabKey, active, children }) => (
  <div role="tabpanel" id={`panel-${tabKey}`} aria-labelledby={`tab-${tabKey}`} hidden={tabKey !== active}>
    {tabKey === active && children}
  </div>
);

/* ---------------- Ingestion stepper ---------------- */
const INGESTION_STEPS = ["QUEUED", "VALIDATING", "PARSING", "OCR", "CHUNKING", "EMBEDDING", "INDEXING", "READY"];

export const IngestionStepper = ({ status, errorMessage }) => {
  const state = (status || "QUEUED").toUpperCase();
  if (state === "FAILED") {
    return (
      <div className="ui-stepper" aria-label="Ingestion status">
        <span className="ui-step ui-step--failed"><span className="ui-step__dot" />FAILED</span>
        {errorMessage && <span style={{ color: "var(--color-danger-700)", fontSize: "var(--font-size-xs)" }}>{errorMessage}</span>}
      </div>
    );
  }
  const idx = INGESTION_STEPS.indexOf(state);
  return (
    <div className="ui-stepper" aria-label={`Ingestion status: ${state}`} role="status">
      {INGESTION_STEPS.map((s, i) => {
        const done = i < idx;
        const current = i === idx;
        return (
          <span key={s} className={`ui-step ${done ? "ui-step--done" : ""} ${current ? "ui-step--active" : ""}`}>
            <span className="ui-step__dot" aria-hidden="true" />
            {s}
            {current && <span className="ui-sr-only"> (current step)</span>}
          </span>
        );
      })}
    </div>
  );
};

/* ---------------- Upload zone ---------------- */
export const UploadZone = ({ onFiles, accept }) => {
  const [drag, setDrag] = useState(false);
  const inputRef = useRef(null);

  return (
    <div
      className={`ui-upload ${drag ? "ui-upload--drag" : ""}`}
      onDragOver={(e) => { e.preventDefault(); setDrag(true); }}
      onDragLeave={(e) => { e.preventDefault(); setDrag(false); }}
      onDrop={(e) => { e.preventDefault(); setDrag(false); const f = e.dataTransfer.files; if (f?.length) onFiles(Array.from(f)); }}
      onClick={() => inputRef.current?.click()}
      onKeyDown={(e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); inputRef.current?.click(); } }}
      role="button"
      tabIndex={0}
      aria-label="Upload files. Drag and drop or press Enter to browse."
    >
      <svg className="ui-upload__icon" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.6} d="M7 16a4 4 0 01-.88-7.9A5 5 0 1115.9 6H16a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12" />
      </svg>
      <p className="ui-upload__title">Drop files here or click to browse</p>
      <p className="ui-upload__hint">Supported formats: PDF, DOCX, PPTX, TXT, MD</p>
      <input
        ref={inputRef}
        type="file"
        multiple
        accept={accept}
        className="ui-sr-only"
        onChange={(e) => { if (e.target.files?.length) onFiles(Array.from(e.target.files)); e.target.value = ""; }}
      />
    </div>
  );
};

/* ---------------- Pagination ---------------- */
export const Pagination = ({ page, totalPages, onChange, itemLabel = "items" }) => {
  if (!totalPages || totalPages <= 1) return null;
  const window = 2;
  const nums = [];
  for (let i = 0; i < totalPages; i += 1) {
    if (i === 0 || i === totalPages - 1 || Math.abs(i - page) <= window) nums.push(i);
    else if (nums[nums.length - 1] !== "…") nums.push("…");
  }
  return (
    <div className="ui-pager">
      <span className="ui-pager__info">
        Page {page + 1} of {totalPages}
      </span>
      <div className="ui-page-dots">
        <button type="button" onClick={() => onChange(page - 1)} disabled={page === 0} aria-label="Previous page">‹</button>
        {nums.map((n, i) => n === "…" ? (
          <span key={`gap-${i}`} className="ui-pager__info" aria-hidden="true">…</span>
        ) : (
          <button key={n} type="button" onClick={() => onChange(n)} aria-current={n === page ? "page" : undefined} aria-label={`Page ${n + 1}`}>
            {n + 1}
          </button>
        ))}
        <button type="button" onClick={() => onChange(page + 1)} disabled={page >= totalPages - 1} aria-label="Next page">›</button>
      </div>
    </div>
  );
};

/* ---------------- Stat card ---------------- */
export const StatCard = ({ label, value, hint, icon, tone = "primary", to, onClick }) => {
  const inner = (
    <>
      <div className="ui-stat__top">
        <div style={{ minWidth: 0 }}>
          <p className="ui-stat__label">{label}</p>
          <p className="ui-stat__value">{value}</p>
        </div>
        {icon && <span className={`ui-stat__icon ui-stat__icon--${tone}`} aria-hidden="true">{icon}</span>}
      </div>
      {hint && <p className="ui-stat__hint">{hint}</p>}
    </>
  );
  if (to) return <a className="ui-stat ui-stat--link" href={to}>{inner}</a>;
  if (onClick) return <button type="button" className="ui-stat ui-stat--link" onClick={onClick}>{inner}</button>;
  return <div className="ui-stat">{inner}</div>;
};

/* ---------------- Definition list ---------------- */
export const DefList = ({ items }) => (
  <dl className="kv">
    {items.filter(Boolean).map(([k, v]) => (
      <div key={k} style={{ display: "contents" }}>
        <dt>{k}</dt>
        <dd>{v ?? "—"}</dd>
      </div>
    ))}
  </dl>
);

/* ---------------- Copy button ---------------- */
export const CopyButton = ({ text, label = "Copy", size = "sm", onCopied }) => {
  const [done, setDone] = useState(false);
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(text);
      setDone(true);
      onCopied?.();
      window.setTimeout(() => setDone(false), 1800);
    } catch { /* clipboard unavailable */ }
  };
  return (
    <Button variant="ghost" size={size} onClick={copy} aria-label={label}>
      {done ? "Copied" : label}
    </Button>
  );
};