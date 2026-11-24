import * as React from "react"

import { ArrowLeft, ChevronLeft, ChevronRight, SidebarIcon } from "../lib/icons"
import { cn } from "../lib/utils"
import { useNative } from "./provider"

type ToolbarProps = Omit<React.ComponentProps<"div">, "title"> & {
  /** What it acts on, for screen readers (e.g. "Chat"). */
  "aria-label": string
  /** The page's title (macOS: bold, left; Windows: a page title). Hidden inside a TitleBar on Windows. */
  title?: React.ReactNode
  /**
   * Going back: a ← button on Windows, ‹ on a Mac; also the mouse's back button, and Alt+← (Windows) /
   * ⌘[ (Mac).
   */
  onBack?: () => void
  canGoBack?: boolean
  /** Going forward: › on a Mac (Windows has no forward button); also the mouse's forward button, Alt+→ / ⌘]. */
  onForward?: () => void
  canGoForward?: boolean
  /** macOS: the sidebar button at the start, which hides and shows the Sidebar. (Windows: the Sidebar's ☰.) */
  onToggleSidebar?: () => void
}

/**
 * A strip of a window's actions: Windows' CommandBar (buttons with icon and label, ← back), macOS' toolbar
 * (icon buttons that show a grey shape on hover, ‹ › to go back and forward). Put it inside a TitleBar for
 * one row with the window's buttons, as both systems now do; or under it. Children: ToolbarButtons,
 * ToolbarSpacer, ToolbarSeparator, SearchField.
 */
function Toolbar({
  title,
  onBack,
  canGoBack = true,
  onForward,
  canGoForward = true,
  onToggleSidebar,
  className,
  children,
  ...props
}: ToolbarProps) {
  const { platform } = useNative()

  // The mouse's back and forward buttons, and the system's keys for them.
  React.useEffect(() => {
    if (!onBack && !onForward) return
    const back = () => canGoBack && onBack?.()
    const forward = () => canGoForward && onForward?.()
    const mouse = (event: MouseEvent) => {
      if (event.button === 3) back()
      if (event.button === 4) forward()
    }
    const keys = (event: KeyboardEvent) => {
      const mac = platform === "mac"
      if (
        mac
          ? event.metaKey && event.key === "["
          : event.altKey && event.key === "ArrowLeft"
      ) {
        event.preventDefault()
        back()
      } else if (
        mac
          ? event.metaKey && event.key === "]"
          : event.altKey && event.key === "ArrowRight"
      ) {
        event.preventDefault()
        forward()
      }
    }
    window.addEventListener("mouseup", mouse)
    window.addEventListener("keydown", keys)
    return () => {
      window.removeEventListener("mouseup", mouse)
      window.removeEventListener("keydown", keys)
    }
  }, [onBack, onForward, canGoBack, canGoForward, platform])

  return (
    <div
      role="toolbar"
      data-webview4j-drag-region
      className={cn("nt-toolbar", className)}
      {...props}
    >
      {onToggleSidebar && (
        <ToolbarButton
          data-nav="sidebar"
          icon={<SidebarIcon />}
          label="Show or hide the sidebar"
          onClick={onToggleSidebar}
        />
      )}
      {onBack && (
        <ToolbarButton
          data-nav="back"
          icon={platform === "mac" ? <ChevronLeft /> : <ArrowLeft />}
          label="Back"
          disabled={!canGoBack}
          onClick={onBack}
        />
      )}
      {onForward && (
        <ToolbarButton
          data-nav="forward"
          icon={<ChevronRight />}
          label="Forward"
          disabled={!canGoForward}
          onClick={onForward}
        />
      )}
      {title != null && <span className="nt-toolbar-title">{title}</span>}
      {children}
    </div>
  )
}

type ToolbarButtonProps = Omit<React.ComponentProps<"button">, "children"> & {
  /** 16px. With one, a Mac shows only the icon (the label is its tooltip and accessible name). */
  icon?: React.ReactNode
  label: string
  /** A toggle (e.g. "Show details"): on is accent-filled on Windows, accent on a Mac. */
  pressed?: boolean
}

function ToolbarButton({
  icon,
  label,
  pressed,
  className,
  ...props
}: ToolbarButtonProps) {
  return (
    <button
      type="button"
      data-icon={icon != null || undefined}
      aria-pressed={pressed}
      title={label}
      className={cn("nt-tool", className)}
      {...props}
    >
      {icon}
      <span className="nt-tool-label">{label}</span>
    </button>
  )
}

/** Pushes what follows to the end. Dragging it moves the window (inside a TitleBar). */
function ToolbarSpacer() {
  return (
    <span
      className="nt-toolbar-spacer"
      data-webview4j-drag-region
      aria-hidden="true"
    />
  )
}

function ToolbarSeparator() {
  return (
    <span
      className="nt-toolbar-separator"
      role="separator"
      aria-orientation="vertical"
    />
  )
}

export { Toolbar, ToolbarButton, ToolbarSeparator, ToolbarSpacer }
export type { ToolbarButtonProps, ToolbarProps }
