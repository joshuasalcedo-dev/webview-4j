import * as React from "react"

import { Avatar } from "../avatar"
import { type MenuEntry, useContextMenu } from "../context-menu"
import { Pill } from "../pill"
import type { DataRow } from "./data-table"
import { fieldText } from "./field"
import { type CollectionQuery, EMPTY_QUERY, type Paging } from "./query"
import { CollectionState, type EmptyStateProps, LoadingBar, Pages, useLoadedOnce } from "./states"
import "./collection.css"

/**
 * Records as cards in a grid that fills the width (a gallery, a dashboard of things): each card a picture (or the
 * initials of its title), a title, a quieter line, a pill, a footnote; chosen with a click (Ctrl/⌘ for several),
 * opened with a double-click or Enter, a right-click menu.
 */
type DataGridProps = {
  "aria-label": string
  items: DataRow[]
  itemKey?: string
  /** The fields shown on each card. */
  title: string
  subtitle?: string
  image?: string
  pill?: string
  footer?: string
  /** The narrowest a card is, CSS pixels (220 by default): as many fit in a row as the width allows. */
  minWidth?: number
  selected?: string[]
  onSelectionChange?: (ids: string[]) => void
  multiple?: boolean
  onOpen?: (id: string, item: DataRow) => void
  itemMenu?: (item: DataRow) => MenuEntry[]
  /** Cards per page; 0: all of them. */
  pageSize?: number
  query?: Partial<CollectionQuery>
  /** The server answers the page. */
  onQueryChange?: (query: CollectionQuery) => void
  paging?: Paging
  loading?: boolean
  error?: { title?: string; message?: string } | null
  onRetry?: () => void
  empty?: React.ReactNode | EmptyStateProps
  className?: string
  style?: React.CSSProperties
}

function DataGrid({
  items,
  itemKey = "id",
  title,
  subtitle,
  image,
  pill,
  footer,
  minWidth = 220,
  selected,
  onSelectionChange,
  multiple = false,
  onOpen,
  itemMenu,
  pageSize = 0,
  query,
  onQueryChange,
  paging,
  loading = false,
  error,
  onRetry,
  empty,
  className,
  style,
  ...props
}: DataGridProps) {
  const [ownSelected, setOwnSelected] = React.useState<string[]>([])
  const chosen = selected ?? ownSelected
  const choose = (id: string, add: boolean) => {
    const next = multiple && add ? (chosen.includes(id) ? chosen.filter((c) => c !== id) : [...chosen, id]) : [id]
    onSelectionChange?.(next)
    if (!selected) setOwnSelected(next)
  }
  const field = fieldText
  const [own, setOwn] = React.useState<CollectionQuery>({ ...EMPTY_QUERY, size: pageSize })
  const current: CollectionQuery = { ...own, ...query, size: query?.size ?? paging?.size ?? (pageSize || own.size) }
  const onServer = onQueryChange != null
  const size = current.size
  const all = onServer ? (paging?.total ?? items.length) : items.length
  const pages = size > 0 ? Math.max(1, Math.ceil(all / size)) : 1
  const at = Math.min(Math.max(1, onServer ? (paging?.page ?? current.page) : current.page), pages)
  const visible = !onServer && size > 0 ? items.slice((at - 1) * size, at * size) : items
  const loadedOnce = useLoadedOnce(loading, items.length)
  const go = (n: number) => {
    const next = { ...current, page: n }
    setOwn(next)
    onQueryChange?.(next)
  }
  return (
    <div style={{ position: "relative", display: "grid", gap: 8, alignContent: "start" }}>
    {loading && loadedOnce && <LoadingBar />}
    <div
      role="listbox"
      aria-label={props["aria-label"]}
      aria-multiselectable={multiple || undefined}
      className={["nt-datagrid", className].filter(Boolean).join(" ")}
      data-reloading={loading && loadedOnce ? "" : undefined}
      style={{ gridTemplateColumns: `repeat(auto-fill, minmax(${minWidth}px, 1fr))`, ...style }}
    >
      {!(loading && !loadedOnce) && !error && visible.map((item, i) => {
        const id = String(item[itemKey] ?? i)
        return (
          <Card
            key={id}
            item={item}
            chosen={chosen.includes(id)}
            onClick={(e) => choose(id, e.ctrlKey || e.metaKey)}
            onOpen={() => onOpen?.(id, item)}
            itemMenu={itemMenu}
          >
            {image && field(item, image) ? (
              <img className="nt-datagrid-image" src={field(item, image)} alt="" />
            ) : (
              <div className="nt-datagrid-image" data-initials="">
                <Avatar name={field(item, title) ?? "?"} size={48} />
              </div>
            )}
            <div className="nt-datagrid-body">
              <div className="nt-datagrid-title">{field(item, title)}</div>
              {subtitle && field(item, subtitle) && <div className="nt-datagrid-subtitle">{field(item, subtitle)}</div>}
              {(pill && field(item, pill)) || (footer && field(item, footer)) ? (
                <div className="nt-datagrid-footer">
                  {pill && field(item, pill) && <Pill size="small">{field(item, pill)}</Pill>}
                  <span>{field(item, footer)}</span>
                </div>
              ) : null}
            </div>
          </Card>
        )
      })}
      <div style={{ gridColumn: "1 / -1" }}>
        <CollectionState loading={loading} loadedOnce={loadedOnce} error={error} onRetry={onRetry} count={visible.length} filtering={false} empty={empty} columns={3} />
      </div>
    </div>
    <Pages page={at} pages={pages} total={onServer ? all : undefined} onPage={go} />
    </div>
  )
}

function Card({
  item,
  chosen,
  onClick,
  onOpen,
  itemMenu,
  children,
}: {
  item: DataRow
  chosen: boolean
  onClick: (e: React.MouseEvent) => void
  onOpen: () => void
  itemMenu?: (item: DataRow) => MenuEntry[]
  children: React.ReactNode
}) {
  const { onContextMenu } = useContextMenu(() => itemMenu?.(item) ?? [])
  return (
    <div
      role="option"
      aria-selected={chosen}
      tabIndex={0}
      className="nt-datagrid-card"
      onClick={onClick}
      onDoubleClick={onOpen}
      onKeyDown={(e) => {
        if (e.key === "Enter") onOpen()
      }}
      onContextMenu={itemMenu ? onContextMenu : undefined}
    >
      {children}
    </div>
  )
}

export { DataGrid }
export type { DataGridProps }
