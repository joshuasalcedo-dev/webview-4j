import * as React from "react"

import { cn } from "../lib/utils"
import { ProgressBar } from "./progress-bar"
import { useNative } from "./provider"
import { type SoundName, useSound } from "./sound"
import { TextField } from "./text-field"

/** What the dialog is about; it picks the icon. */
type DialogKind = "info" | "question" | "warning" | "error"

/** One button at the bottom of a dialog. */
type DialogAction = {
  label: string
  onSelect: () => void
  /** The main action: the accent button, and what Enter does. One per dialog. */
  primary?: boolean
  /** What Escape does (Cancel, Close). One per dialog. */
  cancel?: boolean
  /** It deletes or loses something: a Mac shows it in red, and the focus starts on Cancel instead. */
  destructive?: boolean
  disabled?: boolean
}

type DialogProps = {
  open: boolean
  title: React.ReactNode
  /** What happened or what is asked, in plain words. */
  message?: React.ReactNode
  kind?: DialogKind
  /**
   * Most important first (the main action, then the others, Cancel last). Windows lays them out in that
   * order from the left; a Mac puts the main one on the right (two buttons) or on top (three or more).
   */
  actions: DialogAction[]
  /** Extra content under the message: a text field, a progress bar, details. */
  children?: React.ReactNode
  /** Where the focus starts (e.g. a text field); otherwise the main button, or Cancel when it is destructive. */
  initialFocus?: React.RefObject<HTMLElement | null>
  /** Drawn in place, not over the window (for a picture of the dialog, e.g. in a story or a help page). */
  inline?: boolean
  className?: string
}

const ICONS: Record<DialogKind, React.ReactNode> = {
  info: (
    <svg viewBox="0 0 32 32" aria-hidden="true">
      <circle cx="16" cy="16" r="15" fill="var(--nt-accent)" />
      <path
        d="M16 14v9M16 9.5v.5"
        stroke="var(--nt-on-accent)"
        strokeWidth="2.6"
        strokeLinecap="round"
      />
    </svg>
  ),
  question: (
    <svg viewBox="0 0 32 32" aria-hidden="true">
      <circle cx="16" cy="16" r="15" fill="var(--nt-accent)" />
      <path
        d="M12.2 12.4a3.9 3.9 0 0 1 7.6 1.2c0 2.6-3.8 3-3.8 5.6M16 23v.3"
        fill="none"
        stroke="var(--nt-on-accent)"
        strokeWidth="2.4"
        strokeLinecap="round"
      />
    </svg>
  ),
  warning: (
    <svg viewBox="0 0 32 32" aria-hidden="true">
      <path
        d="M16 2.5 30.5 28h-29z"
        fill="#f7c600"
        stroke="#c99a00"
        strokeWidth="1"
        strokeLinejoin="round"
      />
      <path
        d="M16 11v8.5M16 23.5v.3"
        stroke="#1a1a1a"
        strokeWidth="2.6"
        strokeLinecap="round"
      />
    </svg>
  ),
  error: (
    <svg viewBox="0 0 32 32" aria-hidden="true">
      <circle cx="16" cy="16" r="15" fill="#d13438" />
      <path
        d="m11 11 10 10M21 11 11 21"
        stroke="#ffffff"
        strokeWidth="2.6"
        strokeLinecap="round"
      />
    </svg>
  ),
}

const FOCUSABLE =
  'button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])'

/**
 * A modal dialog over the window, as the system draws one: Windows 11's ContentDialog (the window dimmed,
 * a card with the title, the message and a row of equal buttons on a grey footer), a Mac alert (a small
 * centred panel, a large icon on top, bold title, small message, wide buttons). Enter does the main action,
 * Escape the cancel one, and Tab stays inside. For the usual dialogs, useDialogs() is shorter.
 *
 * It is the browser's own modal <dialog>, shown in the top layer: it always covers the whole window, above
 * everything, and is never cut off or squeezed by what it is written inside (a panel, a scrolling list, a
 * short frame). With `inline` it is drawn in place instead, without covering anything (for pictures of it).
 */
