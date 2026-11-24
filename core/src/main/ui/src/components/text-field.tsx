import * as React from "react"

import { cn } from "../lib/utils"

type TextFieldProps = React.ComponentProps<"input"> & {
  /** Shown above the field, and what it is called for screen readers. */
  label?: React.ReactNode
  /** One line under it about what to type. */
  hint?: React.ReactNode
  /** What is wrong with what was typed; shown instead of the hint, and the field is marked. */
  error?: React.ReactNode
}

/**
 * A one-line text box, with its label and a hint or an error. className and style size the whole field
 * (e.g. style={{ flex: 1 }} in a row); the other props go to the <input>.
 */
function TextField({
  label,
  hint,
  error,
  id,
  className,
  style,
  ...props
}: TextFieldProps) {
  const generated = React.useId()
  const inputId = id ?? generated
  const noteId = `${inputId}-note`
  const note = error ?? hint
  return (
    <div className={cn("nt-field", className)} style={style}>
      {label != null && (
        <label className="nt-field-label" htmlFor={inputId}>
          {label}
        </label>
      )}
      <input
        id={inputId}
        className="nt-input"
        aria-invalid={error != null ? true : undefined}
        aria-describedby={note != null ? noteId : undefined}
        {...props}
      />
      {note != null && (
        <span
          id={noteId}
          className="nt-field-hint"
          data-error={error != null ? "" : undefined}
        >
          {note}
        </span>
      )}
    </div>
  )
}

export { TextField }
export type { TextFieldProps }
