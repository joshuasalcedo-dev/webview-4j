import * as React from "react"

import { Close } from "../lib/icons"
import { cn } from "../lib/utils"
import { useNative } from "./provider"

// --- the parts every field has: label, the box, a hint or an error ---------------------------------

type FieldParts = {
  /** Shown above the field, and what it is called for screen readers. */
  label?: React.ReactNode
  /** One line under it about what to type. */
  hint?: React.ReactNode
  /** What is wrong with what was typed; shown instead of the hint, and the field is marked. */
  error?: React.ReactNode
}

/** Wraps a field's box with its label and its hint or error, and gives them ids that point at each other. */
function useField(id: string | undefined, { label, hint, error }: FieldParts) {
  const generated = React.useId()
  const inputId = id ?? generated
  const noteId = `${inputId}-note`
  const note = error ?? hint
  const wrap = (
    box: React.ReactNode,
    className?: string,
    style?: React.CSSProperties,
    extra?: React.ReactNode
  ) => (
    <div className={cn("nt-field", className)} style={style}>
      {label != null && (
        <label className="nt-field-label" htmlFor={inputId}>
          {label}
        </label>
      )}
      {box}
      {(note != null || extra != null) && (
        <span className="nt-field-foot">
          {note != null && (
            <span
              id={noteId}
              className="nt-field-hint"
              data-error={error != null ? "" : undefined}
            >
              {note}
            </span>
          )}
          {extra}
        </span>
      )}
    </div>
  )
  return {
    inputId,
    describedBy: note != null ? noteId : undefined,
    invalid: error != null ? true : undefined,
    wrap,
  }
}

// --- TextArea -----------------------------------------------------------------------------------------

type TextAreaProps = React.ComponentProps<"textarea"> &
  FieldParts & {
    /** Grows with what is typed, from minRows up to maxRows, then scrolls. */
    autoGrow?: boolean
    minRows?: number
    maxRows?: number
    /** With maxLength: "120/500" under the box. True by default when there is a maxLength. */
    showCount?: boolean
  }

/** Fits a text box's height to its text, between two numbers of lines. */
function fitHeight(box: HTMLTextAreaElement, minRows: number, maxRows: number) {
  const style = getComputedStyle(box)
  const line = parseFloat(style.lineHeight) || 20
  const extra =
    parseFloat(style.paddingTop) +
    parseFloat(style.paddingBottom) +
    parseFloat(style.borderTopWidth) +
    parseFloat(style.borderBottomWidth)
  box.style.height = "auto"
  const wanted = Math.min(
    Math.max(box.scrollHeight, line * minRows + extra),
    line * maxRows + extra
  )
  box.style.height = `${wanted}px`
  box.style.overflowY = box.scrollHeight > wanted ? "auto" : "hidden"
}

/**
 * A text box for several lines: Windows' multi-line TextBox, a Mac's text view in a field. It can grow with
 * what is typed, and count the characters against a limit. Right-click gives the edit menu; clicks grow the
 * selection (word, sentence, paragraph, all).
 */
function TextArea({
  label,
  hint,
  error,
  id,
  className,
  style,
  autoGrow = false,
  minRows = 3,
  maxRows = 10,
  showCount,
  maxLength,
  value,
  defaultValue,
  onChange,
  ref,
  ...props
}: TextAreaProps) {
  const field = useField(id, { label, hint, error })
  const own = React.useRef<HTMLTextAreaElement>(null)
  const [length, setLength] = React.useState(
    () => String(value ?? defaultValue ?? "").length
  )

  React.useLayoutEffect(() => {
    if (autoGrow && own.current) fitHeight(own.current, minRows, maxRows)
  }, [autoGrow, minRows, maxRows, value])

  const counting = maxLength != null && showCount !== false
  return field.wrap(
    <textarea
      ref={(element) => {
        own.current = element
        if (typeof ref === "function") ref(element)
        else if (ref) ref.current = element
      }}
      id={field.inputId}
      className="nt-input nt-textarea"
      rows={autoGrow ? minRows : (props.rows ?? minRows)}
      aria-invalid={field.invalid}
      aria-describedby={field.describedBy}
      maxLength={maxLength}
      value={value}
      defaultValue={defaultValue}
      onChange={(event) => {
        setLength(event.target.value.length)
        if (autoGrow) fitHeight(event.target, minRows, maxRows)
        onChange?.(event)
      }}
      {...props}
    />,
    className,
    style,
    counting ? (
      <span
        className="nt-field-count"
        data-full={length >= maxLength || undefined}
        aria-live="polite"
      >
        {length}/{maxLength}
      </span>
    ) : undefined
  )
}

