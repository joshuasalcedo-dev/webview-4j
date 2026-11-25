import * as React from "react"

import { applySuggestion, type Dialect, type Mistake, useGrammar } from "../lib/grammar"
import { cn } from "../lib/utils"
import "./grammar.css"

type GrammarTextAreaProps = Omit<React.ComponentProps<"textarea">, "value" | "onChange" | "defaultValue"> & {
  value?: string
  defaultValue?: string
  onChange?: (value: string) => void
  /** Which English (en-US unless set). */
  dialect?: Dialect
  /** Checks as it is typed (on unless false). */
  check?: boolean
  /** How many lines tall (4 unless set). */
  rows?: number
  /** The mistakes found, each time they are checked (e.g. to count them, or to stop a send). */
  onMistakes?: (mistakes: Mistake[]) => void
}

/**
 * A text area that checks spelling and grammar as it is typed (Harper, in the page as WebAssembly): mistakes are
 * underlined (spelling red, the rest blue), and the one at the caret shows what it is and its fixes; a click on a
 * fix puts it in, "Ignore" leaves that word alone.
 */
function GrammarTextArea({
  value,
  defaultValue = "",
  onChange,
  dialect = "en-US",
  check = true,
  rows = 4,
  onMistakes,
  className,
  style,
  onSelect,
  onKeyUp,
  onClick,
  onScroll,
  ...props
}: GrammarTextAreaProps) {
  const [own, setOwn] = React.useState(defaultValue)
  const text = value ?? own
  const [ignored, setIgnored] = React.useState<string[]>([])
  const { mistakes, ready } = useGrammar(text, { dialect, enabled: check, ignored })
  const [caret, setCaret] = React.useState<number | null>(null)
  const area = React.useRef<HTMLTextAreaElement>(null)
  const backdrop = React.useRef<HTMLDivElement>(null)
  const id = React.useId()

  React.useEffect(() => onMistakes?.(mistakes), [mistakes]) // eslint-disable-line react-hooks/exhaustive-deps

  const set = (next: string, caretAt?: number) => {
    if (value === undefined) setOwn(next)
    onChange?.(next)
    if (caretAt !== undefined) {
      requestAnimationFrame(() => {
        area.current?.focus()
        area.current?.setSelectionRange(caretAt, caretAt)
        setCaret(caretAt)
      })
    }
  }
  const track = () => {
    const el = area.current
    if (el) setCaret(el.selectionStart === el.selectionEnd ? el.selectionStart : null)
  }
  // the caret, wherever it moves (keys, the mouse, a script): the hint follows it
  React.useEffect(() => {
    const onSelection = () => {
      if (document.activeElement === area.current) track()
    }
    document.addEventListener("selectionchange", onSelection)
    return () => document.removeEventListener("selectionchange", onSelection)
  }, [])
  // the mistakes stay valid only for the text they were found in
  const current = mistakes.filter((m) => m.to <= text.length)
  const here = caret == null ? undefined : current.find((m) => caret >= m.from && caret <= m.to)

  // the backdrop: the same text, laid out as the text area lays it out, the mistakes marked
  const pieces: React.ReactNode[] = []
  let at = 0
  for (const [i, m] of current.entries()) {
    if (m.from < at) continue
    pieces.push(text.slice(at, m.from))
    pieces.push(
      <mark key={i} data-kind={m.kind === "Spelling" ? "spelling" : "grammar"} data-here={m === here || undefined}>
        {text.slice(m.from, m.to)}
      </mark>
    )
    at = m.to
  }
  pieces.push(text.slice(at) + "\n")

  return (
    <div className={cn("nt-grammar", className)} style={style}>
      <div className="nt-grammar-field">
        <div ref={backdrop} className="nt-grammar-backdrop" aria-hidden="true">
          {pieces}
        </div>
        <textarea
          ref={area}
          className="nt-grammar-input"
          rows={rows}
          spellCheck={false}
          value={text}
          aria-describedby={here ? id : undefined}
          onChange={(e) => {
            set(e.target.value)
            setCaret(e.target.selectionStart)
          }}
          onSelect={(e) => {
            track()
            onSelect?.(e)
          }}
          onKeyUp={(e) => {
            track()
            onKeyUp?.(e)
          }}
          onClick={(e) => {
            track()
            onClick?.(e)
          }}
          onScroll={(e) => {
            if (backdrop.current) backdrop.current.scrollTop = e.currentTarget.scrollTop
            onScroll?.(e)
          }}
          {...props}
        />
      </div>
      {here ? (
        <div className="nt-grammar-hint" id={id} role="status">
          <span className="nt-grammar-kind" data-kind={here.kind === "Spelling" ? "spelling" : "grammar"}>
            {here.kind === "Miscellaneous" ? "Grammar" : here.kind.replace(/([a-z])([A-Z])/g, "$1 $2")}
          </span>
          <span className="nt-grammar-message">{here.message.replace(/`/g, "")}</span>
          <span className="nt-grammar-fixes">
            {here.suggestions.slice(0, 4).map((s) => (
              <button
                key={s}
                type="button"
                className="nt-grammar-fix"
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => set(applySuggestion(text, here, s), here.from + s.length)}
              >
                {s === "" ? "Remove it" : s}
              </button>
            ))}
            <button
              type="button"
              className="nt-grammar-ignore"
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => setIgnored([...ignored, text.slice(here.from, here.to)])}
            >
              Ignore
            </button>
          </span>
        </div>
      ) : (
        check && (
          <div className="nt-grammar-status" aria-live="polite">
            {!ready ? "Checking…" : current.length === 0 ? "No mistakes" : `${current.length} to look at`}
          </div>
        )
      )}
    </div>
  )
}

export { GrammarTextArea }
export type { GrammarTextAreaProps }
