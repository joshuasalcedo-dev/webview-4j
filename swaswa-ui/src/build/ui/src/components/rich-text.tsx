import "./rich-text.css"

import Placeholder from "@tiptap/extension-placeholder"
import {
  type Editor,
  EditorContent,
  type JSONContent,
  useEditor,
  useEditorState,
} from "@tiptap/react"
import StarterKit from "@tiptap/starter-kit"
import * as React from "react"

import type { Platform } from "../lib/platform"
import { cn } from "../lib/utils"
import { formatShortcut, type MenuEntry, useContextMenu } from "./context-menu"
import { useDialogs } from "./dialog"
import { useNative } from "./provider"
import { Select } from "./select"

/** What the editor holds, in the three forms an app may want to keep or send. */
type RichTextValue = { html: string; json: JSONContent; text: string }

type RichTextEditorProps = {
  /** What it holds, as HTML. Given, the editor follows it (controlled); defaultValue only starts it. */
  value?: string
  defaultValue?: string
  /** Every change: the HTML, TipTap's JSON and the plain text. */
  onChange?: (value: RichTextValue) => void
  /** Shown, greyed, while it is empty. */
  placeholder?: string
  /** It can be read and copied, not changed (and the toolbar is gone). */
  readOnly?: boolean
  /** The formatting bar above the text. True by default. */
  toolbar?: boolean
  /** What it is for, for screen readers (e.g. "Message"). */
  "aria-label": string
  autoFocus?: boolean
  /** The text area's smallest height in px (160 by default); it grows with the text. */
  minHeight?: number
  /** Fills its parent's height instead (the text area scrolls). */
  fill?: boolean
  className?: string
  style?: React.CSSProperties
}

// --- icons: 16px strokes in currentColor ----------------------------------------------------------------

const svg = {
  viewBox: "0 0 16 16",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 1.3,
  strokeLinecap: "round",
  strokeLinejoin: "round",
  "aria-hidden": true,
} as const

const Icons = {
  undo: (
    <svg {...svg}>
      <path d="M5.5 3.5 2.5 6.5l3 3M2.5 6.5h7a4 4 0 0 1 0 8H7" />
    </svg>
  ),
  redo: (
    <svg {...svg}>
      <path d="m10.5 3.5 3 3-3 3M13.5 6.5h-7a4 4 0 0 0 0 8H9" />
    </svg>
  ),
  bold: (
    <svg {...svg} strokeWidth={1.8}>
      <path d="M4.5 2.5h4.25a2.75 2.75 0 0 1 0 5.5H4.5zM4.5 8h5a3 3 0 0 1 0 6h-5z" />
    </svg>
  ),
  italic: (
    <svg {...svg}>
      <path d="M7 2.5h5M4 13.5h5M9.5 2.5l-3 11" />
    </svg>
  ),
  underline: (
    <svg {...svg}>
      <path d="M4.5 2.5V7a3.5 3.5 0 0 0 7 0V2.5M3.5 14h9" />
    </svg>
  ),
  strike: (
    <svg {...svg}>
      <path d="M2.5 8h11M11 4.5C10.5 3.3 9.4 2.5 8 2.5c-1.9 0-3.2 1-3.2 2.5 0 .9.4 1.5 1.1 2M5 11.3c.5 1.3 1.6 2.2 3.1 2.2 2 0 3.3-1.1 3.3-2.6 0-.5-.1-.9-.4-1.3" />
    </svg>
  ),
  code: (
    <svg {...svg}>
      <path d="M5.5 4.5 2 8l3.5 3.5M10.5 4.5 14 8l-3.5 3.5" />
    </svg>
  ),
  bulletList: (
    <svg {...svg}>
      <path d="M6.5 4h7M6.5 8h7M6.5 12h7" />
      <circle cx="3" cy="4" r=".9" fill="currentColor" stroke="none" />
      <circle cx="3" cy="8" r=".9" fill="currentColor" stroke="none" />
      <circle cx="3" cy="12" r=".9" fill="currentColor" stroke="none" />
    </svg>
  ),
  orderedList: (
    <svg {...svg}>
      <path d="M6.5 4h7M6.5 8h7M6.5 12h7M2.5 2.8l1-.6v3.3M2.3 9.6c.2-.6.7-.9 1.2-.9.6 0 1 .4 1 .9 0 .9-2.2 1.6-2.2 2.5h2.2" />
    </svg>
  ),
  quote: (
    <svg {...svg}>
      <path d="M3 4v8M6 5h7.5M6 8h7.5M6 11h5" />
    </svg>
  ),
  codeBlock: (
    <svg {...svg}>
      <rect x="1.5" y="2.5" width="13" height="11" rx="2" />
      <path d="M6 6.5 4.5 8 6 9.5M10 6.5 11.5 8 10 9.5" />
    </svg>
  ),
  link: (
    <svg {...svg}>
      <path d="M6.8 9.2a2.8 2.8 0 0 0 4 0l2-2a2.8 2.8 0 0 0-4-4l-.6.6M9.2 6.8a2.8 2.8 0 0 0-4 0l-2 2a2.8 2.8 0 0 0 4 4l.6-.6" />
    </svg>
  ),
}

