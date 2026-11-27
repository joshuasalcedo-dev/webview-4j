import * as React from "react"

import { cn } from "../lib/utils"
import { Button } from "./button"
import { MoneyField } from "./money"
import { TextField } from "./text-field"
import { formatShortcut, type MenuEntry, useContextMenu } from "./context-menu"
import { useDialogs } from "./dialog"
import { useNative } from "./provider"
import { useSound } from "./sound"
import "./composer-toolbar.css"

/** What a tool can do to the composer it is in. */
type ComposerToolContext = {
  /** What is written now. */
  text: string
  setText: (text: string) => void
  /** Puts text where the caret is (replacing what is selected). */
  insert: (text: string) => void
  /** Puts marks around what is selected ("**", "**"), or where the caret is. */
  wrap: (before: string, after?: string) => void
  focus: () => void
  /** Opens the file picker. */
  attach: () => void
  addFiles: (files: File[]) => void
}

/** One button in the row of tools under the box. Plain data: apps mix their own with the built-in ones. */
type ChatComposerTool = {
  id: string
  /** Its tooltip and accessible name. */
  label: string
  /** 16px. */
  icon: React.ReactNode
  onClick?: (ctx: ComposerToolContext) => void
  /** The pointer came onto it or left it (to show a preview, or fetch something early). */
  onHover?: (hovering: boolean, ctx: ComposerToolContext) => void
  /** As written for code ("Mod+Shift+E"): shown in the tooltip the system's way, and works while typing. */
  shortcut?: string
  /** Shown as on (e.g. the formatting bar is showing). */
  pressed?: boolean
  /** Off; a string says why (its tooltip). */
  disabled?: boolean | string
  hidden?: boolean
  /** A count or a dot on it. */
  badge?: number | boolean
  /** A menu that opens from it (More, Schedule send). */
  menu?: MenuEntry[] | ((ctx: ComposerToolContext) => MenuEntry[])
  /** A small panel above it (an emoji picker, a GIF search). */
  popover?: (
    ctx: ComposerToolContext & { close: () => void }
  ) => React.ReactNode
  /** Draws the tool itself (the voice recorder). */
  render?: (ctx: ComposerToolContext) => React.ReactNode
  /** "start" (the default): the group on the left. "end": on the right, next to Send. */
  placement?: "start" | "end"
}

/** Whether a key press is a shortcut written as "Mod+Shift+E" (Mod: ⌘ on a Mac, Ctrl elsewhere). */
function matchesShortcut(
  event: React.KeyboardEvent,
  shortcut: string,
  platform: "windows" | "mac"
): boolean {
  const keys = shortcut.split("+").map((k) => k.trim().toLowerCase())
  const key = keys[keys.length - 1]
  const wants = (name: string) => keys.includes(name)
  const mod = platform === "mac" ? event.metaKey : event.ctrlKey
  const pressed =
    event.key.toLowerCase() === key ||
    event.code.toLowerCase() === `key${key}` ||
    event.code.toLowerCase() === `digit${key}`
  return (
    pressed &&
    (wants("mod") ? mod : true) &&
    wants("shift") === event.shiftKey &&
    wants("alt") === event.altKey
  )
}

