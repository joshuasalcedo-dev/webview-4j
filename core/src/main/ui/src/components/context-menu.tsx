import * as React from "react"
import { createPortal } from "react-dom"

import type { Platform } from "../lib/platform"

/** One choice in a menu. */
type MenuItem = {
  label: React.ReactNode
  onSelect?: () => void
  /** As written for code, e.g. "Mod+C": shown as ⌘C on a Mac and Ctrl+C on Windows. Only shown, not bound. */
  shortcut?: string
  /** A small icon before the label (Windows' window menu has them). */
  icon?: React.ReactNode
  /** A tick before the label; the item is then a checkbox item. */
  checked?: boolean
  disabled?: boolean
}

/** A choice, or a line between groups of them. */
type MenuEntry = MenuItem | "separator"

type OpenMenu = {
  x: number
  y: number
  entries: MenuEntry[]
  /** What was right-clicked: it gets the focus back, and macOS outlines it while the menu is open. */
  target: HTMLElement | null
  fromKeyboard: boolean
}

type MenuApi = {
  open: (event: React.MouseEvent | MouseEvent, entries: MenuEntry[]) => void
}

const MenuContext = React.createContext<MenuApi | null>(null)

// --- shortcuts ------------------------------------------------------------------------------------

const MAC_KEYS: Record<string, string> = {
  Mod: "⌘",
  Cmd: "⌘",
  Ctrl: "⌃",
  Alt: "⌥",
  Option: "⌥",
  Shift: "⇧",
  Enter: "↩",
  Delete: "⌫",
  Backspace: "⌫",
  Escape: "⎋",
  Tab: "⇥",
}
const MAC_ORDER = ["⌃", "⌥", "⇧", "⌘"]

/** "Mod+Shift+Z" as the system writes it: ⇧⌘Z on a Mac, Ctrl+Shift+Z on Windows. */
function formatShortcut(shortcut: string, platform: Platform): string {
  const keys = shortcut.split("+").map((key) => key.trim())
  if (platform === "mac") {
    const symbols = keys.map((key) => MAC_KEYS[key] ?? key.toUpperCase())
    const modifiers = MAC_ORDER.filter((m) => symbols.includes(m))
    return (
      modifiers.join("") +
      symbols.filter((s) => !MAC_ORDER.includes(s)).join("")
    )
  }
  return keys
    .map((key) =>
      key === "Mod" || key === "Cmd" ? "Ctrl" : key === "Option" ? "Alt" : key
    )
    .join("+")
}

// --- the menus every app gets ---------------------------------------------------------------------

type TextControl = HTMLInputElement | HTMLTextAreaElement

const TEXT_INPUTS = new Set([
  "text",
  "search",
  "email",
  "url",
  "tel",
  "password",
  "number",
])

/** A text box or text area the edit menu is for (not a checkbox, a button, ...). */
function asTextControl(element: Element | null): TextControl | null {
  if (element instanceof HTMLTextAreaElement) return element
  if (element instanceof HTMLInputElement && TEXT_INPUTS.has(element.type))
    return element
  return null
}

/** Undo, Cut, Copy, Paste, Select All: what a right-click on a text box shows on both systems. */
function editMenu(field: TextControl, platform: Platform): MenuEntry[] {
  const selected = (field.selectionStart ?? 0) !== (field.selectionEnd ?? 0)
  const editable = !field.readOnly && !field.disabled
  const secret = field instanceof HTMLInputElement && field.type === "password"
  return [
    {
      label: "Undo",
      shortcut: "Mod+Z",
      disabled: !editable,
      onSelect: () => document.execCommand("undo"),
    },
    "separator",
    {
      label: "Cut",
      shortcut: "Mod+X",
      disabled: !editable || !selected || secret,
      onSelect: () => document.execCommand("cut"),
    },
    {
      label: "Copy",
      shortcut: "Mod+C",
      disabled: !selected || secret,
      onSelect: () => document.execCommand("copy"),
    },
    {
      label: "Paste",
      shortcut: "Mod+V",
      disabled: !editable,
      onSelect: () => {
        // execCommand("paste") is not allowed in a webview; reading the clipboard then typing it in is,
        // and keeps Undo working.
        void navigator.clipboard
          ?.readText()
          .then((text) => document.execCommand("insertText", false, text))
      },
    },
    "separator",
    {
      label: platform === "mac" ? "Select All" : "Select all",
      shortcut: "Mod+A",
      onSelect: () => field.select(),
    },
  ]
}

/** Copy, for text selected outside a text box. */
function selectionMenu(text: string): MenuEntry[] {
  return [
    {
      label: "Copy",
      shortcut: "Mod+C",
      onSelect: () => void navigator.clipboard?.writeText(text),
    },
  ]
}