// --- PasswordField ---------------------------------------------------------------------------------------

type PasswordFieldProps = Omit<React.ComponentProps<"input">, "type"> &
  FieldParts

const EyeIcon = ({ crossed }: { crossed: boolean }) => (
  <svg
    viewBox="0 0 16 16"
    fill="none"
    stroke="currentColor"
    strokeWidth="1.2"
    strokeLinecap="round"
    aria-hidden="true"
  >
    <path d="M1.5 8s2.4-4.5 6.5-4.5S14.5 8 14.5 8 12.1 12.5 8 12.5 1.5 8 1.5 8z" />
    <circle cx="8" cy="8" r="2" />
    {crossed && <path d="m2.5 2.5 11 11" />}
  </svg>
)

/**
 * A password box: the characters hidden, an eye that shows them (Windows: while it is held down, as its
 * PasswordBox; a Mac: a toggle), and a warning when Caps Lock is on, as both systems' sign-in boxes give.
 */
function PasswordField({
  label,
  hint,
  error,
  id,
  className,
  style,
  onKeyDown,
  onKeyUp,
  onBlur,
  ...props
}: PasswordFieldProps) {
  const { platform } = useNative()
  const field = useField(id, { label, hint, error })
  const [shown, setShown] = React.useState(false)
  const [capsLock, setCapsLock] = React.useState(false)
  const checkCaps = (event: React.KeyboardEvent) =>
    setCapsLock(event.getModifierState?.("CapsLock") ?? false)
  const hold = platform === "windows"

  return field.wrap(
    <span className="nt-input nt-input-box">
      <input
        id={field.inputId}
        type={shown ? "text" : "password"}
        autoComplete="current-password"
        spellCheck={false}
        aria-invalid={field.invalid}
        aria-describedby={field.describedBy}
        onKeyDown={(event) => {
          checkCaps(event)
          onKeyDown?.(event)
        }}
        onKeyUp={(event) => {
          checkCaps(event)
          onKeyUp?.(event)
        }}
        onBlur={(event) => {
          setCapsLock(false)
          onBlur?.(event)
        }}
        {...props}
      />
      {capsLock && (
        <span className="nt-capslock" role="status" title="Caps Lock is on">
          ⇪
        </span>
      )}
      <button
        type="button"
        className="nt-input-button"
        tabIndex={-1}
        aria-label={shown ? "Hide password" : "Show password"}
        aria-pressed={shown}
        title={
          hold
            ? "Hold to show the password"
            : shown
              ? "Hide password"
              : "Show password"
        }
        onMouseDown={(event) => {
          // Keep the focus (and the caret) in the box.
          event.preventDefault()
          if (hold) setShown(true)
        }}
        onMouseUp={() => hold && setShown(false)}
        onMouseLeave={() => hold && setShown(false)}
        onClick={() => !hold && setShown(!shown)}
      >
        <EyeIcon crossed={shown} />
      </button>
    </span>,
    className,
    style,
    capsLock ? (
      <span className="nt-field-hint">Caps Lock is on.</span>
    ) : undefined
  )
}

// --- NumberField -------------------------------------------------------------------------------------------

type NumberFieldProps = Omit<
  React.ComponentProps<"input">,
  "type" | "value" | "defaultValue" | "onChange" | "min" | "max" | "step"
