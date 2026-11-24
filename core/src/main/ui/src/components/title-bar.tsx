import * as React from "react"

import { cn } from "../lib/utils"
import { currentWindow } from "../lib/window"
import { type MenuEntry, useContextMenu } from "./context-menu"
import { useNative } from "./provider"

type TitleBarProps = Omit<React.ComponentProps<"header">, "title"> & {
  title?: React.ReactNode
  /**
   * The app's logo, 16px, before the title: Windows 11 shows it there (a Mac title bar has none, so it is
   * hidden there). An <img> of the app icon or an SVG.
   */
  icon?: React.ReactNode
  onMinimize?: () => void
  onMaximize?: () => void
  /** Back from maximised; without it, onMaximize is called again (a toggle). */
  onRestore?: () => void
  onClose?: () => void
  /** Whether the window fills the screen: Windows' window menu offers Restore then, Maximize otherwise. */
  maximized?: boolean
  /**
   * Whether dragging the bar moves the app's window (true by default). False for a title bar that is not the
   * window's own, such as an MdiWindow's inside the app.
   */
  dragsWindow?: boolean
}

const glyph = (d: string) => (
  <svg viewBox="0 0 10 10" fill="none" aria-hidden="true">
    <path d={d} stroke="currentColor" />
  </svg>
)

/**
 * The window's top edge, for a window without the system's own (WindowOptions.decorations(false)). Windows: the
 * app's icon and the title on the left, minimise / maximise / close on the right, and the window menu on a right-click
 * (Restore, Minimize, Maximize, Close). macOS: the red, yellow and green buttons on the left, the title in the
 * middle, no menu (as on a Mac). Children go in the free space (search, toolbar buttons). Dragging the empty
 * bar moves the window, and a double click maximises it (data-webview4j-drag-region). In a webview-4j window the
 * buttons work by themselves (minimise, maximise or restore, close); give onMinimize, onMaximize, onRestore or
 * onClose to do something else.
 */
function TitleBar({
  title,
  icon,
  onMinimize = currentWindow.minimize,
  onMaximize = currentWindow.toggleMaximize,
  onRestore,
  onClose = currentWindow.close,
  maximized = false,
  dragsWindow = true,
  className,
  children,
  ...props
}: TitleBarProps) {
  const { platform } = useNative()
  const windowMenu: MenuEntry[] = [
    {
      label: "Restore",
      icon: glyph("M2.5 3.5h4v4h-4zM3.5 3.5V2.5h4v4h-1"),
      disabled: !maximized,
      onSelect: onRestore ?? onMaximize,
    },
    { label: "Minimize", icon: glyph("M1.5 5h7"), onSelect: onMinimize },
    {
      label: "Maximize",
      icon: glyph("M1.5 1.5h7v7h-7z"),
      disabled: maximized,
      onSelect: onMaximize,
    },
    "separator",
    {
      label: "Close",
      icon: glyph("m2 2 6 6M8 2 2 8"),
      shortcut: "Alt+F4",
      onSelect: onClose,
    },
  ]
  const { onContextMenu } = useContextMenu(windowMenu)
  return (
    <header
      data-webview4j-drag-region={dragsWindow ? "" : undefined}
      className={cn("nt-titlebar", className)}
      onContextMenu={platform === "windows" ? onContextMenu : undefined}
      {...props}
    >
      <span className="nt-lights">
        <button
          type="button"
          className="nt-light"
          data-action="close"
          aria-label="Close"
          onClick={onClose}
        >
          <svg viewBox="0 0 8 8" fill="none" aria-hidden="true">
            <path
              d="m2 2 4 4M6 2 2 6"
              stroke="currentColor"
              strokeWidth="1.2"
              strokeLinecap="round"
            />
          </svg>
        </button>
        <button
          type="button"
          className="nt-light"
          data-action="minimize"
          aria-label="Minimize"
          onClick={onMinimize}
        >
          <svg viewBox="0 0 8 8" fill="none" aria-hidden="true">
            <path
              d="M1.5 4h5"
              stroke="currentColor"
              strokeWidth="1.2"
              strokeLinecap="round"
            />
          </svg>
        </button>
        <button
          type="button"
          className="nt-light"
          data-action="maximize"
          aria-label="Zoom"
          onClick={onMaximize}
        >
          <svg viewBox="0 0 8 8" fill="currentColor" aria-hidden="true">
            <path d="M2 2h3L2 5zM6 6H3l3-3z" />
          </svg>
        </button>
      </span>
      {icon != null && (
        <span className="nt-titlebar-icon" aria-hidden="true">
          {icon}
        </span>
      )}
      {title != null && <span className="nt-titlebar-title">{title}</span>}
      <span className="nt-titlebar-content">{children}</span>
      <span className="nt-titlebar-controls">
        <button
          type="button"
          className="nt-caption"
          data-action="minimize"
          aria-label="Minimize"
          onClick={onMinimize}
        >
          <svg viewBox="0 0 10 10" fill="none" aria-hidden="true">
            <path d="M0 5h10" stroke="currentColor" />
          </svg>
        </button>
        <button
          type="button"
          className="nt-caption"
          data-action="maximize"
          aria-label="Maximize"
          onClick={onMaximize}
        >
          <svg viewBox="0 0 10 10" fill="none" aria-hidden="true">
            <rect
              x="0.5"
              y="0.5"
              width="9"
              height="9"
              rx="1"
              stroke="currentColor"
            />
          </svg>
        </button>
        <button
          type="button"
          className="nt-caption"
          data-action="close"
          aria-label="Close"
          onClick={onClose}
        >
          <svg viewBox="0 0 10 10" fill="none" aria-hidden="true">
            <path d="m0.5 0.5 9 9M9.5 0.5l-9 9" stroke="currentColor" />
          </svg>
        </button>
      </span>
    </header>
  )
}

export { TitleBar }
export type { TitleBarProps }