// --- the host: one per NativeProvider -------------------------------------------------------------

/**
 * Opens the menus inside a NativeProvider, and decides what a right-click shows where no component said:
 * the edit menu on a text box, Copy on selected text, and otherwise nothing (never the webview's own menu
 * with Back, Reload and Inspect), as in a native app. browserMenu keeps the webview's menu, for debugging.
 */
function ContextMenuHost({
  platform,
  browserMenu,
  children,
}: {
  platform: Platform
  browserMenu: boolean
  children: React.ReactNode
}) {
  const [menu, setMenu] = React.useState<OpenMenu | null>(null)

  const close = React.useCallback((restoreFocus: boolean) => {
    setMenu((current) => {
      if (current?.target) {
        current.target.removeAttribute("data-context-target")
        if (restoreFocus) current.target.focus({ preventScroll: true })
      }
      return null
    })
  }, [])

  const api = React.useMemo<MenuApi>(
    () => ({
      open(event, entries) {
        event.preventDefault()
        // What was right-clicked: the element the handler is on, or, for a wrapper that is not drawn
        // (display: contents: ContextMenu, the host), the element in it.
        const current =
          event.currentTarget instanceof HTMLElement
            ? event.currentTarget
            : null
        const target = (
          current && current.style.display !== "contents"
            ? current
            : (current?.firstElementChild ?? event.target)
        ) as HTMLElement | null
        const fromKeyboard = event.clientX === 0 && event.clientY === 0
        const box = target?.getBoundingClientRect()
        setMenu((current) => {
          current?.target?.removeAttribute("data-context-target")
          target?.setAttribute("data-context-target", "")
          return {
            x: fromKeyboard && box ? box.left + 8 : event.clientX,
            y: fromKeyboard && box ? box.bottom : event.clientY,
            entries,
            target,
            fromKeyboard,
          }
        })
      },
    }),
    []
  )

  function fallback(event: React.MouseEvent) {
    if (event.defaultPrevented || browserMenu) return
    const field = asTextControl(
      (event.target as Element).closest?.("input, textarea") ?? null
    )
    if (field) {
      api.open(event, editMenu(field, platform))
      return
    }
    const text = window.getSelection()?.toString() ?? ""
    if (text.trim()) {
      api.open(event, selectionMenu(text))
      return
    }
    event.preventDefault()
  }

  return (
    <MenuContext.Provider value={api}>
      <div style={{ display: "contents" }} onContextMenu={fallback}>
        {children}
      </div>
      {menu &&
        inTopDialog(
          <Menu
            key={`${menu.x},${menu.y}`}
            menu={menu}
            platform={platform}
            onClose={close}
            onChoose={(item) => {
              close(true)
              item.onSelect?.()
            }}
          />
        )}
    </MenuContext.Provider>
  )
}

/**
 * While a modal dialog is open, everything outside it is out of reach (the browser makes it inert) and under
 * it (the dialog is in the top layer). So a menu opened then is drawn inside the topmost open dialog; it is
 * placed by the window's coordinates either way.
 */
function inTopDialog(menu: React.ReactNode): React.ReactNode {
  if (typeof document === "undefined") return menu
  const dialogs = Array.from(
    document.querySelectorAll<HTMLDialogElement>("dialog[open]")
  ).filter((d) => d.matches(":modal"))
  const top = dialogs[dialogs.length - 1]
  return top ? createPortal(menu, top) : menu
}

function isItem(entry: MenuEntry): entry is MenuItem {
  return entry !== "separator"
}

