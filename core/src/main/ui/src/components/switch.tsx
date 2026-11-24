import * as React from "react"

import { cn } from "../lib/utils"

type SwitchProps = Omit<React.ComponentProps<"input">, "type" | "role"> & {
  /** Next to the switch; clicking it flips the switch too. */
  label?: React.ReactNode
}

/** An on/off switch (Windows' ToggleSwitch, macOS' switch). Controlled (checked + onChange) or not. */
function Switch({ label, className, ...props }: SwitchProps) {
  return (
    <label className={cn("nt-switch", className)}>
      <span className="nt-switch-track">
        <input type="checkbox" role="switch" {...props} />
        <span className="nt-switch-thumb" aria-hidden="true" />
      </span>
      {label}
    </label>
  )
}

export { Switch }
export type { SwitchProps }
