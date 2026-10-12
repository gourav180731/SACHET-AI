import { cloneElement, isValidElement, useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";

const GAP = 8;

const Tooltip = ({ children, content, position = "top", delay = 200, disabled = false }) => {
  const [coords, setCoords] = useState(null);
  const anchorRef = useRef(null);
  const tipRef = useRef(null);
  const timerRef = useRef(null);

  useEffect(() => () => window.clearTimeout(timerRef.current), []);

  const open = () => {
    if (disabled || !content) return;
    window.clearTimeout(timerRef.current);
    timerRef.current = window.setTimeout(() => {
      const a = anchorRef.current;
      const t = tipRef.current;
      if (!a || !t) return;
      const r = a.getBoundingClientRect();
      const tw = t.offsetWidth;
      const th = t.offsetHeight;
      let top = 0;
      let left = 0;
      if (position === "bottom") { top = r.bottom + GAP; left = r.left + r.width / 2 - tw / 2; }
      else if (position === "left") { top = r.top + r.height / 2 - th / 2; left = r.left - tw - GAP; }
      else if (position === "right") { top = r.top + r.height / 2 - th / 2; left = r.right + GAP; }
      else { top = r.top - th - GAP; left = r.left + r.width / 2 - tw / 2; }
      left = Math.max(GAP, Math.min(left, window.innerWidth - tw - GAP));
      top = Math.max(GAP, Math.min(top, window.innerHeight - th - GAP));
      setCoords({ top, left });
    }, delay);
  };

  const close = () => {
    window.clearTimeout(timerRef.current);
    setCoords(null);
  };

  const anchorProps = {
    ref: anchorRef,
    onMouseEnter: open,
    onMouseLeave: close,
    onFocus: open,
    onBlur: close,
  };

  const child = isValidElement(children)
    ? cloneElement(children, anchorProps)
    : <span {...anchorProps} tabIndex={0}>{children}</span>;

  return (
    <>
      {child}
      {coords && createPortal(
        <div
          ref={tipRef}
          className="ui-tooltip"
          role="tooltip"
          style={{ top: coords.top, left: coords.left, position: "fixed" }}
        >
          {content}
        </div>,
        document.body
      )}
    </>
  );
};

export default Tooltip;