function Menu({
  menu,
  platform,
  onClose,
  onChoose,
}: {
  menu: OpenMenu
  platform: Platform
  onClose: (restoreFocus: boolean) => void
  onChoose: (item: MenuItem) => void
}) {
  const ref = React.useRef<HTMLDivElement>(null)
  const enabled = menu.entries.flatMap((entry, index) =>
    isItem(entry) && !entry.disabled ? [index] : []
  )
  const [active, setActive] = React.useState(
    menu.fromKeyboard ? (enabled[0] ?? -1) : -1
  )
  const gutter = menu.entries.some(
    (entry) => isItem(entry) && (entry.checked != null || entry.icon != null)
  )

  // Where it fits: to the right of and below the pointer, or flipped where the window ends. Measured once
  // drawn (hidden), then placed and shown; written to the element, not to state, as nothing else needs it.
  React.useLayoutEffect(() => {
    const element = ref.current
    if (!element) return
    const { width, height } = element.getBoundingClientRect()
    const left =
      menu.x + width > window.innerWidth - 4
        ? Math.max(4, menu.x - width)
        : menu.x
    const top =
      menu.y + height > window.innerHeight - 4
        ? Math.max(4, menu.y - height)
        : menu.y
    element.style.left = `${left}px`
    element.style.top = `${top}px`
    element.style.visibility = "visible"
    element.focus({ preventScroll: true })
  }, [menu])

  // Clicking elsewhere, scrolling, resizing or leaving the window closes it.
  React.useEffect(() => {
    const outside = (event: PointerEvent) => {
      if (!ref.current?.contains(event.target as Node)) onClose(false)
    }
    const away = () => onClose(false)
    document.addEventListener("pointerdown", outside, true)
    window.addEventListener("blur", away)
    window.addEventListener("resize", away)
    document.addEventListener("scroll", away, true)
    return () => {
      document.removeEventListener("pointerdown", outside, true)
      window.removeEventListener("blur", away)
      window.removeEventListener("resize", away)
      document.removeEventListener("scroll", away, true)
    }
  }, [onClose])

  function step(by: number) {
    if (!enabled.length) return
    const at = enabled.indexOf(active)
    const next =
      at === -1
        ? by > 0
          ? 0
          : enabled.length - 1
        : (at + by + enabled.length) % enabled.length
    setActive(enabled[next])
  }

  return (
    <div
      ref={ref}
      role="menu"
      tabIndex={-1}
      className="nt-menu"
      data-gutter={gutter}
      // Keep the page's text selection (Copy needs it) when the menu is clicked.
      onMouseDown={(event) => event.preventDefault()}
      onContextMenu={(event) => event.preventDefault()}
      onKeyDown={(event) => {
        const keys: Record<string, () => void> = {
          ArrowDown: () => step(1),
          ArrowUp: () => step(-1),
          Home: () => setActive(enabled[0] ?? -1),
          End: () => setActive(enabled[enabled.length - 1] ?? -1),
          Escape: () => onClose(true),
          Tab: () => onClose(true),
          Enter: () => {
            const entry = menu.entries[active]
            if (entry && isItem(entry) && !entry.disabled) onChoose(entry)
          },
        }
        keys[" "] = keys.Enter
        const action = keys[event.key]
        if (action) {
          event.preventDefault()
          action()
        }
      }}
    >
      {menu.entries.map((entry, index) =>
        isItem(entry) ? (
          <div
            key={index}
            role={entry.checked != null ? "menuitemcheckbox" : "menuitem"}
            aria-checked={entry.checked}
            aria-disabled={entry.disabled || undefined}
            className="nt-menu-item"
            data-active={index === active || undefined}
            onPointerEnter={() => setActive(entry.disabled ? -1 : index)}
            onPointerLeave={() => setActive(-1)}
            onClick={() => !entry.disabled && onChoose(entry)}
          >
            {gutter && (
              <span className="nt-menu-gutter" aria-hidden="true">
                {entry.checked ? (
                  <svg viewBox="0 0 12 12" fill="none">
                    <path
                      d="M2.5 6.2 5 8.6l4.5-5.2"
                      stroke="currentColor"
                      strokeWidth="1.5"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                    />
                  </svg>
                ) : (
                  entry.icon
                )}
              </span>
            )}
            <span className="nt-menu-label">{entry.label}</span>
            {entry.shortcut ? (
              <span className="nt-menu-shortcut">
                {formatShortcut(entry.shortcut, platform)}
              </span>
            ) : (
              <span />
            )}
          </div>
        ) : (
          <div key={index} role="separator" className="nt-menu-separator" />
        )
      )}
    </div>
  )
}

// --- giving a component its own menu --------------------------------------------------------------

/**
 * The onContextMenu that opens these entries: spread it on what can be right-clicked. Entries may be a
 * function, to build them when the menu opens (e.g. "Pin" or "Unpin"). Outside a NativeProvider it does
 * nothing (the browser's menu shows).
 */
function useContextMenu(entries: MenuEntry[] | (() => MenuEntry[])) {
  const api = React.useContext(MenuContext)
  return {
    onContextMenu: (event: React.MouseEvent) => {
      if (!api) return
      api.open(event, typeof entries === "function" ? entries() : entries)
    },
  }
}

/** Gives what is inside it a right-click menu. */
function ContextMenu({
  entries,
  children,
}: {
  entries: MenuEntry[] | (() => MenuEntry[])
  children: React.ReactNode
}) {
  const { onContextMenu } = useContextMenu(entries)
  return (
    <div style={{ display: "contents" }} onContextMenu={onContextMenu}>
      {children}
    </div>
  )
}

export {
  ContextMenu,
  ContextMenuHost,
  editMenu,
  formatShortcut,
  useContextMenu,
}
export type { MenuEntry, MenuItem }
