import { forwardRef } from "react";

const VARIANT_MAP = {
  primary: "ui-btn--primary",
  secondary: "ui-btn--secondary",
  outline: "ui-btn--outline",
  ghost: "ui-btn--ghost",
  danger: "ui-btn--danger",
  dangerOutline: "ui-btn--danger-outline",
  success: "ui-btn--success",
};

const SIZE_MAP = { sm: "ui-btn--sm", md: "", lg: "ui-btn--lg", icon: "ui-btn--icon" };

const Button = forwardRef(({
  variant = "primary",
  size = "md",
  loading = false,
  fullWidth = false,
  leftIcon,
  rightIcon,
  className = "",
  children,
  disabled,
  ...props
}, ref) => {
  const classes = [
    "ui-btn",
    VARIANT_MAP[variant] || VARIANT_MAP.secondary,
    SIZE_MAP[size],
    fullWidth ? "ui-btn--full" : "",
    loading ? "btn--loading" : "",
    className,
  ].filter(Boolean).join(" ");

  return (
    <button ref={ref} className={classes} disabled={disabled || loading} aria-busy={loading || undefined} {...props}>
      {loading && <span className="btn__spinner" aria-hidden="true" />}
      {!loading && leftIcon && <span aria-hidden="true" style={{ display: "flex" }}>{leftIcon}</span>}
      <span>{children}</span>
      {!loading && rightIcon && <span aria-hidden="true" style={{ display: "flex" }}>{rightIcon}</span>}
    </button>
  );
});

Button.displayName = "Button";

export default Button;