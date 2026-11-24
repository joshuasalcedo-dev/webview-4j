import * as React from "react"

import { Magnifier } from "../lib/icons"
import { cn } from "../lib/utils"

type SearchFieldProps = Omit<React.ComponentProps<"input">, "type"> & {
  /** What is searched, for screen readers when there is no visible label (e.g. "Search chats"). */
  "aria-label": string
}

/**
 * A search box: Windows' AutoSuggestBox (the magnifier at the end), macOS' rounded toolbar search field
 * (the magnifier at the start). Escape clears it, as on both systems. className / style size the whole box.
 */
function SearchField({
  placeholder = "Search",
  className,
  style,
  onKeyDown,
  ...props
}: SearchFieldProps) {
  return (
    <span className={cn("nt-search", className)} style={style}>
      <Magnifier />
      <input
        type="search"
        placeholder={placeholder}
        onKeyDown={(event) => {
          if (event.key === "Escape" && event.currentTarget.value) {
            event.preventDefault()
            // Clearing through the input's own value setter keeps a controlled React input in step.
            const setter = Object.getOwnPropertyDescriptor(
              HTMLInputElement.prototype,
              "value"
            )?.set
            setter?.call(event.currentTarget, "")
            event.currentTarget.dispatchEvent(
              new Event("input", { bubbles: true })
            )
          }
          onKeyDown?.(event)
        }}
        {...props}
      />
    </span>
  )
}

export { SearchField }
export type { SearchFieldProps }
