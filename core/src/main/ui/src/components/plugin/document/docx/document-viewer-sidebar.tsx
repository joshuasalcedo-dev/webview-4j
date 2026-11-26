import * as React from "react"

import { cn } from "../../../../lib/utils"
import { SegmentedControl } from "../../../segmented-control"
import {
  Sidebar,
  SidebarContent,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
} from "../../../sidebar"
import "./docx.css"

/** Under this width (CSS pixels) a viewer's side panel floats over the page instead of pushing it aside. */
const INLINE_MIN_WIDTH = 768

/** An element's width, kept up to date (a ResizeObserver); the last real width while it is hidden. */
export function useElementWidth<T extends HTMLElement>() {
  const ref = React.useRef<T | null>(null)
  const [width, setWidth] = React.useState(0)
  React.useLayoutEffect(() => {
    const element = ref.current
    if (!element) return
    const update = () => {
      const next = element.getBoundingClientRect().width
      // hidden or detached: keep the last width, or the viewer lays out again for nothing
      if (next > 0) setWidth(next)
    }
    update()
    const observer = new ResizeObserver(update)
    observer.observe(element)
    return () => observer.disconnect()
  }, [])
  return [ref, width] as const
}

/** Whether a side panel fits beside the page at this viewer width (else it floats over it). */
export function useInlineThumbnailSidebar(width: number) {
  return width === 0 || width >= INLINE_MIN_WIDTH
}

/** A tab of the side panel (Pages, Outline, Sections). */
export type DocumentViewerSidebarTab = { value: string; label: string; icon?: React.ReactNode }

type DocumentViewerSidebarProps = Omit<React.ComponentProps<"nav">, "title"> & {
  /** What it shows, for screen readers (e.g. "Pages"). */
  "aria-label": string
  open: boolean
  /** Beside the page (true), or floating over it (a narrow viewer). */
  inline?: boolean
  /** Its width, CSS pixels (176 unless set). */
  width?: number
  side?: "left" | "right"
  /** A heading on top, when there are no tabs. */
  title?: React.ReactNode
  tabs?: DocumentViewerSidebarTab[]
  tab?: string
  onTabChange?: (tab: string) => void
}

/**
 * A document viewer's side panel: page thumbnails, the outline, the sections, the comments. The kit's sidebar
 * built from parts (it follows the platform and the theme), opening and closing by its edge; beside the page in a
 * wide viewer, over it in a narrow one. Tabs on top switch what it shows.
 */
export function DocumentViewerSidebar({
  open,
  inline = true,
  width = 176,
  side = "left",
  title,
  tabs,
  tab,
  onTabChange,
  className,
  style,
  children,
  ...props
}: DocumentViewerSidebarProps) {
  return (
    <Sidebar
      composed
      collapsible="offcanvas"
      collapsed={!open}
      side={side}
      data-overlay={!inline || undefined}
      className={cn("nt-docx-sidebar", className)}
      style={{ ["--nt-sb-width" as string]: `${width}px`, ...style }}
      {...props}
    >
      {(tabs?.length || title != null) && (
        <SidebarHeader className="nt-docx-sidebar-header">
          {tabs && tabs.length > 1 ? (
            <SegmentedControl
              aria-label="Show"
              className="nt-docx-sidebar-tabs"
              segments={tabs.map((t) => ({
                value: t.value,
                label: t.icon ? <span className="nt-docx-tab" title={t.label}>{t.icon}<span className="nt-docx-tab-label">{t.label}</span></span> : t.label,
              }))}
              value={tab ?? tabs[0].value}
              onValueChange={(value) => onTabChange?.(value)}
            />
          ) : (
            <div className="nt-docx-sidebar-title">{title ?? tabs?.[0]?.label}</div>
          )}
        </SidebarHeader>
      )}
      <SidebarContent className="nt-docx-sidebar-content">{children}</SidebarContent>
    </Sidebar>
  )
}

/** An entry of a side panel's list: a heading of the outline, a section, a page. */
export type DocumentViewerSidebarEntry = {
  id: string
  label: string
  /** A second, quieter line. */
  description?: string
  /** Indented this many steps (an outline's levels). */
  level?: number
  /** A count, or anything small, at its end. */
  badge?: React.ReactNode
  icon?: React.ReactNode
}

/** A side panel's list of entries, the current one marked; arrow keys move between them. */
export function DocumentViewerSidebarList({
  entries,
  active,
  onSelect,
  empty = "Nothing here",
}: {
  entries: DocumentViewerSidebarEntry[]
  active?: string
  onSelect?: (id: string) => void
  empty?: React.ReactNode
}) {
  if (!entries.length) return <div className="nt-docx-sidebar-empty">{empty}</div>
  return (
    <SidebarMenu className="nt-docx-sidebar-list">
      {entries.map((entry) => (
        <SidebarMenuItem key={entry.id}>
          <SidebarMenuButton
            isActive={entry.id === active}
            icon={entry.icon}
            badge={entry.badge}
            description={entry.description}
            size={entry.description ? "lg" : "default"}
            tooltip={entry.label}
            style={entry.level ? { paddingLeft: 8 + entry.level * 12 } : undefined}
            onClick={() => onSelect?.(entry.id)}
            onKeyDown={(event) => {
              const by = event.key === "ArrowDown" ? 1 : event.key === "ArrowUp" ? -1 : 0
              if (!by) return
              const list = event.currentTarget.closest("ul")
              const buttons = Array.from(list?.querySelectorAll<HTMLButtonElement>(".nt-sb-button") ?? [])
              const at = buttons.indexOf(event.currentTarget)
              const next = buttons[at + by]
              if (next) {
                event.preventDefault()
                next.focus()
              }
            }}
          >
            {entry.label}
          </SidebarMenuButton>
        </SidebarMenuItem>
      ))}
    </SidebarMenu>
  )
}

/** A thumbnail's place while the document is still loading. */
export function DocumentViewerSidebarSkeleton({ className }: { className?: string }) {
  return (
    <div className={cn("nt-docx-skeleton", className)} aria-hidden="true">
      <div className="nt-docx-skeleton-page" />
      <div className="nt-docx-skeleton-line" />
    </div>
  )
}