function ToolButton({
  tool,
  ctx,
}: {
  tool: ChatComposerTool
  ctx: ComposerToolContext
}) {
  const { platform } = useNative()
  const [open, setOpen] = React.useState(false)
  const { onContextMenu } = useContextMenu(() =>
    typeof tool.menu === "function" ? tool.menu(ctx) : (tool.menu ?? [])
  )
  const wrapper = React.useRef<HTMLSpanElement>(null)

  React.useEffect(() => {
    if (!open) return
    const away = (event: MouseEvent) => {
      if (!wrapper.current?.contains(event.target as Node)) setOpen(false)
    }
    document.addEventListener("mousedown", away)
    return () => document.removeEventListener("mousedown", away)
  }, [open])

  if (tool.render) return <>{tool.render(ctx)}</>
  const off = !!tool.disabled
  const tip =
    typeof tool.disabled === "string"
      ? tool.disabled
      : tool.shortcut
        ? `${tool.label} (${formatShortcut(tool.shortcut, platform)})`
        : tool.label
  const close = () => {
    setOpen(false)
    ctx.focus()
  }
  return (
    // A disabled button shows no tooltip in some browsers: the wrapper carries it.
    <span ref={wrapper} className="nt-ctool-wrap" title={tip}>
      <button
        type="button"
        className="nt-ctool"
        data-tool={tool.id}
        aria-label={tool.label}
        aria-pressed={tool.pressed}
        aria-expanded={tool.popover ? open : undefined}
        aria-haspopup={tool.menu ? "menu" : tool.popover ? "dialog" : undefined}
        aria-disabled={off || undefined}
        tabIndex={-1}
        onMouseDown={(event) => event.preventDefault()}
        onPointerEnter={() => tool.onHover?.(true, ctx)}
        onPointerLeave={() => tool.onHover?.(false, ctx)}
        onClick={(event) => {
          if (off) return
          if (tool.menu) onContextMenu(event)
          else if (tool.popover) setOpen(!open)
          else tool.onClick?.(ctx)
        }}
      >
        {tool.icon}
        {tool.badge ? (
          <span
            className="nt-ctool-badge"
            data-dot={tool.badge === true || undefined}
          >
            {tool.badge === true ? "" : tool.badge}
          </span>
        ) : null}
      </button>
      {open && tool.popover && (
        <span
          className="nt-ctool-popover"
          role="dialog"
          aria-label={tool.label}
          onKeyDown={(event) => {
            if (event.key === "Escape") {
              event.stopPropagation()
              close()
            }
          }}
        >
          {tool.popover({ ...ctx, close })}
        </span>
      )}
    </span>
  )
}

type ComposerToolbarProps = {
  tools: ChatComposerTool[]
  ctx: ComposerToolContext
  /** Drawn at the very end (Send, on Windows). */
  end?: React.ReactNode
  className?: string
}

/**
 * The row of chat tools under the box, as Teams and Slack have: format, emoji, attach, GIF, mention, voice,
 * more; a group on the left and one on the right by Send. Tab reaches it, ← → move along it (Home/End to the
 * ends), and tooltips give each tool's shortcut.
 */
function ComposerToolbar({ tools, ctx, end, className }: ComposerToolbarProps) {
  const row = React.useRef<HTMLDivElement>(null)
  const [active, setActive] = React.useState(0)
  const shown = tools.filter((t) => !t.hidden)
  const start = shown.filter((t) => t.placement !== "end")
  const last = shown.filter((t) => t.placement === "end")

  // One tool is the row's tab stop (the last one used); the arrow keys move it.
  const buttons = () =>
    Array.from(
      row.current?.querySelectorAll<HTMLButtonElement>(
        "button.nt-ctool, .nt-ctoolbar-end button"
      ) ?? []
    )
  React.useEffect(() => {
    buttons().forEach((b, i) => (b.tabIndex = i === active ? 0 : -1))
  })

  return (
    <div
      ref={row}
      className={cn("nt-ctoolbar", className)}
      role="toolbar"
      aria-label="Message tools"
      onFocus={(event) => {
        const at = buttons().indexOf(
          event.target as unknown as HTMLButtonElement
        )
        if (at >= 0) setActive(at)
      }}
      onKeyDown={(event) => {
        const all = buttons()
        const at = all.indexOf(document.activeElement as HTMLButtonElement)
        if (at < 0) return
        const to =
          event.key === "ArrowRight"
            ? at + 1
            : event.key === "ArrowLeft"
              ? at - 1
              : event.key === "Home"
                ? 0
                : event.key === "End"
                  ? all.length - 1
                  : null
        if (to === null) return
        event.preventDefault()
        const next = all[(to + all.length) % all.length]
        next?.focus()
      }}
    >
      {start.map((tool) => (
        <ToolButton key={tool.id} tool={tool} ctx={ctx} />
      ))}
      <span className="nt-ctoolbar-spacer" />
      {last.map((tool) => (
        <ToolButton key={tool.id} tool={tool} ctx={ctx} />
      ))}
      {end && <span className="nt-ctoolbar-end">{end}</span>}
    </div>
  )
}

