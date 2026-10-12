export const Spinner = ({ size = "md", className = "", label = "Loading" }) => (
  <svg className={`ui-spinner ui-spinner--${size} ${className}`} viewBox="0 0 24 24" fill="none" role="status" aria-label={label}>
    <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="3.5" opacity="0.2" />
    <path d="M22 12a10 10 0 00-10-10" stroke="currentColor" strokeWidth="3.5" strokeLinecap="round" />
  </svg>
);

export const DotsLoader = ({ label = "Loading" }) => (
  <span className="ui-dots" role="status" aria-label={label}>
    <span /><span /><span />
  </span>
);

export const Skeleton = ({ variant = "text", width, height, count = 1, style, className = "" }) => {
  const items = Array.from({ length: count });
  return (
    <>
      {items.map((_, i) => (
        <div
          key={i}
          className={`ui-skeleton ui-skeleton--${variant} ${className}`}
          style={{ width, height, ...style }}
          aria-hidden="true"
        />
      ))}
    </>
  );
};

export const ProgressBar = ({ value = 0, max = 100, color = "primary", showLabel = false, label }) => {
  const pct = Math.min(100, Math.max(0, (value / max) * 100));
  return (
    <div>
      <div
        className="ui-progress"
        role="progressbar"
        aria-valuenow={Math.round(pct)}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-label={label}
      >
        <div className={`ui-progress__bar ${color !== "primary" ? `ui-progress__bar--${color}` : ""}`} style={{ width: `${pct}%` }} />
      </div>
      {showLabel && <p className="ui-progress__label">{Math.round(pct)}%</p>}
    </div>
  );
};

export const LoadingState = ({ label = "Loading..." }) => (
  <div className="ui-loading" aria-live="polite">
    <Spinner size="md" />
    <p className="ui-loading__text">{label}</p>
  </div>
);

export const InlineLoading = ({ label = "Loading" }) => (
  <span className="ui-inline-loading"><Spinner size="xs" />{label}</span>
);

export const EmptyState = ({ title, children, action, icon }) => (
  <div className="ui-state">
    {icon !== false && (
      <svg className="ui-state__icon" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.4} d="M20 13V6a2 2 0 00-2-2H6a2 2 0 00-2 2v7m16 0v5a2 2 0 01-2 2H6a2 2 0 01-2-2v-5m16 0h-2.586a1 1 0 00-.707.293l-2.414 2.414a1 1 0 01-.707.293h-3.172a1 1 0 01-.707-.293l-2.414-2.414A1 1 0 006.586 13H4" />
      </svg>
    )}
    {title && <h3 className="ui-state__title">{title}</h3>}
    {children && <p className="ui-state__text">{children}</p>}
    {action}
  </div>
);

export const ErrorState = ({ message, onRetry }) => (
  <div className="ui-state" role="alert">
    <svg className="ui-state__icon ui-state__icon--error" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.6} d="M12 9v4m0 4h.01M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z" />
    </svg>
    <h3 className="ui-state__title">Something went wrong</h3>
    <p className="ui-state__text">{message}</p>
    {onRetry && (
      <button className="ui-btn ui-btn--secondary ui-btn--sm" onClick={onRetry}>Try again</button>
    )}
  </div>
);

export const Alert = ({ kind = "info", title, children, className = "" }) => (
  <div className={`ui-alert ui-alert--${kind} ${className}`} role={kind === "error" ? "alert" : "status"}>
    {ICONS_BY_KIND[kind]}
    <div style={{ minWidth: 0 }}>
      {title && <strong style={{ display: "block", marginBottom: 2 }}>{title}</strong>}
      <div>{children}</div>
    </div>
  </div>
);

const ICONS_BY_KIND = {
  error: <svg fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v4m0 4h.01M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z" /></svg>,
  warning: <svg fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v4m0 4h.01M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z" /></svg>,
  success: <svg fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>,
  info: <svg fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>,
};