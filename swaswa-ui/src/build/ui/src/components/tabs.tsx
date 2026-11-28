import * as React from "react"

import { Close, Plus } from "../lib/icons"
import { cn } from "../lib/utils"
import { type MenuEntry, useContextMenu } from "./context-menu"

type TabItem<T extends string> = {
  value: T
  label: string
  /** 16px, before the label. */
  icon?: React.ReactNode
  /** Shows the close button (Windows: always; macOS: on hover). */
  closable?: boolean
  /** Its right-click menu (Close, Close other tabs, Move to new window, ...). */
  menu?: MenuEntry[] | (() => MenuEntry[])
}

type TabsProps<T extends string> = Omit<
  React.ComponentProps<"div">,
  "onChange"
> & {
  /** What the tabs hold, for screen readers (e.g. "Open documents"). */
  "aria-label": string
  tabs: TabItem<T>[]
  value: T
  onValueChange: (value: T) => void
  /** A tab's close button, or Ctrl+W / ⌘W while it has the focus. */
  onClose?: (value: T) => void
  /** Shows the + button that opens a new tab. */
  onAdd?: () => void
}

/**
 * Documents or pages open side by side: Windows 11's TabView (the chosen tab a raised card joined to the page
 * under it, close buttons, +), macOS' window tabs (equal tabs across a bar, the close button on hover, +).
 * Arrow keys move between tabs. Give the page under it role="tabpanel"; the content is yours.
 */
function Tabs<T extends string>({
  tabs,
  value,
  onValueChange,
  onClose,
  onAdd,
  className,
  ...props
}: TabsProps<T>) {
  const refs = React.useRef<(HTMLButtonElement | null)[]>([])

  function move(from: number, by: number) {
    const next = (from + by + tabs.length) % tabs.length
    onValueChange(tabs[next].value)
    refs.current[next]?.focus()
  }

  return (
    <div className={cn("nt-tabs", className)} data-webview4j-drag-region>
      <div role="tablist" style={{ display: "contents" }} {...props}>
        {tabs.map((tab, index) => (
          <Tab
            key={tab.value}
            tab={tab}
            selected={tab.value === value}
            buttonRef={(element) => {
              refs.current[index] = element
            }}
            onSelect={() => onValueChange(tab.value)}
            onClose={
              onClose && tab.closable ? () => onClose(tab.value) : undefined
            }
            onKeyDown={(event) => {
              if (event.key === "ArrowRight") {
                event.preventDefault()
                move(index, 1)
              } else if (event.key === "ArrowLeft") {
                event.preventDefault()
                move(index, -1)
              } else if (
                (event.ctrlKey || event.metaKey) &&
                event.key.toLowerCase() === "w" &&
                onClose &&
                tab.closable
              ) {
                event.preventDefault()
                onClose(tab.value)
              }
            }}
          />
        ))}
      </div>
      {onAdd && (
        <button
          type="button"
          className="nt-tabs-add"
          aria-label="New tab"
          title="New tab"
          onClick={onAdd}
        >
          <Plus />
        </button>
      )}
    </div>
  )
}

function Tab<T extends string>({
  tab,
  selected,
  buttonRef,
  onSelect,
  onClose,
  onKeyDown,
}: {
  tab: TabItem<T>
  selected: boolean
  buttonRef: (element: HTMLButtonElement | null) => void
  onSelect: () => void
  onClose?: () => void
  onKeyDown: (event: React.KeyboardEvent) => void
}) {
  const { onContextMenu } = useContextMenu(tab.menu ?? [])
  return (
    <div
      className="nt-tab"
      data-selected={selected || undefined}
      onContextMenu={tab.menu ? onContextMenu : undefined}
      // The middle button closes a tab, on both systems.
      onAuxClick={(event) => {
        if (event.button === 1 && onClose) onClose()
      }}
    >
      <button
        ref={buttonRef}
        type="button"
        role="tab"
        aria-selected={selected}
        tabIndex={selected ? 0 : -1}
        className="nt-tab-select"
        title={tab.label}
        onClick={onSelect}
        onKeyDown={onKeyDown}
      >
        {tab.icon}
        <span className="nt-tab-label">{tab.label}</span>
      </button>
      {onClose && (
        <button
          type="button"
          className="nt-tab-close"
          tabIndex={-1}
          aria-label={`Close ${tab.label}`}
          title="Close tab"
          onClick={onClose}
        >
          <Close />
        </button>
      )}
    </div>
  )
}

export { Tabs }
export type { TabItem, TabsProps }