// --- the usual tools ----------------------------------------------------------------------------------

const line = {
  viewBox: "0 0 16 16",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 1.3,
  strokeLinecap: "round",
  strokeLinejoin: "round",
  "aria-hidden": true,
} as const

const icons = {
  format: (
    <svg {...line}>
      <path d="M3 13.5 7 2.5h2l4 11M4.7 9.5h6.6" />
    </svg>
  ),
  emoji: (
    <svg {...line}>
      <circle cx="8" cy="8" r="6" />
      <path d="M5.5 9.5a3 3 0 0 0 5 0M6 6.3v.1M10 6.3v.1" />
    </svg>
  ),
  attach: (
    <svg {...line}>
      <path d="M13.5 7.5 8 13a3.5 3.5 0 0 1-5-5l6-6a2.3 2.3 0 0 1 3.3 3.3L6.5 11a1.2 1.2 0 0 1-1.7-1.7L10 4" />
    </svg>
  ),
  gif: (
    <svg viewBox="0 0 16 16" aria-hidden="true">
      <rect
        x="1.5"
        y="3.5"
        width="13"
        height="9"
        rx="2"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.2"
      />
      <text
        x="8"
        y="10.6"
        textAnchor="middle"
        fontSize="5.6"
        fontWeight="700"
        fill="currentColor"
        fontFamily="system-ui, sans-serif"
      >
        GIF
      </text>
    </svg>
  ),
  mention: (
    <svg {...line}>
      <circle cx="8" cy="8" r="2.5" />
      <path d="M10.5 8v1a2 2 0 0 0 4 0V8A6.5 6.5 0 1 0 12 13.2" />
    </svg>
  ),
  mic: (
    <svg {...line}>
      <rect x="6" y="1.5" width="4" height="8" rx="2" />
      <path d="M3.5 7.5a4.5 4.5 0 0 0 9 0M8 12v2.5" />
    </svg>
  ),
  schedule: (
    <svg {...line}>
      <circle cx="8" cy="8" r="5.5" />
      <path d="M8 5v3l2 1.5" />
    </svg>
  ),
  tip: (
    <svg {...line}>
      <circle cx="8" cy="8" r="6" />
      <path d="M9.8 5.8c-.4-.5-1-.8-1.8-.8-1 0-1.8.6-1.8 1.4S7 7.6 8 7.8s1.8.6 1.8 1.4S9 10.8 8 10.8c-.8 0-1.5-.3-1.9-.9M8 4v1M8 10.8V12" />
    </svg>
  ),
  more: (
    <svg viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
      <circle cx="3.5" cy="8" r="1.2" />
      <circle cx="8" cy="8" r="1.2" />
      <circle cx="12.5" cy="8" r="1.2" />
    </svg>
  ),
}

const EMOJI = [
  "😀",
  "😂",
  "😊",
  "😍",
  "🥰",
  "😎",
  "🤔",
  "😮",
  "😢",
  "😡",
  "👍",
  "👎",
  "👏",
  "🙏",
  "🎉",
  "❤️",
  "🔥",
  "✨",
  "💯",
  "✅",
  "👀",
  "🙌",
  "💪",
  "🤝",
]

type GifResult = { url: string; title: string; preview?: string }
type MentionPerson = { id: string; name: string }

