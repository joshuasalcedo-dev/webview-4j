import * as React from "react"

import { cn } from "../lib/utils"
import {
  type ChatComposerTool,
  ComposerToolbar,
  type ComposerToolContext,
  matchesShortcut,
} from "./composer-toolbar"
import { formatShortcut } from "./context-menu"
import { FileChip } from "./files"
import { useNative } from "./provider"
import { useSound } from "./sound"
import { formatFileSize } from "../lib/file-icons"
import { fitHeight } from "./text-inputs"

/** What is sent: the text (trimmed) and the files attached. */
type Message = { text: string; files: File[] }

type ComposerProps = Omit<React.ComponentProps<"div">, "onSubmit"> & {
  /** Sends the message; the box empties when it returns (or its promise resolves), and keeps it when it throws. */
  onSend: (message: Message) => void | Promise<void>
  placeholder?: string
  /** "enter" (the default): Enter sends and Shift+Enter starts a new line. "mod+enter": Enter starts a new line and Ctrl+Enter (⌘↩) sends. */
  sendKey?: "enter" | "mod+enter"
  /** Files can be attached (the paper clip, pasting, dropping). True by default. */
  attachments?: boolean
  /** File kinds the paper clip offers, as an <input accept> (e.g. "image/*,.pdf"). */
  accept?: string
  /** Longest message; a count shows near the end. */
  maxLength?: number
  /** Called at most every few seconds while typing (for "typing…" on the other side). */
  onTyping?: () => void
  /** ↑ in the empty box: edit the last message sent (as chat apps do). */
  onEditLast?: () => void
  /** Something is being sent: the box waits. */
  sending?: boolean
  disabled?: boolean
  /** What the box is for, for screen readers (e.g. "Message Ana Reyes"). */
  "aria-label"?: string
  /** Buttons before the send button (emoji, voice note). */
  extraButtons?: React.ReactNode
  /** The text in the box to begin with (e.g. a message being edited); give the composer a new key to load another. */
  defaultText?: string
  /** Puts the caret in the box when it appears (at the end of defaultText). */
  autoFocus?: boolean
  /**
   * A row of tools under the box (format, emoji, attach, GIF, mention, voice, the app's own): see
   * composerTools. With tools the paper clip is one of them (composerTools.attach()); without, the box looks
   * as it always has.
   */
  tools?: ChatComposerTool[]
  /** Send is off for now; this says why (Send's tooltip), e.g. "You're sending messages too fast". */
  sendDisabledReason?: string
  /** The biggest file that may be attached, in bytes: bigger ones are refused, with the reason under the box. */
  maxFileBytes?: number
  /** Filled with the composer's handle (insert text, focus, attach) for use from outside. */
  apiRef?: React.RefObject<ComposerToolContext | null>
}

const PaperClip = () => (
  <svg
    viewBox="0 0 16 16"
    fill="none"
    stroke="currentColor"
    strokeWidth="1.2"
    strokeLinecap="round"
    strokeLinejoin="round"
    aria-hidden="true"
  >
    <path d="M13.5 7.5 8 13a3.5 3.5 0 0 1-5-5l6-6a2.3 2.3 0 0 1 3.3 3.3L6.5 11a1.2 1.2 0 0 1-1.7-1.7L10 4" />
  </svg>
)

const SendArrow = () => (
  <svg viewBox="0 0 16 16" aria-hidden="true">
    <path
      d="M2 13.5 14.5 8 2 2.5 3.8 8zM3.8 8H9"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.3"
      strokeLinejoin="round"
      strokeLinecap="round"
    />
  </svg>
)

/**
 * The box at the bottom of a chat where a message is written: it grows with the text (up to a limit, then
 * scrolls), Enter sends and Shift+Enter starts a new line (or the other way round), files attach with the
 * paper clip or by pasting or dropping them (shown as FileChips with an ×), and sending plays the "sent"
 * sound. Typing in another language's input method never sends half a word.
 */
