import * as React from "react"

import "./authentication.css"

/**
 * A one-time code (two-step sign-in, an email's code): a box per digit, the focus moving on as each is typed, a whole
 * code pasted at once, Backspace going back. `onComplete` gets the code when every box has a digit.
 */
type CodeFieldProps = {
  "aria-label"?: string
  /** How many digits (6 unless set). */
  length?: number
  value?: string
  onChange?: (code: string) => void
  onComplete?: (code: string) => void
  disabled?: boolean
  error?: React.ReactNode
  className?: string
}

function CodeField({ length = 6, value, onChange, onComplete, disabled, error, className, ...props }: CodeFieldProps) {
  const [own, setOwn] = React.useState("")
  const code = (value ?? own).replace(/\D/g, "").slice(0, length)
  const boxes = React.useRef<(HTMLInputElement | null)[]>([])
  const set = (next: string) => {
    const digits = next.replace(/\D/g, "").slice(0, length)
    if (value === undefined) setOwn(digits)
    onChange?.(digits)
    if (digits.length === length) onComplete?.(digits)
    boxes.current[Math.min(digits.length, length - 1)]?.focus()
  }
  return (
    <div className={["nt-codefield", className].filter(Boolean).join(" ")}>
      <div className="nt-code-boxes" role="group" aria-label={props["aria-label"] ?? "Code"}>
        {Array.from({ length }, (_, i) => (
          <input
            key={i}
            ref={(el) => {
              boxes.current[i] = el
            }}
            className="nt-input nt-code-box"
            inputMode="numeric"
            autoComplete={i === 0 ? "one-time-code" : "off"}
            aria-label={`Digit ${i + 1}`}
            maxLength={length}
            value={code[i] ?? ""}
            disabled={disabled}
            aria-invalid={error ? true : undefined}
            onChange={(e) => {
              const typed = e.target.value.replace(/\D/g, "")
              if (typed.length > 1) set(code.slice(0, i) + typed)   // pasted, or typed fast
              else set(code.slice(0, i) + typed + code.slice(i + 1))
            }}
            onKeyDown={(e) => {
              if (e.key === "Backspace" && !code[i] && i > 0) {
                e.preventDefault()
                set(code.slice(0, i - 1) + code.slice(i))
                boxes.current[i - 1]?.focus()
              }
              if (e.key === "ArrowLeft" && i > 0) boxes.current[i - 1]?.focus()
              if (e.key === "ArrowRight" && i < length - 1) boxes.current[i + 1]?.focus()
            }}
            onPaste={(e) => {
              e.preventDefault()
              set(e.clipboardData.getData("text"))
            }}
          />
        ))}
      </div>
      {error && (
        <div className="nt-signin-error" role="alert">
          {error}
        </div>
      )}
    </div>
  )
}

export { CodeField }
export type { CodeFieldProps }