> &
  FieldParts & {
    value?: number | null
    defaultValue?: number | null
    /** The new number, or null when the box was emptied. */
    onValueChange?: (value: number | null) => void
    min?: number
    max?: number
    /** How much the arrows, ↑ ↓ and the mouse wheel change it (Page Up/Down: ten times that). */
    step?: number
    /** How it is written when not being typed in (e.g. { style: "currency", currency: "PHP" }). */
    format?: Intl.NumberFormatOptions
    /** Kept to this many decimal places (e.g. 2 for centavos); typed extra places are rounded. */
    fractionDigits?: number
    /** Inside the box, before the number (a currency symbol) and after it (a unit, a unit picker). */
    before?: React.ReactNode
    after?: React.ReactNode
    /** The up and down arrows (Windows) or the stepper (Mac). True by default. */
    stepper?: boolean
  }

const clamp = (n: number, min?: number, max?: number) =>
  Math.min(max ?? Infinity, Math.max(min ?? -Infinity, n))

/**
 * A box for a number: Windows' NumberBox (the up and down arrows inside, on the right), a Mac's text field
 * with a stepper beside it. ↑ ↓ and Page Up/Down change it; it stays between min and max; what is typed is
 * read on leaving the box (in the person's own way of writing numbers), and something that is not a number
 * goes back to the last good one.
 */
function NumberField({
  label,
  hint,
  error,
  id,
  className,
  style,
  value,
  defaultValue = null,
  onValueChange,
  min,
  max,
  step = 1,
  format,
  fractionDigits,
  before,
  after,
  stepper = true,
  disabled,
  onKeyDown,
  onBlur,
  onFocus,
  ...props
}: NumberFieldProps) {
  const field = useField(id, { label, hint, error })
  const controlled = value !== undefined
  const [own, setOwn] = React.useState<number | null>(defaultValue)
  const current = controlled ? value : own
  const [typing, setTyping] = React.useState<string | null>(null)
  const formatter = React.useMemo(
    () =>
      new Intl.NumberFormat(undefined, { maximumFractionDigits: 6, ...format }),
    [format]
  )
  const plain = React.useMemo(
    () =>
      new Intl.NumberFormat(undefined, {
        maximumFractionDigits: fractionDigits ?? 6,
        useGrouping: false,
      }),
    [fractionDigits]
  )
  // The person's decimal mark ("." or ","), to read what they typed.
  const decimal = React.useMemo(
    () =>
      plain.formatToParts(1.5).find((p) => p.type === "decimal")?.value ?? ".",
    [plain]
  )

  const set = (next: number | null) => {
    const kept =
      next === null
        ? null
        : clamp(Number(next.toFixed(fractionDigits ?? 10)), min, max)
    if (!controlled) setOwn(kept)
    onValueChange?.(kept)
  }
  const nudge = (by: number) => set(clamp((current ?? min ?? 0) + by, min, max))
  const read = (text: string): number | null | undefined => {
    const cleaned = text
      .trim()
      .replace(/\s/g, "")
      .split(decimal)
      .map((part) => part.replace(/[^\d-]/g, ""))
      .join(".")
    if (cleaned === "") return null
    const n = Number(cleaned)
    return Number.isFinite(n) ? n : undefined
  }

  const shownText =
    typing ?? (current === null ? "" : formatter.format(current))
  const atMin = current !== null && min !== undefined && current <= min
  const atMax = current !== null && max !== undefined && current >= max

  return field.wrap(
    <span
      className="nt-input nt-input-box nt-number"
      data-disabled={disabled || undefined}
      data-stepper={stepper || undefined}
    >
      {before != null && <span className="nt-input-affix">{before}</span>}
      <input
        id={field.inputId}
        inputMode="decimal"
        role="spinbutton"
        aria-valuenow={current ?? undefined}
        aria-valuemin={min}
        aria-valuemax={max}
        aria-invalid={field.invalid}
        aria-describedby={field.describedBy}
        disabled={disabled}
        value={shownText}
        onFocus={(event) => {
          // While typing, the plain number (no thousands marks, no currency).
          setTyping(current === null ? "" : plain.format(current))
          onFocus?.(event)
        }}
        onChange={(event) => setTyping(event.target.value)}
        onBlur={(event) => {
          if (typing !== null) {
            const n = read(typing)
            if (n !== undefined) set(n)
          }
          setTyping(null)
          onBlur?.(event)
        }}
        onKeyDown={(event) => {
          const by =
            event.key === "ArrowUp"
              ? step
              : event.key === "ArrowDown"
                ? -step
                : event.key === "PageUp"
                  ? step * 10
                  : event.key === "PageDown"
                    ? -step * 10
                    : 0
          if (by) {
            event.preventDefault()
            const base = typing !== null ? (read(typing) ?? current) : current
            const next = clamp((base ?? min ?? 0) + by, min, max)
            set(next)
            setTyping(plain.format(next))
          } else if (event.key === "Enter" && typing !== null) {
            const n = read(typing)
            if (n !== undefined) {
              set(n)
              setTyping(n === null ? "" : plain.format(clamp(n, min, max)))
            }
          } else if (event.key === "Escape" && typing !== null) {
            setTyping(current === null ? "" : plain.format(current))
          }
          onKeyDown?.(event)
        }}
        {...props}
      />
      {after != null && <span className="nt-input-affix">{after}</span>}
      {stepper && (
        <span className="nt-stepper" aria-hidden="true">
          <button
            type="button"
            tabIndex={-1}
            disabled={disabled || atMax}
            onMouseDown={(e) => e.preventDefault()}
            onClick={() => nudge(step)}
          >
            <svg viewBox="0 0 10 10">
              <path
                d="m2.5 6.5 2.5-2.5 2.5 2.5"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.2"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </svg>
          </button>
          <button
            type="button"
            tabIndex={-1}
            disabled={disabled || atMin}
            onMouseDown={(e) => e.preventDefault()}
            onClick={() => nudge(-step)}
          >
            <svg viewBox="0 0 10 10">
              <path
                d="m2.5 3.5 2.5 2.5 2.5-2.5"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.2"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </svg>
          </button>
        </span>
      )}
    </span>,
    className,
    style
  )
}