/** The usual chat tools, ready to put in `tools` in any order with the app's own. */
const composerTools = {
  /** Shows the formatting bar (bold, italic, strike, code, link as markdown marks). Composer handles it. */
  format: (pressed = false): ChatComposerTool => ({
    id: "format",
    label: "Formatting",
    icon: icons.format,
    shortcut: "Mod+Shift+X",
    pressed,
  }),

  /** A small emoji picker that puts the emoji where the caret is. */
  emoji: (set: string[] = EMOJI): ChatComposerTool => ({
    id: "emoji",
    label: "Emoji",
    icon: icons.emoji,
    shortcut: "Mod+Shift+E",
    popover: ({ insert, close }) => (
      <span className="nt-emoji-grid" role="grid" aria-label="Emoji">
        {set.map((emoji) => (
          <button
            key={emoji}
            type="button"
            aria-label={emoji}
            onClick={() => {
              insert(emoji)
              close()
            }}
          >
            {emoji}
          </button>
        ))}
      </span>
    ),
  }),

  /** The file picker (the paper clip). */
  attach: (): ChatComposerTool => ({
    id: "attach",
    label: "Attach files",
    icon: icons.attach,
    shortcut: "Mod+Shift+A",
    onClick: (c) => c.attach(),
  }),

  /** GIFs: a search box and results above the button; the picked one goes in as its address (or to onPick). */
  gif: (
    search: (query: string) => Promise<GifResult[]>,
    onPick?: (gif: GifResult, ctx: ComposerToolContext) => void
  ): ChatComposerTool => ({
    id: "gif",
    label: "GIFs",
    icon: icons.gif,
    popover: (ctx) => (
      <GifSearch
        search={search}
        onPick={(gif) => (
          onPick ? onPick(gif, ctx) : ctx.insert(gif.url),
          ctx.close()
        )}
      />
    ),
  }),

  /** Mention someone: a list of people (filter by typing), then "@Name " goes in. */
  mention: (people: MentionPerson[]): ChatComposerTool => ({
    id: "mention",
    label: "Mention someone",
    icon: icons.mention,
    shortcut: "Mod+Shift+2",
    popover: (ctx) => (
      <MentionPicker
        people={people}
        onPick={(p) => (ctx.insert(`@${p.name} `), ctx.close())}
      />
    ),
  }),

  /** A voice note: click to start and stop, or hold and let go; `simulate` records nothing (previews, tests). */
  voice: (
    onRecorded: (recording: Recording) => void,
    options: { simulate?: boolean } = {}
  ): ChatComposerTool => ({
    id: "voice",
    label: "Record a voice message",
    icon: icons.mic,
    placement: "end",
    render: () => (
      <VoiceButton
        key="voice"
        onRecorded={onRecorded}
        simulate={options.simulate}
      />
    ),
  }),

  /** Send later: tomorrow morning, Monday morning, or a time typed in. */
  schedule: (
    onSchedule: (at: Date, ctx: ComposerToolContext) => void
  ): ChatComposerTool => ({
    id: "schedule",
    label: "Schedule send",
    icon: icons.schedule,
    placement: "end",
    menu: (ctx) => {
      const at = (days: number, hour: number) => {
        const d = new Date()
        d.setDate(d.getDate() + days)
        d.setHours(hour, 0, 0, 0)
        return d
      }
      const monday = (8 - new Date().getDay()) % 7 || 7
      const words = (d: Date) =>
        d.toLocaleString(undefined, {
          weekday: "long",
          hour: "numeric",
          minute: "2-digit",
        })
      return [
        {
          label: `Tomorrow, ${words(at(1, 9))}`,
          onSelect: () => onSchedule(at(1, 9), ctx),
        },
        {
          label: `${words(at(monday, 9))}`,
          onSelect: () => onSchedule(at(monday, 9), ctx),
        },
      ]
    },
  }),

  /**
   * Send a tip: an amount (and a note) in a small panel. Off unless the chat allows it
   * (ChatCapabilities.sendTip), since the service must take payments.
   */
  tip: (options: {
    currency: string
    /** Sends it; throws with a message a person can read when it fails. */
    onSend: (
      tip: { amount: number; currency: string; note?: string },
      ctx: ComposerToolContext
    ) => void | Promise<void>
  }): ChatComposerTool => ({
    id: "tip",
    label: "Send a tip",
    icon: icons.tip,
    placement: "end",
    popover: (ctx) => (
      <TipForm
        currency={options.currency}
        onSend={(tip) => options.onSend(tip, ctx)}
        onDone={ctx.close}
      />
    ),
  }),

  /** More: the app's own menu. */
  more: (
    menu: MenuEntry[] | ((ctx: ComposerToolContext) => MenuEntry[])
  ): ChatComposerTool => ({
    id: "more",
    label: "More",
    icon: icons.more,
    menu,
    placement: "end",
  }),
}

