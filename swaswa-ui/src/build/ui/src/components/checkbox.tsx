import * as React from "react"

import { cn } from "../lib/utils"

type CheckboxProps = Omit<React.ComponentProps<"input">, "type"> & {
  /** Next to the box; clicking it ticks the box too. */
  label?: React.ReactNode
}

/** A check box with its label. Controlled (checked + onChange) or not (defaultChecked). */
function Checkbox({ label, className, ...props }: CheckboxProps) {
  return (
    <label className={cn("nt-check", className)}>
      <span className="nt-check-box">
        <input type="checkbox" {...props} />
        <svg viewBox="0 0 12 12" fill="none" aria-hidden="true">
          <path
            d="M2.5 6.2 5 8.6l4.5-5.2"
            stroke="currentColor"
            strokeWidth="1.6"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      </span>
      {label}
    </label>
  )
}

export { Checkbox }
export type { CheckboxProps }
