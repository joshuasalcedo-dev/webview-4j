import * as React from "react"

import { Avatar } from "../avatar"
import { type MenuEntry } from "../context-menu"
import { InfiniteScroll } from "../infinite-scroll"
import { List, ListItem } from "../list"
import { Pill } from "../pill"
import { SearchField } from "../search-field"
import type { DataRow } from "./data-table"
import { fieldText } from "./field"
import { type CollectionQuery, EMPTY_QUERY, type Paging } from "./query"
import { CollectionState, type EmptyStateProps, LoadingBar, Pages, useLoadedOnce } from "./states"
import "./collection.css"

/**
 * A list of records, as the system's list: each item a title, a quieter line, an avatar and a pill, something after
 * it; a search box; items chosen (with Ctrl/⌘ for several), opened with a double-click or Enter, a right-click menu;
 * pages, or more loaded as the end comes near (`hasMore` and `onLoadMore`).
 *
 * The fields are named, so any record fits: `title="name"`, `subtitle="email"`. With `onQueryChange`, the server
 * answers the page and the search (a {@link CollectionQuery}), and `paging` says where the items given are.
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
  /** A search box: the items whose shown fields have the text. */
  search?: boolean
  /** Items chosen: one, or several with `multiple`. */
  selected?: string[]
  onSelectionChange?: (ids: string[]) => void
  multiple?: boolean
  onOpen?: (id: string, item: DataRow) => void
  itemMenu?: (item: DataRow) => MenuEntry[]
  /** Items per page; 0: all of them (or more as they load). */
  pageSize?: number
  /** The query shown (controlled). */
  query?: Partial<CollectionQuery>
  /** The server answers the page and the search. */
  onQueryChange?: (query: CollectionQuery) => void
  paging?: Paging
  /** More to load as the end comes near (a long list on a server). */
  hasMore?: boolean
  onLoadMore?: () => void | Promise<void>
  loading?: boolean
  error?: { title?: string; message?: string } | null
  onRetry?: () => void
  empty?: React.ReactNode | EmptyStateProps
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
  selected,
  onSelectionChange,
  multiple = false,
  onOpen,
  itemMenu,
  pageSize = 0,
  query,
  onQueryChange,
  paging,
  hasMore,
  onLoadMore,
  loading = false,
  error,
  onRetry,
  empty,
  className,
  style,
  ...props
}: DataListProps) {
  const [own, setOwn] = React.useState<CollectionQuery>({ ...EMPTY_QUERY, size: pageSize })
  const [ownSelected, setOwnSelected] = React.useState<string[]>([])
  const current: CollectionQuery = { ...own, ...query, size: query?.size ?? paging?.size ?? (pageSize || own.size) }
  const change = (next: CollectionQuery) => {
    setOwn(next)
    onQueryChange?.(next)
  }
  const onServer = onQueryChange != null
  const chosen = selected ?? ownSelected
  const field = fieldText
  const id = (item: DataRow, index: number) => String(item[itemKey] ?? index)

  const shown = React.useMemo(() => {
    if (onServer || !current.search.trim()) return items
    const needle = current.search.trim().toLowerCase()
    return items.filter((item) => [title, subtitle, trailing, pill].some((k) => field(item, k)?.toLowerCase().includes(needle)))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [items, current.search, onServer, title, subtitle, trailing, pill])
  const size = current.size
  const all = onServer ? (paging?.total ?? items.length) : shown.length
  const pages = size > 0 ? Math.max(1, Math.ceil(all / size)) : 1
  const at = Math.min(Math.max(1, onServer ? (paging?.page ?? current.page) : current.page), pages)
  const visible = !onServer && size > 0 ? shown.slice((at - 1) * size, at * size) : shown
  const loadedOnce = useLoadedOnce(loading, items.length)
  const filtering = current.search.trim() !== ""

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
          value={current.search}
          onChange={(e) => change({ ...current, search: e.target.value, page: 1 })}
        />
      )}
      <div className="nt-datalist-scroll" style={{ position: "relative" }} data-reloading={loading && loadedOnce ? "" : undefined}>
        {loading && loadedOnce && <LoadingBar />}
        {!(loading && !loadedOnce) &&
          !error &&
          (onLoadMore ? (
            <InfiniteScroll hasMore={hasMore ?? false} onLoadMore={onLoadMore}>
              {list}
            </InfiniteScroll>
          ) : (
            list
          ))}
        <CollectionState
          loading={loading}
          loadedOnce={loadedOnce}
          error={error}
          onRetry={onRetry}
          count={visible.length}
          filtering={filtering}
          onClear={() => change({ ...current, search: "", page: 1 })}
          empty={empty}
          columns={2}
        />
      </div>
      <Pages page={at} pages={pages} total={onServer ? all : undefined} onPage={(n) => change({ ...current, page: n })} />
    </div>
  )
}

export { DataList }
export type { DataListProps }
