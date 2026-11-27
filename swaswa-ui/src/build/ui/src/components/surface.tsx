import * as React from "react"

import { cn } from "../lib/utils"

type SurfaceProps = Omit<React.ComponentProps<"section">, "title"> & {
  /** A heading for what is inside. */
  title?: React.ReactNode
}

/** A card of related controls: Windows' card, macOS' group box. */
function Surface({ title, className, children, ...props }: SurfaceProps) {
  return (
    <section className={cn("nt-surface", className)} {...props}>
      {title != null && <h3 className="nt-surface-title">{title}</h3>}
      {children}
    </section>
  )
}

export { Surface }
export type { SurfaceProps }
