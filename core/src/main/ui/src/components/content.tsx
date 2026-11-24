import * as React from "react"

import { cn } from "../lib/utils"

/**
 * The window's main area, beside a Sidebar: on Windows 11 a lighter layer over the window's Mica with its
 * top-left corner rounded against the pane; on a Mac the plain content background. It scrolls.
 */
function Content({ className, ...props }: React.ComponentProps<"main">) {
  return <main className={cn("nt-content", className)} {...props} />
}

export { Content }