// --- what the bar and the menu read from the editor --------------------------------------------------------

type Block = "p" | "h1" | "h2" | "h3"

/** The paragraph styles, in each system's words (TextEdit's styles on a Mac, WordPad's on Windows). */
function blockOptions(platform: Platform) {
  return platform === "mac"
    ? [
        { value: "p", label: "Body" },
        { value: "h1", label: "Title" },
        { value: "h2", label: "Heading" },
        { value: "h3", label: "Subheading" },
      ]
    : [
        { value: "p", label: "Normal" },
        { value: "h1", label: "Heading 1" },
        { value: "h2", label: "Heading 2" },
        { value: "h3", label: "Heading 3" },
      ]
}

function readState(editor: Editor) {
  const block: Block = editor.isActive("heading", { level: 1 })
    ? "h1"
    : editor.isActive("heading", { level: 2 })
      ? "h2"
      : editor.isActive("heading", { level: 3 })
        ? "h3"
        : "p"
  return {
    block,
    bold: editor.isActive("bold"),
    italic: editor.isActive("italic"),
    underline: editor.isActive("underline"),
    strike: editor.isActive("strike"),
    code: editor.isActive("code"),
    link: editor.isActive("link"),
    bulletList: editor.isActive("bulletList"),
    orderedList: editor.isActive("orderedList"),
    blockquote: editor.isActive("blockquote"),
    codeBlock: editor.isActive("codeBlock"),
    canUndo: editor.can().undo(),
    canRedo: editor.can().redo(),
    selected: !editor.state.selection.empty,
  }
}

/** "https://" in front of an address typed without one; mailto: for an email address. */
function withProtocol(address: string): string {
  const trimmed = address.trim()
  if (/^[a-z][a-z0-9+.-]*:/i.test(trimmed)) return trimmed
  if (/^[^\s@/]+@[^\s@/]+\.[^\s@/]+$/.test(trimmed)) return `mailto:${trimmed}`
  return `https://${trimmed}`
}

/** Why an address can't be a link, or nothing when it can (web, email and phone links only). */
function addressProblem(address: string): string | undefined {
  if (address.trim() === "") return undefined
  try {
    const url = new URL(withProtocol(address))
    if (!["http:", "https:", "mailto:", "tel:"].includes(url.protocol))
      return "Only web, email (mailto:) and phone (tel:) links work."
    if (
      (url.protocol === "http:" || url.protocol === "https:") &&
      !url.hostname.includes(".") &&
      url.hostname !== "localhost"
    )
      return "Type a whole address, e.g. example.com."
    return undefined
  } catch {
    return "That isn't an address. Type one like example.com."
  }
}

// --- the editor ---------------------------------------------------------------------------------------------

/**
 * Formatted text, as WordPad or Notepad's formatting on Windows and TextEdit on a Mac: bold, italic, underline,
 * strikethrough, code, headings, lists, quotes, code blocks and links, from the bar above it, the right-click
 * menu, or the keys (Ctrl+B / ⌘B and the rest; Ctrl+K / ⌘K for a link). Built on TipTap; the value is HTML.
 */
