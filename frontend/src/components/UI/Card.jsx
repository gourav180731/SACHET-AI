import { forwardRef } from "react";

const Card = forwardRef(({
  title, subtitle, actions, footer, children,
  padding = "md", hoverable = false, elevated = false, bordered = true,
  className = "", as: Tag = "section", ...props
}, ref) => {
  const padClass =
    padding === "lg" ? "ui-card--lg" : padding === "sm" ? "ui-card--sm" : padding === "none" ? "ui-card--flush" : "";

  const classes = [
    "ui-card",
    padClass,
    hoverable ? "ui-card--hover" : "",
    elevated ? "ui-card--elevated" : "",
    bordered ? "" : "ui-card--noborder",
    className,
  ].filter(Boolean).join(" ");

  return (
    <Tag ref={ref} className={classes} {...props}>
      {(title || subtitle || actions) && (
        <div className="ui-card__head">
          <div style={{ minWidth: 0 }}>
            {title && <h2 className="ui-card__title">{title}</h2>}
            {subtitle && <p className="ui-card__subtitle">{subtitle}</p>}
          </div>
          {actions && <div className="ui-card__actions">{actions}</div>}
        </div>
      )}
      {children}
      {footer && <div className="ui-card__footer">{footer}</div>}
    </Tag>
  );
});

Card.displayName = "Card";

export default Card;