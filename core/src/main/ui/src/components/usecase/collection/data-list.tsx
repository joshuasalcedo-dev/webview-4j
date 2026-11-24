import * as React from "react"

import { Avatar } from "../../avatar"
import { Button } from "../../button"
import { type MenuEntry } from "../../context-menu"
import { InfiniteScroll, Spinner } from "../../infinite-scroll"
import { List, ListItem } from "../../list"
import { Pill } from "../../pill"
import { SearchField } from "../../search-field"
import type { DataRow } from "./data-table"
import "./collection.css"

/**
 * A list of records, as the system's list: each item a title, a quieter line, an avatar and a pill, something after
 * it; a search box; items chosen (with Ctrl/⌘ for several), opened with a double-click or Enter, a right-click menu;
 * pages, or more loaded as the end comes near (`hasMore` and `onLoadMore`).
 *
 * The fields are named, so any record fits: `title="name"`, `subtitle="email"`.
 */
type DataListProps = {
  "aria-label": string
  items: DataRow[]
  /** The field that tells items apart ("id" by default). */
  itemKey?: string
  /** The fields shown: the title (required), a second line, an avatar from a name, a pill, something after. */
  title: string
  subtitle?: string
  avatar?: string
  pill?: string
  trailing?: string
  /** A search box: the items whose shown fields have the text. With `onFilterChange`, searching is the server's. */
  search?: boolean
  filter?: string
  onFilterChange?: (text: string) => void
  /** Items chosen: one, or several with `multiple`. */
  selected?: string[]
  onSelectionChange?: (ids: string[]) => void
  multiple?: boolean
  onOpen?: (id: string, item: DataRow) => void
  itemMenu?: (item: DataRow) => MenuEntry[]
  /** Items per page; 0: all of them (or more as they load). */
  pageSize?: number
  /** More to load as the end comes near (a long list on a server). */
  hasMore?: boolean
  onLoadMore?: () => void | Promise<void>
  loading?: boolean
  empty?: React.ReactNode
  className?: string
  style?: React.CSSProperties
}

function DataList({
  items,
  itemKey = "id",
  title,
  subtitle,
  avatar,
  pill,
  trailing,
  search = false,
  filter,
  onFilterChange,
  selected,
  onSelectionChange,
  multiple = false,
  onOpen,
  itemMenu,
  pageSize = 0,
  hasMore,
  onLoadMore,
  loading = false,
  empty = "Nothing here.",
  className,
  style,
  ...props
}: DataListProps) {
  const [ownFilter, setOwnFilter] = React.useState("")
  const [ownSelected, setOwnSelected] = React.useState<string[]>([])
  const [page, setPage] = React.useState(1)
  const text = filter ?? ownFilter
  const chosen = selected ?? ownSelected
  const field = (item: DataRow, key?: string) => (key && item[key] != null ? String(item[key]) : undefined)
  const id = (item: DataRow, index: number) => String(item[itemKey] ?? index)

  const shown = React.useMemo(() => {
    if (onFilterChange || !text.trim()) return items
    const needle = text.trim().toLowerCase()
    return items.filter((item) => [title, subtitle, trailing, pill].some((k) => field(item, k)?.toLowerCase().includes(needle)))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [items, text, onFilterChange, title, subtitle, trailing, pill])
  const pages = pageSize > 0 ? Math.max(1, Math.ceil(shown.length / pageSize)) : 1
  const visible = pageSize > 0 ? shown.slice((page - 1) * pageSize, page * pageSize) : shown

  const choose = (itemId: string, add: boolean) => {
    const next = multiple && add ? (chosen.includes(itemId) ? chosen.filter((c) => c !== itemId) : [...chosen, itemId]) : [itemId]
    onSelectionChange?.(next)
    if (!selected) setOwnSelected(next)
  }

  const list = (
    <List aria-label={props["aria-label"]}>
      {visible.map((item, i) => {
        const itemId = id(item, i)
        return (
          <ListItem
            key={itemId}
            title={field(item, title) ?? ""}
            subtitle={
              pill && field(item, pill) ? (
                <span className="nt-datalist-line">
                  <Pill size="small">{field(item, pill)}</Pill>
                  {field(item, subtitle)}
                </span>
              ) : (
                field(item, subtitle)
              )
            }
            leading={avatar && field(item, avatar) ? <Avatar name={field(item, avatar)!} /> : undefined}
            trailing={field(item, trailing)}
            selected={chosen.includes(itemId)}
            onSelect={() => choose(itemId, false)}
            onDoubleClick={() => onOpen?.(itemId, item)}
            onKeyDown={(e: React.KeyboardEvent) => {
              if (e.key === "Enter") onOpen?.(itemId, item)
            }}
            onClickCapture={(e: React.MouseEvent) => {
              if (multiple && (e.ctrlKey || e.metaKey)) {
                e.stopPropagation()
                choose(itemId, true)
              }
            }}
            menu={itemMenu ? () => itemMenu(item) : undefined}
          />
        )
      })}
    </List>
  )

  return (
    <div className={["nt-datalist", className].filter(Boolean).join(" ")} style={style}>
      {search && (
        <SearchField
          aria-label={`Search ${props["aria-label"]}`}
          value={text}
          onChange={(e) => {
            onFilterChange?.(e.target.value)
            if (filter === undefined) setOwnFilter(e.target.value)
            setPage(1)
          }}
        />
      )}
      <div className="nt-datalist-scroll">
        {onLoadMore ? (
          <InfiniteScroll hasMore={hasMore ?? false} onLoadMore={onLoadMore}>
            {list}
          </InfiniteScroll>
        ) : (
          list
        )}
        {loading && (
          <div className="nt-datatable-note">
            <Spinner label="Loading" />
          </div>
        )}
        {!loading && visible.length === 0 && <div className="nt-datatable-note">{empty}</div>}
      </div>
      {pages > 1 && (
        <div className="nt-datatable-pages">
          <Button variant="subtle" disabled={page <= 1} onClick={() => setPage(page - 1)}>
            Previous
          </Button>
          <span className="nt-datatable-count">
            Page {page} of {pages}
          </span>
          <Button variant="subtle" disabled={page >= pages} onClick={() => setPage(page + 1)}>
            Next
          </Button>
        </div>
      )}
    </div>
  )
}

export { DataList }
export type { DataListProps }