function TipForm({
  currency,
  onSend,
  onDone,
}: {
  currency: string
  onSend: (tip: {
    amount: number
    currency: string
    note?: string
  }) => void | Promise<void>
  onDone: () => void
}) {
  const [amount, setAmount] = React.useState<number | null>(null)
  const [note, setNote] = React.useState("")
  const [busy, setBusy] = React.useState(false)
  const [problem, setProblem] = React.useState<string>()
  const send = async () => {
    if (!amount || amount <= 0) return setProblem("Type an amount above zero.")
    setBusy(true)
    try {
      await onSend({ amount, currency, note: note.trim() || undefined })
      onDone()
    } catch (error) {
      setProblem(
        error instanceof Error && error.message
          ? error.message
          : "The tip wasn't sent. Nothing was charged. Try again."
      )
    }
    setBusy(false)
  }
  return (
    <span className="nt-tipform">
      <MoneyField
        label="Amount"
        currency={currency}
        value={amount}
        onValueChange={setAmount}
        autoFocus
        error={problem}
      />
      <TextField
        label="Note (optional)"
        value={note}
        onChange={(e) => setNote(e.target.value)}
        maxLength={140}
      />
      <Button variant="accent" disabled={busy} onClick={() => void send()}>
        {busy ? "Sending…" : "Send tip"}
      </Button>
    </span>
  )
}

function GifSearch({
  search,
  onPick,
}: {
  search: (query: string) => Promise<GifResult[]>
  onPick: (gif: GifResult) => void
}) {
  const [query, setQuery] = React.useState("")
  const [results, setResults] = React.useState<GifResult[]>([])
  const [state, setState] = React.useState<"idle" | "loading" | "failed">(
    "loading"
  )
  React.useEffect(() => {
    let current = true
    const timer = window.setTimeout(() => {
      search(query)
        .then((found) => current && (setResults(found), setState("idle")))
        .catch(() => current && setState("failed"))
    }, 250)
    return () => {
      current = false
      window.clearTimeout(timer)
    }
  }, [query, search])
  return (
    <span className="nt-gif">
      <input
        className="nt-input"
        autoFocus
        placeholder="Search GIFs"
        aria-label="Search GIFs"
        value={query}
        onChange={(e) => {
          setQuery(e.target.value)
          setState("loading")
        }}
      />
      <span className="nt-gif-grid">
        {state === "failed" && (
          <span className="nt-gif-note">
            Couldn't load GIFs. Check your connection.
          </span>
        )}
        {state !== "failed" && results.length === 0 && (
          <span className="nt-gif-note">
            {state === "loading" ? "Searching…" : "No GIFs found."}
          </span>
        )}
        {results.map((gif) => (
          <button
            key={gif.url}
            type="button"
            aria-label={gif.title}
            title={gif.title}
            onClick={() => onPick(gif)}
          >
            <img src={gif.preview ?? gif.url} alt="" loading="lazy" />
          </button>
        ))}
      </span>
    </span>
  )
}