function Dialog({
  open,
  inline = false,
  title,
  message,
  kind,
  actions,
  children,
  initialFocus,
  className,
}: DialogProps) {
  const { platform } = useNative()
  const sound = useSound()
  const panel = React.useRef<HTMLDialogElement & HTMLDivElement>(null)
  const titleId = React.useId()
  const messageId = React.useId()

  const primary = actions.find((a) => a.primary)
  const cancel = actions.find((a) => a.cancel)

  // Into the top layer when it opens, out when it closes (an unmounted dialog leaves it by itself).
  React.useLayoutEffect(() => {
    const dialog = panel.current
    if (inline || !dialog || typeof dialog.showModal !== "function") return
    if (open && !dialog.open) dialog.showModal()
    else if (!open && dialog.open) dialog.close()
  }, [open, inline])

  // The sound the system plays with this kind of dialog: error and warning on both, info on Windows only.
  React.useEffect(() => {
    if (!open || !kind || inline) return
    const name: SoundName | undefined =
      kind === "error"
        ? "error"
        : kind === "warning"
          ? "warning"
          : kind === "info" && platform === "windows"
            ? "info"
            : undefined
    if (name) sound.play(name)
    // Only on opening.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open])

  // The focus goes into the dialog when it opens, and back where it was when it closes.
  React.useEffect(() => {
    if (!open || inline) return
    const before = document.activeElement as HTMLElement | null
    const start =
      initialFocus?.current ??
      panel.current?.querySelector<HTMLElement>(
        primary?.destructive ? "[data-cancel]" : "[data-primary]"
      ) ??
      panel.current?.querySelector<HTMLElement>(FOCUSABLE)
    start?.focus()
    // A text box starts with its text selected, so typing replaces it (as in the systems' rename boxes).
    if (start instanceof HTMLInputElement) start.select()
    return () => before?.focus?.()
    // Only on opening: later changes to the buttons must not move the focus.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open])

  const doCancel = () => {
    if (cancel && !cancel.disabled) cancel.onSelect()
  }

  function onKeyDown(event: React.KeyboardEvent) {
    if (event.key === "Escape") {
      // The dialog closes only through its cancel action (the browser would otherwise close it by itself).
      event.preventDefault()
      event.stopPropagation()
      doCancel()
    } else if (event.key === "Enter" && primary && !primary.disabled) {
      const target = event.target as HTMLElement
      // A focused button does its own action; a multi-line box needs its Enter.
      if (target.tagName === "BUTTON" || target.tagName === "TEXTAREA") return
      event.preventDefault()
      primary.onSelect()
    } else if (event.key === "Tab") {
      const items = Array.from(
        panel.current?.querySelectorAll<HTMLElement>(FOCUSABLE) ?? []
      )
      if (items.length === 0) return
      const first = items[0]
      const last = items[items.length - 1]
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first.focus()
      }
    }
  }

  if (inline && !open) return null
  const Element = inline ? "div" : "dialog"
  return (
    <Element
      ref={panel}
      role={
        kind === "error" || kind === "warning"
          ? "alertdialog"
          : inline
            ? "dialog"
            : undefined
      }
      aria-modal={inline ? undefined : true}
      aria-labelledby={titleId}
      aria-describedby={message != null ? messageId : undefined}
      data-kind={kind}
      data-buttons={actions.length}
      data-inline={inline || undefined}
      className={cn("nt-dialog", className)}
      onKeyDown={onKeyDown}
      onContextMenu={(event: React.MouseEvent) => {
        // Text boxes inside keep their edit menu; elsewhere in the dialog there is no menu.
        if (
          !(event.target as Element).closest("input, textarea, .nt-selectable")
        )
          event.preventDefault()
      }}
      // Escape through the browser (or its close request): only the cancel action decides.
      onCancel={(event: React.SyntheticEvent) => {
        event.preventDefault()
        doCancel()
      }}
      // Closed by the browser anyway (e.g. Escape pressed twice quickly): cancel it properly, or, when it has
      // no cancel action, put it back.
      onClose={() => {
        if (!open || inline) return
        if (cancel) doCancel()
        else panel.current?.showModal()
      }}
    >
      <div className="nt-dialog-body">
        {kind && <span className="nt-dialog-icon">{ICONS[kind]}</span>}
        <div className="nt-dialog-text">
          <h2 id={titleId} className="nt-dialog-title">
            {title}
          </h2>
          {message != null && (
            <div id={messageId} className="nt-dialog-message">
              {message}
            </div>
          )}
          {children}
        </div>
      </div>
      <div className="nt-dialog-actions">
        {actions.map((action, index) => (
          <button
            key={index}
            type="button"
            className="nt-button"
            data-variant={action.primary ? "accent" : "default"}
            data-primary={action.primary || undefined}
            data-cancel={action.cancel || undefined}
            data-destructive={action.destructive || undefined}
            disabled={action.disabled}
            onClick={action.onSelect}
          >
            {action.label}
          </button>
        ))}
      </div>
    </Element>
  )
}

/** Technical details under an error message, folded away: shown on request, and copyable. */
function DialogDetails({ children }: { children: string }) {
  const [shown, setShown] = React.useState(false)
  const [copied, setCopied] = React.useState(false)
  return (
    <div className="nt-dialog-details">
      <button
        type="button"
        className="nt-dialog-disclosure"
        aria-expanded={shown}
        onClick={() => setShown(!shown)}
      >
        <svg viewBox="0 0 16 16" aria-hidden="true">
          <path
            d="m6 4 4 4-4 4"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.5"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
        {shown ? "Hide details" : "Show details"}
      </button>
      {shown && (
        <>
          <pre className="nt-dialog-details-text nt-selectable" tabIndex={0}>
            {children}
          </pre>
          <button
            type="button"
            className="nt-dialog-disclosure"
            onClick={() => {
              void navigator.clipboard
                ?.writeText(children)
                .then(() => setCopied(true))
            }}
          >
            {copied ? "Copied" : "Copy details"}
          </button>
        </>
      )}
    </div>
  )
}

// --- useDialogs: the usual dialogs as promises ------------------------------------------------------

type MessageOptions = {
  title: string
  message?: React.ReactNode
  kind?: DialogKind
  ok?: string
}

type ConfirmOptions = {
  title: string
  message?: React.ReactNode
  kind?: DialogKind
  /** The main button, a verb for what happens (e.g. "Delete"), not "OK". */
  confirm?: string
  cancel?: string
  /** It deletes or loses something (see DialogAction.destructive). */
  destructive?: boolean
}

type ChooseOptions<T extends string> = {
  title: string
  message?: React.ReactNode
  kind?: DialogKind
  /** Most important first; the one with cancel: true is what Escape gives. E.g. Save, Don't save, Cancel. */
  choices: {
    value: T
    label: string
    primary?: boolean
    cancel?: boolean
    destructive?: boolean
  }[]
}

type ErrorOptions = {
  /** What went wrong, short (e.g. "Couldn't send the message"). */
  title: string
  /** What happened and what to do about it. */
  message: React.ReactNode
  /** Technical details (an error code, a stack), folded under "Show details". */
  details?: string
  /** A button that tries again: error() then resolves "retry". */
  retry?: string
  ok?: string
}

type PromptOptions = {
  title: string
  message?: React.ReactNode
  label?: string
  placeholder?: string
  /** What is in the field to begin with (selected, so typing replaces it). */
  value?: string
  /** The main button (e.g. "Rename"). */
  confirm?: string
  cancel?: string
  /** What is wrong with what was typed, or nothing when it is fine; the main button waits until it is. */
  validate?: (value: string) => string | undefined
}

type ProgressOptions = {
  title: string
  message?: React.ReactNode
  /** 0 to 100; left out, the bar just moves (how long it takes is not known). */
  value?: number
  /** Shows a Cancel button; onCancel is called when it is pressed (the dialog stays until close()). */
  onCancel?: () => void
}

/** A progress dialog that is open: change what it says, then close it. */
type ProgressHandle = {
  update: (change: { value?: number; message?: React.ReactNode }) => void
  close: () => void
}

type DialogsApi = {
  /** Something to know, with one button. */
  message: (options: MessageOptions) => Promise<void>
  /** A question with a yes (the action) and Cancel: true when the action was chosen. */
  confirm: (options: ConfirmOptions) => Promise<boolean>
  /** A question with several answers: the value chosen. */
  choose: <T extends string>(options: ChooseOptions<T>) => Promise<T>
  /** What went wrong and what to do; "retry" when the retry button was chosen. */
  error: (options: ErrorOptions) => Promise<"ok" | "retry">
  /** Asks for a line of text: what was typed, or null when cancelled. */
  prompt: (options: PromptOptions) => Promise<string | null>
  /** Work that takes a while, with a progress bar; closes when told to. */
  progress: (options: ProgressOptions) => ProgressHandle
}

type Shown = { id: number; render: (close: () => void) => React.ReactNode }

const DialogsContext = React.createContext<DialogsApi | null>(null)

/** Shows the dialogs useDialogs() asks for, one at a time, in order. NativeProvider puts it in. */
function DialogHost({ children }: { children: React.ReactNode }) {
  const [queue, setQueue] = React.useState<Shown[]>([])
  const nextId = React.useRef(0)

  const api = React.useMemo<DialogsApi>(() => {
    function show(render: Shown["render"]) {
      const id = nextId.current++
      setQueue((shown) => [...shown, { id, render }])
      return () => setQueue((shown) => shown.filter((s) => s.id !== id))
    }
    function ask<T>(
      build: (
        answer: (value: T) => void
      ) => (close: () => void) => React.ReactNode
    ): Promise<T> {
      return new Promise<T>((resolve) => {
        let close = () => {}
        const answer = (value: T) => {
          close()
          resolve(value)
        }
        close = show(build(answer))
      })
    }
    return {
      message: ({ title, message, kind = "info", ok = "OK" }) =>
        ask<void>((answer) => () => (
          <Dialog
            open
            title={title}
            message={message}
            kind={kind}
            actions={[
              {
                label: ok,
                primary: true,
                cancel: true,
                onSelect: () => answer(),
              },
            ]}
          />
        )),
      confirm: ({
        title,
        message,
        kind = "question",
        confirm = "OK",
        cancel = "Cancel",
        destructive,
      }) =>
        ask<boolean>((answer) => () => (
          <Dialog
            open
            title={title}
            message={message}
            kind={kind}
            actions={[
              {
                label: confirm,
                primary: true,
                destructive,
                onSelect: () => answer(true),
              },
              { label: cancel, cancel: true, onSelect: () => answer(false) },
            ]}
          />
        )),
      choose: <T extends string>({
        title,
        message,
        kind = "question",
        choices,
      }: ChooseOptions<T>) =>
        ask<T>((answer) => () => (
          <Dialog
            open
            title={title}
            message={message}
            kind={kind}
            actions={choices.map((c) => ({
              ...c,
              onSelect: () => answer(c.value),
            }))}
          />
        )),
      error: ({ title, message, details, retry, ok = "OK" }) =>
        ask<"ok" | "retry">((answer) => () => (
          <Dialog
            open
            title={title}
            message={message}
            kind="error"
            actions={[
              ...(retry
                ? [
                    {
                      label: retry,
                      primary: true,
                      onSelect: () => answer("retry"),
                    },
                  ]
                : []),
              {
                label: ok,
                primary: !retry,
                cancel: true,
                onSelect: () => answer("ok"),
              },
            ]}
          >
            {details && <DialogDetails>{details}</DialogDetails>}
          </Dialog>
        )),
      prompt: (options) =>
        ask<string | null>((answer) => () => (
          <PromptDialog options={options} answer={answer} />
        )),
      progress: (options) => {
        let set: ((change: Partial<ProgressOptions>) => void) | undefined
        let pending: Partial<ProgressOptions> = {}
        const close = show(() => (
          <ProgressDialog
            options={options}
            connect={(setter) => {
              set = setter
              setter(pending)
            }}
          />
        ))
        return {
          update: (change) => {
            pending = { ...pending, ...change }
            set?.(change)
          },
          close,
        }
      },
    }
  }, [])

  const current = queue[0]
  return (
    <DialogsContext.Provider value={api}>
      {children}
      {current && (
        <React.Fragment key={current.id}>
          {current.render(() => {})}
        </React.Fragment>
      )}
    </DialogsContext.Provider>
  )
}

function PromptDialog({
  options,
  answer,
}: {
  options: PromptOptions
  answer: (value: string | null) => void
}) {
  const {
    title,
    message,
    label,
    placeholder,
    value = "",
    confirm = "OK",
    cancel = "Cancel",
    validate,
  } = options
  const [text, setText] = React.useState(value)
  const [touched, setTouched] = React.useState(false)
  const input = React.useRef<HTMLInputElement>(null)
  const problem = validate?.(text)

  return (
    <Dialog
      open
      title={title}
      message={message}
      initialFocus={input}
      actions={[
        {
          label: confirm,
          primary: true,
          disabled: problem != null,
          onSelect: () => answer(text),
        },
        { label: cancel, cancel: true, onSelect: () => answer(null) },
      ]}
    >
      <TextField
        ref={input}
        className="nt-dialog-field"
        label={label}
        placeholder={placeholder}
        value={text}
        error={touched ? problem : undefined}
        onChange={(event) => {
          setText(event.target.value)
          setTouched(true)
        }}
      />
    </Dialog>
  )
}

function ProgressDialog({
  options,
  connect,
}: {
  options: ProgressOptions
  connect: (setter: (change: Partial<ProgressOptions>) => void) => void
}) {
  const [state, setState] = React.useState<ProgressOptions>(options)
  React.useEffect(
    () => connect((change) => setState((s) => ({ ...s, ...change }))),
    [connect]
  )
  return (
    <Dialog
      open
      title={state.title}
      message={state.message}
      actions={
        state.onCancel
          ? [{ label: "Cancel", cancel: true, onSelect: state.onCancel }]
          : []
      }
    >
      <ProgressBar
        className="nt-dialog-progress"
        aria-label={state.title}
        value={state.value}
      />
    </Dialog>
  )
}

/**
 * The usual dialogs, each a promise: const dialogs = useDialogs();
 * if (await dialogs.confirm({ title: "Delete this chat?", confirm: "Delete", destructive: true })) ...
 * Inside a NativeProvider.
 */
function useDialogs(): DialogsApi {
  const api = React.useContext(DialogsContext)
  if (!api)
    throw new Error(
      "useDialogs() needs a NativeProvider around it: put <NativeProvider> around the app."
    )
  return api
}

export { Dialog, DialogDetails, DialogHost, useDialogs }
export type {
  ChooseOptions,
  ConfirmOptions,
  DialogAction,
  DialogKind,
  DialogProps,
  DialogsApi,
  ErrorOptions,
  MessageOptions,
  ProgressHandle,
  ProgressOptions,
  PromptOptions,
}