function RichTextEditor({
  value,
  defaultValue,
  onChange,
  placeholder,
  readOnly = false,
  toolbar = true,
  "aria-label": label,
  autoFocus = false,
  minHeight = 160,
  fill = false,
  className,
  style,
}: RichTextEditorProps) {
  const { platform } = useNative()
  const dialogs = useDialogs()
  const mac = platform === "mac"
  const keys = (shortcut: string) => formatShortcut(shortcut, platform)

  // The editor is made once; what it calls later goes through these, so it always uses the latest.
  const onChangeRef = React.useRef(onChange)
  const linkRef = React.useRef<() => void>(() => undefined)
  const macRef = React.useRef(mac)
  React.useEffect(() => {
    onChangeRef.current = onChange
    macRef.current = mac
  })

  // The last values the editor gave out through onChange (see the controlled value below).
  const given = React.useRef(new Set<string>())
  const remember = (html: string) => {
    given.current.add(html)
    if (given.current.size > 50)
      given.current.delete(given.current.values().next().value as string)
  }

  const editor = useEditor({
    extensions: [
      StarterKit.configure({
        heading: { levels: [1, 2, 3] },
        link: { openOnClick: false, autolink: true, defaultProtocol: "https" },
      }),
      Placeholder.configure({ placeholder: placeholder ?? "" }),
    ],
    content: value ?? defaultValue ?? "",
    editable: !readOnly,
    autofocus: autoFocus ? "end" : false,
    immediatelyRender: true,
    editorProps: {
      attributes: {
        class: "nt-rt-content",
        "aria-label": label,
        "aria-multiline": "true",
        role: "textbox",
        spellcheck: "true",
      },
      // Ctrl+K / ⌘K: add, change or take away a link (TipTap has no key for it).
      handleKeyDown: (_view, event) => {
        const mod = macRef.current ? event.metaKey : event.ctrlKey
        if (
          mod &&
          !event.shiftKey &&
          !event.altKey &&
          event.key.toLowerCase() === "k"
        ) {
          event.preventDefault()
          linkRef.current()
          return true
        }
        return false
      },
    },
    onUpdate: ({ editor: changed }) => {
      const html = changed.getHTML()
      remember(html)
      onChangeRef.current?.({
        html,
        json: changed.getJSON(),
        text: changed.getText(),
      })
    },
  })

  // Controlled: the editor takes a value only when it came from outside. A value it gave out itself, even one
  // a few keystrokes old (React can be a step behind fast typing), is never put back: that would undo the
  // newest keystrokes and send the cursor to the start.
  React.useEffect(() => {
    if (
      value === undefined ||
      value === editor.getHTML() ||
      given.current.has(value)
    )
      return
    editor.commands.setContent(value, { emitUpdate: false })
    given.current.clear()
  }, [value, editor])

  React.useEffect(() => {
    editor.setEditable(!readOnly, false)
  }, [readOnly, editor])

  const state = useEditorState({
    editor,
    selector: ({ editor: e }) => readState(e),
  })

  /** Asks for the address of a link: a new one on the chosen text, or the link under the cursor changed. */
  const editLink = async () => {
    if (readOnly) return
    const current = editor.getAttributes("link").href as string | undefined
    const { empty } = editor.state.selection
    const address = await dialogs.prompt({
      title: current ? "Edit link" : "Add a link",
      message: current
        ? "Change the address, or empty the box to take the link away."
        : undefined,
      label: "Address",
      placeholder: "example.com",
      value: current ?? "",
      confirm: current ? "Save" : "Add link",
      validate: (typed) =>
        typed.trim() === "" && !current
          ? "Type the address the link goes to."
          : addressProblem(typed),
    })
    if (address === null) {
      editor.commands.focus()
      return
    }
    const chain = editor.chain().focus().extendMarkRange("link")
    if (address.trim() === "") {
      chain.unsetLink().run()
      return
    }
    const href = withProtocol(address)
    if (empty && !current) {
      // Nothing chosen: the address goes in as the link's text.
      chain
        .insertContent({
          type: "text",
          text: address.trim(),
          marks: [{ type: "link", attrs: { href } }],
        })
        .run()
    } else {
      chain.setLink({ href }).run()
    }
  }
  React.useEffect(() => {
    linkRef.current = () => void editLink()
  })

  const run =
    (
      command: (
        chain: ReturnType<Editor["chain"]>
      ) => ReturnType<Editor["chain"]>
    ) =>
    () =>
      command(editor.chain().focus()).run()

  const setBlock = (block: Block) => {
    const chain = editor.chain().focus()
    if (block === "p") chain.setParagraph().run()
    else chain.setHeading({ level: Number(block[1]) as 1 | 2 | 3 }).run()
  }

  // The right-click menu: the edit items, then the formatting that applies to the chosen text.
  const menu = (): MenuEntry[] => {
    const editable = !readOnly
    return [
      {
        label: "Cut",
        shortcut: "Mod+X",
        disabled: !editable || !state.selected,
        onSelect: () => document.execCommand("cut"),
      },
      {
        label: "Copy",
        shortcut: "Mod+C",
        disabled: !state.selected,
        onSelect: () => document.execCommand("copy"),
      },
      {
        label: "Paste",
        shortcut: "Mod+V",
        disabled: !editable,
        onSelect: () => {
          // A webview may not paste by itself; reading the clipboard and putting the text in keeps Undo working.
          void navigator.clipboard
            ?.readText()
            .then((text) => editor.chain().focus().insertContent(text).run())
        },
      },
      {
        label: mac ? "Select All" : "Select all",
        shortcut: "Mod+A",
        onSelect: () => editor.chain().focus().selectAll().run(),
      },
      ...(editable
        ? ([
            "separator",
            {
              label: "Bold",
              shortcut: "Mod+B",
              checked: state.bold,
              onSelect: run((c) => c.toggleBold()),
            },
            {
              label: "Italic",
              shortcut: "Mod+I",
              checked: state.italic,
              onSelect: run((c) => c.toggleItalic()),
            },
            {
              label: "Underline",
              shortcut: "Mod+U",
              checked: state.underline,
              onSelect: run((c) => c.toggleUnderline()),
            },
            "separator",
            {
              label: state.link ? "Edit link…" : "Link…",
              shortcut: "Mod+K",
              onSelect: () => void editLink(),
            },
            ...(state.link
              ? [
                  {
                    label: "Remove link",
                    onSelect: run((c) => c.extendMarkRange("link").unsetLink()),
                  },
                ]
              : []),
            {
              label: "Clear formatting",
              onSelect: run((c) => c.unsetAllMarks().clearNodes()),
            },
          ] satisfies MenuEntry[])
        : []),
    ]
  }
  const { onContextMenu } = useContextMenu(menu)

  const button = (
    name: keyof typeof Icons,
    title: string,
    action: () => void,
    options: { pressed?: boolean; shortcut?: string; disabled?: boolean } = {}
  ) => (
    <button
      type="button"
      className="nt-rt-button"
      aria-label={title}
      aria-pressed={options.pressed}
      title={options.shortcut ? `${title} (${keys(options.shortcut)})` : title}
      disabled={options.disabled}
      // Keep the text's selection while the button is pressed.
      onMouseDown={(event) => event.preventDefault()}
      onClick={action}
    >
      {Icons[name]}
    </button>
  )

  const marks = (
    <span className="nt-rt-group" role="group" aria-label="Text style">
      {button(
        "bold",
        "Bold",
        run((c) => c.toggleBold()),
        { pressed: state.bold, shortcut: "Mod+B" }
      )}
      {button(
        "italic",
        "Italic",
        run((c) => c.toggleItalic()),
        { pressed: state.italic, shortcut: "Mod+I" }
      )}
      {button(
        "underline",
        "Underline",
        run((c) => c.toggleUnderline()),
        { pressed: state.underline, shortcut: "Mod+U" }
      )}
      {button(
        "strike",
        "Strikethrough",
        run((c) => c.toggleStrike()),
        { pressed: state.strike, shortcut: "Mod+Shift+S" }
      )}
      {button(
        "code",
        "Code",
        run((c) => c.toggleCode()),
        { pressed: state.code, shortcut: "Mod+E" }
      )}
    </span>
  )
  const blocks = (
    <span className="nt-rt-group" role="group" aria-label="Paragraph">
      {button(
        "bulletList",
        "Bulleted list",
        run((c) => c.toggleBulletList()),
        { pressed: state.bulletList, shortcut: "Mod+Shift+8" }
      )}
      {button(
        "orderedList",
        "Numbered list",
        run((c) => c.toggleOrderedList()),
        { pressed: state.orderedList, shortcut: "Mod+Shift+7" }
      )}
      {button(
        "quote",
        "Quote",
        run((c) => c.toggleBlockquote()),
        { pressed: state.blockquote, shortcut: "Mod+Shift+B" }
      )}
      {button(
        "codeBlock",
        "Code block",
        run((c) => c.toggleCodeBlock()),
        { pressed: state.codeBlock, shortcut: "Mod+Alt+C" }
      )}
    </span>
  )

  return (
    <div
      className={cn("nt-rt", className)}
      data-fill={fill || undefined}
      data-readonly={readOnly || undefined}
      style={{ ...style, ["--nt-rt-min" as string]: `${minHeight}px` }}
    >
      {toolbar && !readOnly && (
        <div className="nt-rt-toolbar" role="toolbar" aria-label="Formatting">
          {!mac && (
            <span className="nt-rt-group" role="group" aria-label="History">
              {button(
                "undo",
                "Undo",
                run((c) => c.undo()),
                { shortcut: "Mod+Z", disabled: !state.canUndo }
              )}
              {button(
                "redo",
                "Redo",
                run((c) => c.redo()),
                {
                  shortcut: mac ? "Mod+Shift+Z" : "Mod+Y",
                  disabled: !state.canRedo,
                }
              )}
            </span>
          )}
          <Select
            aria-label="Paragraph style"
            className="nt-rt-style"
            value={state.block}
            options={blockOptions(platform)}
            onChange={(event) => setBlock(event.target.value as Block)}
          />
          {marks}
          {blocks}
          <span className="nt-rt-group" role="group" aria-label="Link">
            {button(
              "link",
              state.link ? "Edit link" : "Link",
              () => void editLink(),
              { pressed: state.link, shortcut: "Mod+K" }
            )}
          </span>
        </div>
      )}
      <div className="nt-rt-scroll" onContextMenu={onContextMenu}>
        <EditorContent editor={editor} />
      </div>
    </div>
  )
}

export { RichTextEditor }
export type { RichTextEditorProps, RichTextValue }