function MentionPicker({
  people,
  onPick,
}: {
  people: MentionPerson[]
  onPick: (person: MentionPerson) => void
}) {
  const [query, setQuery] = React.useState("")
  const [active, setActive] = React.useState(0)
  const found = people
    .filter((p) => p.name.toLowerCase().includes(query.toLowerCase()))
    .slice(0, 8)
  return (
    <span className="nt-mention">
      <input
        className="nt-input"
        autoFocus
        placeholder="Who?"
        aria-label="Mention who"
        value={query}
        onChange={(e) => (setQuery(e.target.value), setActive(0))}
        onKeyDown={(e) => {
          if (e.key === "ArrowDown" || e.key === "ArrowUp") {
            e.preventDefault()
            setActive(
              (a) =>
                (a + (e.key === "ArrowDown" ? 1 : -1) + found.length) %
                Math.max(1, found.length)
            )
          } else if (e.key === "Enter" && found[active]) {
            e.preventDefault()
            onPick(found[active])
          }
        }}
      />
      <span role="listbox" className="nt-mention-list">
        {found.length === 0 && (
          <span className="nt-gif-note">Nobody by that name here.</span>
        )}
        {found.map((p, i) => (
          <span
            key={p.id}
            role="option"
            aria-selected={i === active}
            onMouseDown={(e) => (e.preventDefault(), onPick(p))}
            onMouseEnter={() => setActive(i)}
          >
            {p.name}
          </span>
        ))}
      </span>
    </span>
  )
}

type Recording = { blob?: Blob; seconds: number }

function VoiceButton({
  onRecorded,
  simulate = false,
}: {
  onRecorded: (recording: Recording) => void
  simulate?: boolean
}) {
  const dialogs = useDialogs()
  const sound = useSound()
  const [recording, setRecording] = React.useState(false)
  const [seconds, setSeconds] = React.useState(0)
  const started = React.useRef(0)
  const recorder = React.useRef<MediaRecorder | null>(null)
  const chunks = React.useRef<Blob[]>([])
  const held = React.useRef<number | null>(null)
  const keep = React.useRef(true)

  React.useEffect(() => {
    if (!recording) return
    const timer = window.setInterval(
      () => setSeconds((performance.now() - started.current) / 1000),
      200
    )
    return () => window.clearInterval(timer)
  }, [recording])

  const start = async () => {
    keep.current = true
    chunks.current = []
    if (!simulate) {
      try {
        const stream = await navigator.mediaDevices.getUserMedia({
          audio: true,
        })
        const r = new MediaRecorder(stream)
        r.ondataavailable = (e) => chunks.current.push(e.data)
        r.onstop = () => {
          stream.getTracks().forEach((t) => t.stop())
          const length = (performance.now() - started.current) / 1000
          if (keep.current && length > 0.5)
            onRecorded({
              blob: new Blob(chunks.current, { type: r.mimeType }),
              seconds: length,
            })
        }
        r.start()
        recorder.current = r
      } catch {
        void dialogs.error({
          title: "Couldn't use the microphone",
          message:
            "Allow this app to use the microphone in the system's privacy settings, then try again.",
        })
        return
      }
    }
    started.current = performance.now()
    setSeconds(0)
    setRecording(true)
    sound.play("tap")
  }

  const stop = (send: boolean) => {
    keep.current = send
    setRecording(false)
    if (recorder.current) {
      recorder.current.stop()
      recorder.current = null
    } else if (send) {
      const length = (performance.now() - started.current) / 1000
      if (length > 0.5) onRecorded({ seconds: length })
    }
  }

  const time = `${Math.floor(seconds / 60)}:${String(Math.floor(seconds % 60)).padStart(2, "0")}`
  return (
    <span
      className="nt-ctool-wrap nt-voice-rec"
      data-recording={recording || undefined}
      title={
        recording
          ? "Click to stop and send; Esc throws it away"
          : "Record a voice message (click, or hold)"
      }
    >
      {recording && (
        <span className="nt-voice-rec-time" role="status" aria-live="polite">
          <span className="nt-voice-rec-dot" aria-hidden="true" />
          {time}
        </span>
      )}
      <button
        type="button"
        className="nt-ctool"
        data-tool="voice"
        aria-label={
          recording
            ? "Stop and send the voice message"
            : "Record a voice message"
        }
        aria-pressed={recording}
        tabIndex={-1}
        onMouseDown={(event) => event.preventDefault()}
        onPointerDown={() => {
          // Held for a moment: record while held, send when let go.
          if (recording) return
          held.current = window.setTimeout(() => {
            held.current = -1
            void start()
          }, 350)
        }}
        onPointerUp={() => {
          if (held.current === -1) {
            held.current = null
            stop(true)
          } else if (held.current) {
            window.clearTimeout(held.current)
            held.current = null
            if (recording) stop(true)
            else void start()
          }
        }}
        onKeyDown={(event) => {
          if (event.key === "Escape" && recording) {
            event.stopPropagation()
            stop(false)
          } else if (
            (event.key === "Enter" || event.key === " ") &&
            !event.repeat
          ) {
            event.preventDefault()
            if (recording) stop(true)
            else void start()
          }
        }}
      >
        {icons.mic}
      </button>
    </span>
  )
}

