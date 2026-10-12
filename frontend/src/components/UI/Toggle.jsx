import { forwardRef, useId } from "react";

const SIZES = {
  sm: { w: 36, h: 20, thumb: 16, shift: 16 },
  md: { w: 44, h: 24, thumb: 18, shift: 20 },
  lg: { w: 52, h: 28, thumb: 22, shift: 24 },
};

const Toggle = forwardRef(({
  checked, onChange, disabled = false, label, description, size = "md", className = "", ...props
}, ref) => {
  const generatedId = useId();
  const id = props.id || generatedId;
  const dim = SIZES[size] || SIZES.md;

  return (
    <div className={`ui-toggle ${className}`}>
      <div className="ui-input-wrap" style={{ width: dim.w, flexShrink: 0 }}>
        <input
          ref={ref}
          type="checkbox"
          id={id}
          role="switch"
          checked={checked}
          disabled={disabled}
          onChange={(e) => onChange?.(e.target.checked)}
          aria-checked={checked}
          aria-labelledby={label ? `${id}-label` : undefined}
          aria-describedby={description ? `${id}-desc` : undefined}
          style={{ position: "absolute", opacity: 0, width: dim.w, height: dim.h, margin: 0, cursor: disabled ? "not-allowed" : "pointer", zIndex: 1 }}
          {...props}
        />
        <label
          htmlFor={id}
          className={`ui-toggle__track ${checked ? "ui-toggle__track--on" : ""}`}
          aria-disabled={disabled || undefined}
          style={{ width: dim.w, height: dim.h, display: "block", "--toggle-shift": `${dim.shift}px` }}
        >
          <span className="ui-toggle__thumb" style={{ width: dim.thumb, height: dim.thumb }} />
        </label>
      </div>
      {(label || description) && (
        <div className="ui-toggle__text">
          {label && <label className="ui-toggle__label" id={`${id}-label`} htmlFor={id}>{label}</label>}
          {description && <p className="ui-toggle__desc" id={`${id}-desc`}>{description}</p>}
        </div>
      )}
    </div>
  );
});

Toggle.displayName = "Toggle";

export const ToggleGroup = ({ children, className = "" }) => (
  <div className={`ui-stack ui-stack--md ${className}`}>{children}</div>
);

export default Toggle;