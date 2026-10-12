import { forwardRef, useId } from "react";

const ErrorIcon = () => (
  <svg width="14" height="14" fill="currentColor" viewBox="0 0 20 20" aria-hidden="true">
    <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
  </svg>
);

function Field({ id, label, error, hint, required, children }) {
  return (
    <div className="ui-field">
      {label && (
        <label className="ui-label" htmlFor={id}>
          {label}
          {required && <span className="ui-label__req" aria-hidden="true">*</span>}
        </label>
      )}
      {children}
      {error ? (
        <p className="ui-error-text" id={`${id}-error`} role="alert"><ErrorIcon />{error}</p>
      ) : hint ? (
        <p className="ui-hint" id={`${id}-hint`}>{hint}</p>
      ) : null}
    </div>
  );
}

const Input = forwardRef(({
  label, error, hint, leftIcon, rightIcon, required,
  className = "", containerClassName = "", ...props
}, ref) => {
  const id = useId();
  const describedBy = error ? `${id}-error` : hint ? `${id}-hint` : undefined;

  const classes = [
    "ui-input",
    error ? "ui-input--invalid" : "",
    leftIcon ? "ui-input--icon-left" : "",
    rightIcon ? "ui-input--icon-right" : "",
    className,
  ].filter(Boolean).join(" ");

  const input = (
    <input
      ref={ref}
      id={id}
      className={classes}
      required={required}
      aria-invalid={error ? "true" : undefined}
      aria-describedby={describedBy}
      {...props}
    />
  );

  return (
    <Field id={id} label={label} error={error} hint={hint} required={required}>
      <div className={`ui-input-wrap ${containerClassName}`}>
        {leftIcon && <span className="ui-input-icon ui-input-icon--left">{leftIcon}</span>}
        {input}
        {rightIcon && <span className="ui-input-icon ui-input-icon--right">{rightIcon}</span>}
      </div>
    </Field>
  );
});
Input.displayName = "Input";

export const Textarea = forwardRef(({ label, error, hint, required, rows = 3, className = "", ...props }, ref) => {
  const id = useId();
  const classes = ["ui-textarea", error ? "ui-input--invalid" : "", className].filter(Boolean).join(" ");
  return (
    <Field id={id} label={label} error={error} hint={hint} required={required}>
      <textarea
        ref={ref} id={id} rows={rows} className={classes} required={required}
        aria-invalid={error ? "true" : undefined}
        aria-describedby={error ? `${id}-error` : hint ? `${id}-hint` : undefined}
        {...props}
      />
    </Field>
  );
});
Textarea.displayName = "Textarea";

export const Select = forwardRef(({ label, error, hint, required, options, placeholder, className = "", children, ...props }, ref) => {
  const id = useId();
  const classes = ["ui-select", error ? "ui-input--invalid" : "", className].filter(Boolean).join(" ");
  return (
    <Field id={id} label={label} error={error} hint={hint} required={required}>
      <select
        ref={ref} id={id} className={classes} required={required}
        aria-invalid={error ? "true" : undefined}
        aria-describedby={error ? `${id}-error` : hint ? `${id}-hint` : undefined}
        {...props}
      >
        {placeholder && <option value="">{placeholder}</option>}
        {options?.map((o) => (
          <option key={o.value} value={o.value}>{o.label}</option>
        ))}
        {children}
      </select>
    </Field>
  );
});
Select.displayName = "Select";

export const Checkbox = ({ label, description, className = "", id: providedId, ...props }) => {
  const generated = useId();
  const id = providedId || generated;
  return (
    <label htmlFor={id} className={`ui-checkbox-row ${className}`}>
      <input type="checkbox" id={id} className="ui-checkbox" {...props} />
      <span>
        <span style={{ display: "block", fontSize: "var(--font-size-sm)" }}>{label}</span>
        {description && <span className="ui-hint" style={{ display: "block" }}>{description}</span>}
      </span>
    </label>
  );
};

export default Input;