/** What the usual tools need from the app (each one left out drops its tool). */
type DefaultToolsOptions = {
  /** People to mention (the @ tool shows when given). */
  people?: MentionPerson[]
  /** GIF search (the GIF tool shows when given). */
  gifSearch?: (query: string) => Promise<GifResult[]>
  /** Send later (the clock tool shows when given). */
  onSchedule?: (at: Date, ctx: ComposerToolContext) => void
  /** A voice note recorded; by default it is attached to the message as an audio file. */
  onVoice?: (recording: Recording, ctx: ComposerToolContext) => void
  /** Records nothing (previews and tests). */
  simulateVoice?: boolean
  /** A tip sent (the tip tool shows when given, and when the chat allows tips). */
  tip?: { currency: string; onSend: (tip: { amount: number; currency: string; note?: string }, ctx: ComposerToolContext) => void | Promise<void> }
  /** More: the app's own menu (the … tool shows when given). */
  more?: MenuEntry[] | ((ctx: ComposerToolContext) => MenuEntry[])
}

/**
 * The usual chat tools, in their usual order: formatting, emoji, attach, GIF, mention, then on the right
 * voice, schedule, tip, more. The ones that need the app (people, GIF search, scheduling, tips, a menu) show
 * only when it gives them. ChatComposer uses this when it is given no `tools`.
 */
function defaultComposerTools(options: DefaultToolsOptions = {}): ChatComposerTool[] {
  const tools: ChatComposerTool[] = [composerTools.format(), composerTools.emoji(), composerTools.attach()]
  if (options.gifSearch) tools.push(composerTools.gif(options.gifSearch))
  if (options.people) tools.push(composerTools.mention(options.people))
  tools.push({
    ...composerTools.voice(() => undefined, { simulate: options.simulateVoice }),
    // The recorder hands its recording to onVoice, or attaches it as an audio file.
    render: (ctx) =>
      composerTools
        .voice(
          (recording) => {
            if (options.onVoice) options.onVoice(recording, ctx)
            else if (recording.blob) ctx.addFiles([new File([recording.blob], "Voice message.webm", { type: recording.blob.type })])
          },
          { simulate: options.simulateVoice },
        )
        .render?.(ctx),
  })
  if (options.onSchedule) tools.push(composerTools.schedule(options.onSchedule))
  if (options.tip) tools.push(composerTools.tip(options.tip))
  if (options.more) tools.push(composerTools.more(options.more))
  return tools
}

export { ComposerToolbar, composerTools, defaultComposerTools, matchesShortcut }
export type { DefaultToolsOptions }
export type {
  ChatComposerTool,
  ComposerToolbarProps,
  ComposerToolContext,
  GifResult,
  MentionPerson,
  Recording,
}
