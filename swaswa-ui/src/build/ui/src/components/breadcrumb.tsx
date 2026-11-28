import * as React from "react"

import { ChevronRight } from "../lib/icons"
import { cn } from "../lib/utils"

type Crumb = {
  label: string
  /** Before the label (macOS' path bar shows them, in the accent colour). */
  icon?: React.ReactNode
  /** Going there; the last crumb is where this is, and is not a button. */
  onSelect?: () => void
}

type BreadcrumbProps = Omit<React.ComponentProps<"nav">, "children"> & {
  /** From the top of the hierarchy to here. */
  items: Crumb[]
  "aria-label"?: string
}

/**
 * Where in a hierarchy this page is, each level a way back up: Windows' BreadcrumbBar (a page title's
 * size, chevrons between), macOS' path bar (small, with icons, as at the bottom of a Finder window).
 */
function Breadcrumb({
  items,
  className,
  "aria-label": label = "Breadcrumb",
  ...props
}: BreadcrumbProps) {
  return (
    <nav
      aria-label={label}
      className={cn("nt-breadcrumb", className)}
      {...props}
    >
      <ol>
        {items.map((item, index) => {
          const last = index === items.length - 1
          const inner = (
            <>
              {item.icon}
              <span>{item.label}</span>
            </>
          )
          return (
            <li key={index}>
              {last || !item.onSelect ? (
                <span
                  className="nt-crumb"
                  aria-current={last ? "page" : undefined}
                >
                  {inner}
                </span>
              ) : (
                <button
                  type="button"
                  className="nt-crumb"
                  onClick={item.onSelect}
                >
                  {inner}
                </button>
              )}
              {!last && (
                <span className="nt-crumb-separator" aria-hidden="true">
                  <ChevronRight />
                </span>
              )}
            </li>
          )
        })}
      </ol>
    </nav>
  )
}

export { Breadcrumb }
export type { BreadcrumbProps, Crumb }
