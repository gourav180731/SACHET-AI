import { useEffect, useRef, useCallback } from "react";
import { createPortal } from "react-dom";
import Button from "./Button";

const FOCUSABLE = 'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

const Modal = ({ isOpen, title, children, footer, onClose, size = "md", closeOnOverlayClick = true }) => {
  const dialogRef = useRef(null);
  const previouslyFocused = useRef(null);

  const handleKey = useCallback((e) => {
    if (e.key === "Escape") { onClose?.(); return; }
    if (e.key !== "Tab" || !dialogRef.current) return;
    const items = Array.from(dialogRef.current.querySelectorAll(FOCUSABLE));
    if (!items.length) return;
    const first = items[0];
    const last = items[items.length - 1];
    if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
    else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
  }, [onClose]);

  useEffect(() => {
    if (!isOpen) return undefined;
    previouslyFocused.current = document.activeElement;
    const prevOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    document.addEventListener("keydown", handleKey);
    const id = requestAnimationFrame(() => {
      const target = dialogRef.current?.querySelector("[data-autofocus]") || dialogRef.current;
      target?.focus();
    });
    return () => {
      document.body.style.overflow = prevOverflow;
      document.removeEventListener("keydown", handleKey);
      cancelAnimationFrame(id);
      previouslyFocused.current?.focus?.();
    };
  }, [isOpen, handleKey]);

  if (!isOpen) return null;

  return createPortal(
    <div className="ui-modal-backdrop" onMouseDown={(e) => { if (closeOnOverlayClick && e.target === e.currentTarget) onClose?.(); }}>
      <div
        ref={dialogRef}
        className={`ui-modal ui-modal--${size}`}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        tabIndex={-1}
      >
        <div className="ui-modal__head">
          <h2 className="ui-modal__title">{title}</h2>
          <button className="ui-modal__close" onClick={onClose} aria-label="Close dialog">
            <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
        <div className="ui-modal__body">{children}</div>
        {footer && <div className="ui-modal__foot">{footer}</div>}
      </div>
    </div>,
    document.body
  );
};

export const ConfirmDialog = ({
  isOpen, title, message, confirmLabel = "Confirm", cancelLabel = "Cancel",
  variant = "danger", loading = false, onConfirm, onCancel,
}) => (
  <Modal
    isOpen={isOpen}
    title={title}
    onClose={onCancel}
    size="sm"
    footer={
      <>
        <Button variant="secondary" onClick={onCancel} disabled={loading}>{cancelLabel}</Button>
        <Button variant={variant} onClick={onConfirm} loading={loading} data-autofocus>{confirmLabel}</Button>
      </>
    }
  >
    <p className="ui-muted" style={{ margin: 0, fontSize: "var(--font-size-sm)" }}>{message}</p>
  </Modal>
);

export default Modal;