import * as React from "react"

import { cn } from "../lib/utils"

/**
 * The strip along a window's bottom edge with what is going on: Windows' status bar (segments split by thin
 * lines, as in Notepad), macOS' bottom bar (small grey text, centred unless a StatusBarSpacer spreads it,
 * as in Finder). Children: StatusItems, a StatusBarSpacer.
 */
function StatusBar({ className, ...props }: React.ComponentProps<"footer">) {
  return (
    <footer
      role="status"
      aria-live="polite"
      className={cn("nt-statusbar", className)}
      {...props}
    />
  )
}

type StatusItemProps = React.ComponentProps<"span"> & {
  /** Before the text: a StatusDot, a 12px icon. */
  icon?: React.ReactNode
}

/** One piece of the status: text, with an icon or a small ProgressBar beside it. */
function StatusItem({ icon, className, children, ...props }: StatusItemProps) {
  return (
    <span className={cn("nt-status-item", className)} {...props}>
      {icon}
      {children}
    </span>
  )
}

/** Pushes the items after it to the end. */
function StatusBarSpacer() {
  return <span className="nt-statusbar-spacer" aria-hidden="true" />
}

type StatusDotProps = Omit<React.ComponentProps<"span">, "children"> & {
  /** online: green; connecting: yellow; offline: grey; error: red. In the system's own shades. */
  state: "online" | "connecting" | "offline" | "error"
  /** What it means, for screen readers (e.g. "Connected"). */
  "aria-label": string
}

/** A small round light for a connection's state. */
function StatusDot({ state, className, ...props }: StatusDotProps) {
  return (
    <span
      role="img"
      data-state={state}
      className={cn("nt-status-dot", className)}
      {...props}
    />
  )
}

export { StatusBar, StatusBarSpacer, StatusDot, StatusItem }
export type { StatusDotProps, StatusItemProps }