// --- TokenField ---------------------------------------------------------------------------------------------

type TokenFieldProps = Omit<
  React.ComponentProps<"input">,
  "value" | "defaultValue" | "onChange"
> &
  FieldParts & {
    /** The entries as chips (e.g. recipients, tags). */
    value?: string[]
    defaultValue?: string[]
    onValueChange?: (tokens: string[]) => void
    /** Offered while typing; ↑ ↓ to choose, Enter or Tab to take one. */
    suggestions?: string[]
    /** Whether a typed entry may become a chip; a message under the box when not (e.g. "Not an email address"). */
    validate?: (token: string) => string | undefined
    /** Keys that end an entry besides Enter: "," by default (and ";" for addresses). */
    separators?: string[]
  }

/**
 * Entries that become chips as they are typed: a Mac's token field (the To: line in Mail), the people
 * picker in Windows' Mail. Enter, Tab or a comma ends one; Backspace in the empty box takes the last one
 * back for editing; each chip has an ×; pasted lists split into chips; suggestions show while typing.
 */
function TokenField({
  label,
  hint,
  error,
  id,
  className,
  style,
  value,
  defaultValue = [],
  onValueChange,
  suggestions = [],
  validate,
  separators = [","],
  placeholder,
  disabled,
  ...props
}: TokenFieldProps) {
  const [problem, setProblem] = React.useState<string>()
  const field = useField(id, { label, hint, error: error ?? problem })
  const controlled = value !== undefined
  const [own, setOwn] = React.useState(defaultValue)
  const tokens = controlled ? value : own
  const [text, setText] = React.useState("")
  const [active, setActive] = React.useState(0)
  const input = React.useRef<HTMLInputElement>(null)
  const listId = `${field.inputId}-suggestions`

  const set = (next: string[]) => {
    if (!controlled) setOwn(next)
    onValueChange?.(next)
  }
  const offered = text.trim()
    ? suggestions
        .filter(
          (s) =>
            s.toLowerCase().includes(text.trim().toLowerCase()) &&
            !tokens.includes(s)
        )
        .slice(0, 6)
    : []

  /** Turns typed text into chips; false when something was not allowed (it stays in the box). */
  const commit = (raw: string) => {
    const parts = raw
      .split(new RegExp(`[${separators.map((s) => `\\${s}`).join("")}\\n]`))
      .map((p) => p.trim())
      .filter(Boolean)
    const added: string[] = []
    for (const part of parts) {
      const wrong = validate?.(part)
      if (wrong) {
        setProblem(wrong)
        setText(part)
        set([...tokens, ...added])
        return false
      }
      if (!tokens.includes(part) && !added.includes(part)) added.push(part)
    }
    setProblem(undefined)
    set([...tokens, ...added])
    setText("")
    setActive(0)
    return true
  }

  return field.wrap(
    <span
      className="nt-input nt-input-box nt-tokens"
      data-disabled={disabled || undefined}
      onClick={() => input.current?.focus()}
    >
      {tokens.map((token) => (
        <span key={token} className="nt-token">
          <span className="nt-token-text">{token}</span>
          {!disabled && (
            <button
              type="button"
              tabIndex={-1}
              aria-label={`Remove ${token}`}
              onClick={(event) => {
                event.stopPropagation()
                set(tokens.filter((t) => t !== token))
                input.current?.focus()
              }}
            >
              <Close />
            </button>
          )}
        </span>
      ))}
      <input
        ref={input}
        id={field.inputId}
        role="combobox"
        aria-expanded={offered.length > 0}
        aria-controls={listId}
        aria-autocomplete="list"
        aria-invalid={field.invalid}
        aria-describedby={field.describedBy}
        placeholder={tokens.length === 0 ? placeholder : undefined}
        disabled={disabled}
        value={text}
        onChange={(event) => {
          const typed = event.target.value
          if (separators.some((s) => typed.endsWith(s))) commit(typed)
          else {
            setText(typed)
            setProblem(undefined)
            setActive(0)
          }
        }}
        onPaste={(event) => {
          const pasted = event.clipboardData.getData("text")
          if (/[\n,;]/.test(pasted)) {
            event.preventDefault()
            commit(text + pasted)
          }
        }}
        onKeyDown={(event) => {
          if (
            offered.length > 0 &&
            (event.key === "ArrowDown" || event.key === "ArrowUp")
          ) {
            event.preventDefault()
            setActive(
              (a) =>
                (a + (event.key === "ArrowDown" ? 1 : -1) + offered.length) %
                offered.length
            )
          } else if (
            (event.key === "Enter" || event.key === "Tab") &&
            (offered.length > 0 || text.trim())
          ) {
            event.preventDefault()
            commit(offered.length > 0 ? offered[active] : text)
          } else if (
            event.key === "Backspace" &&
            text === "" &&
            tokens.length > 0
          ) {
            // The last chip comes back into the box, to be edited.
            event.preventDefault()
            setText(tokens[tokens.length - 1])
            set(tokens.slice(0, -1))
          } else if (event.key === "Escape") {
            setText("")
          }
        }}
        onBlur={() => text.trim() && commit(text)}
        {...props}
      />
      {offered.length > 0 && (
        <ul id={listId} role="listbox" className="nt-token-suggestions">
          {offered.map((s, i) => (
            <li
              key={s}
              role="option"
              aria-selected={i === active}
              onMouseDown={(event) => {
                event.preventDefault()
                commit(s)
              }}
              onMouseEnter={() => setActive(i)}
            >
              {s}
            </li>
          ))}
        </ul>
      )}
    </span>,
    className,
    style
  )
}

export { fitHeight, NumberField, PasswordField, TextArea, TokenField }
export type {
  NumberFieldProps,
  PasswordFieldProps,
  TextAreaProps,
  TokenFieldProps,
}
