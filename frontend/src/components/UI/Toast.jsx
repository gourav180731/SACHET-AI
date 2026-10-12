import { useEffect, useState, useCallback, useRef } from "react";
import { createPortal } from "react-dom";

const ICONS = {
  success: <svg width="18" height="18" fill="currentColor" viewBox="0 0 20 20" aria-hidden="true"><path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" /></svg>,
  error: <svg width="18" height="18" fill="currentColor" viewBox="0 0 20 20" aria-hidden="true"><path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm1-7a1 1 0 11-2 0v4a1 1 0 102 0V11zm-1-9a1 1 0 10-2 0v4a1 1 0 102 0V2z" clipRule="evenodd" /></svg>,
  warning: <svg width="18" height="18" fill="currentColor" viewBox="0 0 20 20" aria-hidden="true"><path fillRule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clipRule="evenodd" /></svg>,
  info: <svg width="18" height="18" fill="currentColor" viewBox="0 0 20 20" aria-hidden="true"><path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clipRule="evenodd" /></svg>,
};

function Toast({ toast, onDismiss }) {
  const [leaving, setLeaving] = useState(false);

  const close = () => {
    setLeaving(true);
    window.setTimeout(() => onDismiss(toast.id), 200);
  };

  useEffect(() => {
    if (toast.duration === 0) return undefined;
    const t = window.setTimeout(close, toast.duration);
    return () => window.clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [toast.id]);

  return (
    <div className={`ui-toast ui-toast--${toast.kind}${leaving ? " ui-toast--leaving" : ""}`} role="status">
      <span style={{ flexShrink: 0, display: "flex", marginTop: 1 }}>{ICONS[toast.kind] || ICONS.info}</span>
      <div className="ui-toast__body">
        {toast.title && <p className="ui-toast__title">{toast.title}</p>}
        <p className="ui-toast__msg">{toast.message}</p>
      </div>
      <button className="ui-toast__close" onClick={close} aria-label="Dismiss notification">
        <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
        </svg>
      </button>
    </div>
  );
}

export const ToastContainer = ({ toasts, onDismiss }) => {
  if (!toasts?.length) return null;
  return createPortal(
    <div className="ui-toast-host" aria-live="polite" aria-relevant="additions">
      {toasts.map((t) => <Toast key={t.id} toast={t} onDismiss={onDismiss} />)}
    </div>,
    document.body
  );
};

const DEFAULT_DURATION = 5000;

export function useToasts() {
  const [toasts, setToasts] = useState([]);
  const counter = useRef(0);

  const dismiss = useCallback((id) => {
    setToasts((list) => list.filter((t) => t.id !== id));
  }, []);

  const push = useCallback((message, kind = "info", title = null, duration = DEFAULT_DURATION) => {
    counter.current += 1;
    const id = `${Date.now()}-${counter.current}`;
    setToasts((list) => [...list.slice(-3), { id, message, kind, title, duration }]);
    return id;
  }, []);

  return {
    toasts,
    dismiss,
    push,
    success: (m, t) => push(m, "success", t),
    error: (m, t) => push(m, "error", t),
    warning: (m, t) => push(m, "warning", t),
    info: (m, t) => push(m, "info", t),
  };
}

export default useToasts;