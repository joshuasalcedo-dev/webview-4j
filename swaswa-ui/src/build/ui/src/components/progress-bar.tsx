import * as React from "react"

import { cn } from "../lib/utils"

type ProgressBarProps = Omit<React.ComponentProps<"div">, "children"> & {
  /** 0 to 100; left out, it runs back and forth (it is not known how long it takes). */
  value?: number
  /** What is in progress, for screen readers (e.g. "Sending"). */
  "aria-label": string
}

/** How far something has got: a thin line on Windows, a rounded groove on macOS. */
function ProgressBar({ value, className, ...props }: ProgressBarProps) {
  const known = value != null
  const percent = known ? Math.min(100, Math.max(0, value)) : undefined
  return (
    <div
      role="progressbar"
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={percent}
      data-indeterminate={known ? undefined : ""}
      className={cn("nt-progress", className)}
      {...props}
    >
      <span
        className="nt-progress-bar"
        style={known ? { width: `${percent}%` } : undefined}
      />
    </div>
  )
}

export { ProgressBar }
export type { ProgressBarProps }