function Composer({
  onSend,
  placeholder = "Type a message",
  sendKey = "enter",
  attachments = true,
  accept,
  maxLength,
  onTyping,
  onEditLast,
  sending = false,
  disabled = false,
  "aria-label": label = "Message",
  extraButtons,
  defaultText = "",
  autoFocus = false,
  tools,
  sendDisabledReason,
  maxFileBytes,
  apiRef,
  className,
  ...props
}: ComposerProps) {
  const { platform } = useNative()
  const sound = useSound()
  const [text, setText] = React.useState(defaultText)
  const [files, setFiles] = React.useState<File[]>([])
  const [dropping, setDropping] = React.useState(false)
  const [failed, setFailed] = React.useState<string>()
  const box = React.useRef<HTMLTextAreaElement>(null)
  React.useEffect(() => {
    const element = box.current
    if (!autoFocus || !element) return
    element.focus()
    element.setSelectionRange(element.value.length, element.value.length)
    // Only when it appears.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])
  const picker = React.useRef<HTMLInputElement>(null)
  const lastTyping = React.useRef(0)

  React.useLayoutEffect(() => {
    if (box.current) fitHeight(box.current, 1, 8)
  }, [text])

  const canSend =
    !disabled &&
    !sending &&
    !sendDisabledReason &&
    (text.trim() !== "" || files.length > 0)

  // What the tools can do to the box.
  const [formatting, setFormatting] = React.useState(false)
  const replaceSelection = (before: string, after = "") => {
    const element = box.current
    const from = element?.selectionStart ?? text.length
    const to = element?.selectionEnd ?? text.length
    const next =
      text.slice(0, from) +
      before +
      text.slice(from, to) +
      after +
      text.slice(to)
    setText(next)
    requestAnimationFrame(() => {
      element?.focus()
      const caret = from + before.length + (to - from)
      element?.setSelectionRange(
        after ? from + before.length : caret,
        after ? caret : caret
      )
    })
  }
  const ctx: ComposerToolContext = {
    text,
    setText,
    insert: (inserted) => replaceSelection(inserted),
    wrap: (before, after = before) => replaceSelection(before, after),
    focus: () => box.current?.focus(),
    attach: () => picker.current?.click(),
    addFiles: (added) => attach(added),
  }
  React.useEffect(() => {
    if (apiRef) apiRef.current = ctx
  })
  const shownTools = tools?.map((tool) =>
    tool.id === "format"
      ? {
          ...tool,
          pressed: formatting,
          onClick: tool.onClick ?? (() => setFormatting((on) => !on)),
        }
      : tool
  )

  async function send() {
    if (!canSend) return
    try {
      setFailed(undefined)
      await onSend({ text: text.trim(), files })
      setText("")
      setFiles([])
      sound.play("sent")
    } catch (problem) {
      // The message stays in the box, so nothing is lost; the reason shows under it.
      setFailed(
        problem instanceof Error
          ? problem.message
          : "The message wasn't sent. Try again."
      )
      sound.play("error")
    }
    box.current?.focus()
  }

  /** Whether a file is of a kind `accept` allows ("image/*", ".pdf", "application/zip"). */
  const acceptable = (file: File) => {
    if (!accept) return true
    const name = file.name.toLowerCase()
    return accept.split(",").some((rule) => {
      const r = rule.trim().toLowerCase()
      if (!r) return false
      if (r.startsWith(".")) return name.endsWith(r)
      if (r.endsWith("/*")) return file.type.startsWith(r.slice(0, -1))
      return file.type === r
    })
  }

  const attach = (offered: File[]) => {
    if (!attachments || offered.length === 0) return
    // Files that may not be sent are refused here (pasted, dropped or picked), with the reason under the box.
    const tooBig = offered.filter(
      (f) => maxFileBytes != null && f.size > maxFileBytes
    )
    const wrongKind = offered.filter((f) => !acceptable(f))
    const added = offered.filter(
      (f) => !tooBig.includes(f) && !wrongKind.includes(f)
    )
    if (tooBig.length > 0)
      setFailed(
        `${tooBig.map((f) => f.name).join(", ")} ${tooBig.length === 1 ? "is" : "are"} too large to send: the limit is ${formatFileSize(maxFileBytes ?? 0, platform)}.`
      )
    else if (wrongKind.length > 0)
      setFailed(
        `${wrongKind.map((f) => f.name).join(", ")} can't be sent here: only ${accept} files can.`
      )
    else setFailed(undefined)
    if (added.length === 0) return
    setFiles((now) => [
      ...now,
      ...added.filter(
        (f) => !now.some((n) => n.name === f.name && n.size === f.size)
      ),
    ])
  }

  const sendShortcut =
    sendKey === "enter"
      ? "Enter"
      : platform === "mac"
        ? "Mod+Enter"
        : "Ctrl+Enter"
  const nearLimit = maxLength != null && text.length > maxLength * 0.8

  // A disabled button shows no tooltip in some browsers: the wrapper carries the reason.
  const sendButton = (
    <span
      className="nt-send-wrap"
      title={
        sendDisabledReason ?? `Send (${formatShortcut(sendShortcut, platform)})`
      }
    >
      <button
        type="button"
        className="nt-composer-send"
        aria-label="Send"
        aria-describedby={
          sendDisabledReason ? "nt-composer-send-why" : undefined
        }
        disabled={!canSend}
        data-busy={sending || undefined}
        onClick={() => void send()}
      >
        <SendArrow />
      </button>
      {sendDisabledReason && (
        <span id="nt-composer-send-why" hidden>
          {sendDisabledReason}
        </span>
      )}
    </span>
  )

  return (
    <div
      className={cn("nt-composer", className)}
      data-dropping={dropping || undefined}
      data-disabled={disabled || undefined}
      data-tools={shownTools ? "" : undefined}
      onDragOver={(event) => {
        if (!attachments || !event.dataTransfer.types.includes("Files")) return
        event.preventDefault()
        setDropping(true)
      }}
      onDragLeave={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget as Node))
          setDropping(false)
      }}
      onDrop={(event) => {
        if (!attachments) return
        event.preventDefault()
        setDropping(false)
        attach(Array.from(event.dataTransfer.files))
      }}
      {...props}
    >
      {files.length > 0 && (
        <div className="nt-composer-files">
          {files.map((file) => (
            <FileChip
              key={`${file.name}-${file.size}`}
              name={file.name}
              size={file.size}
              onRemove={() => setFiles(files.filter((f) => f !== file))}
            />
          ))}
        </div>
      )}
      {formatting && (
        <div className="nt-formatbar" role="toolbar" aria-label="Formatting">
          {[
            { label: "B", name: "Bold", marks: "**", shortcut: "Mod+B" },
            { label: "I", name: "Italic", marks: "_", shortcut: "Mod+I" },
            { label: "S", name: "Strikethrough", marks: "~~" },
            { label: "</>", name: "Code", marks: "`" },
          ].map((f) => (
            <button
              key={f.name}
              type="button"
              aria-label={f.name}
              title={
                f.shortcut
                  ? `${f.name} (${formatShortcut(f.shortcut, platform)})`
                  : f.name
              }
              onMouseDown={(event) => event.preventDefault()}
              onClick={() => ctx.wrap(f.marks)}
              style={{
                fontWeight: f.label === "B" ? 700 : undefined,
                fontStyle: f.label === "I" ? "italic" : undefined,
                textDecoration: f.label === "S" ? "line-through" : undefined,
              }}
            >
              {f.label}
            </button>
          ))}
        </div>
      )}
      <div className="nt-composer-row">
        {attachments && (
          <>
            {!tools && (
              <button
                type="button"
                className="nt-composer-button"
                aria-label="Attach files"
                title="Attach files"
                disabled={disabled}
                onClick={() => picker.current?.click()}
              >
                <PaperClip />
              </button>
            )}
            <input
              ref={picker}
              type="file"
              multiple
              accept={accept}
              hidden
              onChange={(event) => {
                attach(Array.from(event.target.files ?? []))
                event.target.value = ""
              }}
            />
          </>
        )}
        <textarea
          ref={box}
          className="nt-composer-input"
          rows={1}
          aria-label={label}
          placeholder={placeholder}
          value={text}
          maxLength={maxLength}
          disabled={disabled}
          aria-describedby={failed ? "nt-composer-failed" : undefined}
          onChange={(event) => {
            setText(event.target.value)
            if (onTyping && Date.now() - lastTyping.current > 3000) {
              lastTyping.current = Date.now()
              onTyping()
            }
          }}
          onPaste={(event) => {
            const pasted = Array.from(event.clipboardData.files)
            if (attachments && pasted.length > 0) {
              event.preventDefault()
              attach(pasted)
            }
          }}
          onKeyDown={(event) => {
            // A word still being put together in an input method (Japanese, Chinese, ...) is not sent.
            if (event.nativeEvent.isComposing) return
            const tool = shownTools?.find(
              (t) =>
                t.shortcut &&
                !t.hidden &&
                !t.disabled &&
                matchesShortcut(event, t.shortcut, platform)
            )
            if (tool) {
              event.preventDefault()
              if (tool.onClick) tool.onClick(ctx)
              else if (tool.popover || tool.menu)
                event.currentTarget
                  .closest(".nt-composer")
                  ?.querySelector<HTMLButtonElement>(`[data-tool="${tool.id}"]`)
                  ?.click()
              return
            }
            if (
              formatting &&
              (event.metaKey || event.ctrlKey) &&
              !event.shiftKey &&
              (event.key === "b" || event.key === "i")
            ) {
              event.preventDefault()
              ctx.wrap(event.key === "b" ? "**" : "_")
              return
            }
            const mod = platform === "mac" ? event.metaKey : event.ctrlKey
            if (event.key === "Enter") {
              const sends = sendKey === "enter" ? !event.shiftKey && !mod : mod
              if (sends) {
                event.preventDefault()
                void send()
              }
            } else if (event.key === "ArrowUp" && text === "" && onEditLast) {
              event.preventDefault()
              onEditLast()
            }
          }}
        />
        {extraButtons}
        {!(shownTools && platform === "windows") && sendButton}
      </div>
      {shownTools && (
        <ComposerToolbar
          tools={shownTools}
          ctx={ctx}
          end={platform === "windows" ? sendButton : undefined}
        />
      )}
      {(failed || nearLimit) && (
        <div className="nt-composer-foot">
          {failed && (
            <span
              id="nt-composer-failed"
              className="nt-field-hint"
              data-error=""
            >
              {failed}
            </span>
          )}
          {nearLimit && (
            <span
              className="nt-field-count"
              data-full={text.length >= (maxLength ?? 0) || undefined}
            >
              {text.length}/{maxLength}
            </span>
          )}
        </div>
      )}
      {dropping && (
        <div className="nt-composer-drop">Drop files to attach them</div>
      )}
    </div>
  )
}

export { Composer }
export type { ComposerProps, Message }
