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
  /** How long typing must pause before the text is checked, in ms (500 unless set): no check on every key. */
  checkDelay?: number
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
  checkDelay,
  rows = 4,
  onMistakes,
  className,
  style,
  onSelect,
  onKeyUp,
  onClick,
  onScroll,
  onKeyDown,
  ...props
}: GrammarTextAreaProps) {
  const [own, setOwn] = React.useState(defaultValue)
  const text = value ?? own
  const [ignored, setIgnored] = React.useState<string[]>([])
  const { mistakes, ready } = useGrammar(text, { dialect, enabled: check, ignored, delay: checkDelay })
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

  // The card for the mistake at the caret, as Grammarly's: under the marked word, inside the field's width. Esc
  // closes it until the caret goes to another mistake.
  const root = React.useRef<HTMLDivElement>(null)
  const [scrolled, setScrolled] = React.useState(0)
  const [dismissed, setDismissed] = React.useState<number | null>(null)
  const [place, setPlace] = React.useState<{ top: number; left: number } | null>(null)
  const open = here && dismissed !== here.from ? here : undefined
  React.useEffect(() => {
    if (here?.from !== dismissed) setDismissed(null)
  }, [here?.from]) // eslint-disable-line react-hooks/exhaustive-deps
  React.useLayoutEffect(() => {
    const mark = open ? backdrop.current?.querySelector<HTMLElement>("mark[data-here]") : null
    const box = root.current?.getBoundingClientRect()
    if (!mark || !box) {
      setPlace(null)
      return
    }
    const word = mark.getBoundingClientRect()
    const width = Math.min(320, box.width)
    setPlace({ top: word.bottom - box.top + 6, left: Math.max(0, Math.min(word.left - box.left, box.width - width)) })
  }, [open, text, scrolled])

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
    <div ref={root} className={cn("nt-grammar", className)} style={style}>
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
          aria-describedby={open ? id : undefined}
          onKeyDown={(e) => {
            if (e.key === "Escape" && open) {
              e.preventDefault()
              setDismissed(open.from)
            }
            onKeyDown?.(e)
          }}
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
            setScrolled(e.currentTarget.scrollTop)
            onScroll?.(e)
          }}
          {...props}
        />
      </div>
      {open && (
        <div
          className="nt-grammar-card"
          id={id}
          role="status"
          data-kind={open.kind === "Spelling" ? "spelling" : "grammar"}
          style={place ? { top: place.top, left: place.left } : { visibility: "hidden" }}
        >
          <div className="nt-grammar-card-head">
            <span className="nt-grammar-kind" data-kind={open.kind === "Spelling" ? "spelling" : "grammar"}>
              {open.kind === "Miscellaneous" ? "Grammar" : open.kind.replace(/([a-z])([A-Z])/g, "$1 $2")}
            </span>
            <button
              type="button"
              className="nt-grammar-close"
              aria-label="Close"
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => setDismissed(open.from)}
            >
              ×
            </button>
          </div>
          <div className="nt-grammar-message">{open.message.replace(/`/g, "")}</div>
          <div className="nt-grammar-fixes">
            {open.suggestions.slice(0, 4).map((s) => (
              <button
                key={s}
                type="button"
                className="nt-grammar-fix"
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => set(applySuggestion(text, open, s), open.from + s.length)}
              >
                {s === "" ? "Remove it" : s}
              </button>
            ))}
            <button
              type="button"
              className="nt-grammar-ignore"
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => setIgnored([...ignored, text.slice(open.from, open.to)])}
            >
              Ignore
            </button>
          </div>
        </div>
      )}
      {check && (
        <div className="nt-grammar-status" aria-live="polite">
          {!ready ? "Checking…" : current.length === 0 ? "No mistakes" : `${current.length} to look at`}
        </div>
      )}
    </div>
  )
}

export { GrammarTextArea }
export type { GrammarTextAreaProps }
