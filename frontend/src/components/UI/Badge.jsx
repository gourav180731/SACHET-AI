const CLASSIFICATIONS = {
  PUBLIC: "ui-badge--public",
  INTERNAL: "ui-badge--internal",
  RESTRICTED: "ui-badge--restricted",
  CONFIDENTIAL: "ui-badge--confidential",
  SECRET: "ui-badge--secret",
};

const STATUSES = {
  READY: "ui-badge--ready",
  PROCESSING: "ui-badge--processing",
  FAILED: "ui-badge--failed",
  QUEUED: "ui-badge--queued",
  VALIDATING: "ui-badge--info",
  PARSING: "ui-badge--info",
  OCR: "ui-badge--info",
  CHUNKING: "ui-badge--info",
  EMBEDDING: "ui-badge--info",
  INDEXING: "ui-badge--info",
};

const VARIANTS = {
  default: "ui-badge--default",
  primary: "ui-badge--primary",
  success: "ui-badge--success",
  warning: "ui-badge--warning",
  danger: "ui-badge--danger",
  info: "ui-badge--info",
  queued: "ui-badge--queued",
  ready: "ui-badge--ready",
  processing: "ui-badge--processing",
  failed: "ui-badge--failed",
};

const Badge = ({ children, variant = "default", size = "md", dot = false, className = "", ...props }) => {
  const classes = ["ui-badge", `ui-badge--${size}`, VARIANTS[variant] || VARIANTS.default, className]
    .filter(Boolean).join(" ");
  return (
    <span className={classes} {...props}>
      {dot && <span className="ui-badge__dot" aria-hidden="true" />}
      {children}
    </span>
  );
};

export const ClassificationBadge = ({ value, size = "md", dot = true, ...props }) => {
  const val = (value || "INTERNAL").toUpperCase();
  return (
    <span className={`ui-badge ui-badge--${size} ${CLASSIFICATIONS[val] || CLASSIFICATIONS.INTERNAL}`} {...props}>
      {dot && <span className="ui-badge__dot" aria-hidden="true" />}
      {val}
    </span>
  );
};

export const StatusBadge = ({ status, size = "md", dot = true, ...props }) => {
  const val = (status || "QUEUED").toUpperCase();
  return (
    <span className={`ui-badge ui-badge--${size} ${STATUSES[val] || STATUSES.QUEUED}`} {...props}>
      {dot && <span className="ui-badge__dot" aria-hidden="true" />}
      {val}
    </span>
  );
};

export default Badge;