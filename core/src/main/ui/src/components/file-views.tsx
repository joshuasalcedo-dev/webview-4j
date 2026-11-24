import * as React from "react"

import { formatFileSize } from "../lib/file-icons"
import {
  type FileNode,
  fileType,
  findFiles,
  formatModified,
  isFolder,
  joinPath,
  nodeAt,
  sortFiles,
} from "../lib/files"
import { cn } from "../lib/utils"
import { Breadcrumb } from "./breadcrumb"
import { useContextMenu } from "./context-menu"
import {
  type FileActions,
  folderSpaceMenu,
  Highlight,
  selectionKey,
  selectionMenu,
} from "./file-menu"
import { type Selected, useFileSelection } from "./file-selection"
import { FileIcon, FileTree } from "./files"
import { useNative } from "./provider"
import { SearchField } from "./search-field"
import { SegmentedControl } from "./segmented-control"
import { StatusBar, StatusBarSpacer, StatusItem } from "./status-bar"
import { Toolbar, ToolbarButton } from "./toolbar"

/** One file or folder shown in a view, with its path. */
type FileEntry = { path: string; node: FileNode; parent: string }

type FileViewProps = Omit<React.ComponentProps<"div">, "onSelect"> &
  FileActions & {
    /** What the view shows, for screen readers (e.g. "Files in src"). */
    "aria-label": string
    /** A folder's contents. */
    items: FileNode[]
    /** That folder's path (paths of the items start with it); "" for the top. */
    folder?: string
    /** The chosen paths (one, or a list). Left out, the view keeps its own choice. */
    selected?: Selected
    /** The choice changed: every chosen path, in the order chosen. */
    onSelectionChange?: (paths: string[]) => void
    /** Ctrl/⌘+click, Shift+click, Shift+arrows and Ctrl+A choose several. True by default. */
    multiple?: boolean
    /** The item the focus went to (a click or the arrow keys). */
    onSelect?: (path: string, node: FileNode) => void
    /** A folder opened (double-click, Enter): the view does not go in by itself; FileExplorer does. */
    onOpenFolder?: (path: string, node: FileNode) => void
    /** What Find typed: everything under the folder that matches, at any depth, with the match marked. */
    find?: string
  }

/** The entries a view shows: the folder's contents, or with Find what matches under it. */
function entriesOf(
  items: FileNode[],
  folder: string,
  find?: string
): FileEntry[] {
  if (find?.trim()) {
    return findFiles(items, find).map((f) => ({
      ...f,
      path: joinPath(folder || undefined, f.path),
      parent: joinPath(folder || undefined, f.parent),
    }))
  }
  return sortFiles(items).map((node) => ({
    node,
    path: joinPath(folder || undefined, node.name),
    parent: folder,
  }))
}

/**
 * What every view does with its entries, in the order it shows them: choosing one or several, opening, the
 * keys, the menus (the chosen items' menu on a right-click, the empty space's elsewhere).
 */
function useFileView(props: FileViewProps, shown: FileEntry[]) {
  const { platform } = useNative()
  const { folder = "", onSelect, onOpen, onOpenFolder } = props
  const refs = React.useRef(new Map<string, HTMLElement>())
  const byPath = React.useMemo(
    () => new Map(shown.map((e) => [e.path, e])),
    [shown]
  )
  const choice = useFileSelection({
    order: shown.map((e) => e.path),
    selected: props.selected,
    multiple: props.multiple,
    onSelectionChange: props.onSelectionChange,
    platform,
  })
  const [focused, setFocused] = React.useState<string | undefined>(
    choice.selection[choice.selection.length - 1]
  )

  const actions: FileActions = {
    onOpen: (path, node) =>
      isFolder(node) ? onOpenFolder?.(path, node) : onOpen?.(path, node),
    onRename: props.onRename,
    onDelete: props.onDelete,
    onDeleteMany: props.onDeleteMany,
    onReveal: props.onReveal,
    onNewFile: props.onNewFile,
    onNewFolder: props.onNewFolder,
    onDownload: props.onDownload,
    onProperties: props.onProperties,
    menu: props.menu,
  }

  const chosen = (paths: string[]) =>
    paths
      .map((p) => byPath.get(p))
      .filter((e): e is FileEntry => !!e)
      .map(({ path, node }) => ({ path, node }))

  const focus = (entry: FileEntry) => {
    setFocused(entry.path)
    refs.current.get(entry.path)?.focus()
  }

  const onItemClick = (entry: FileEntry, event: React.MouseEvent) => {
    event.stopPropagation()
    setFocused(entry.path)
    choice.click(entry.path, event)
    onSelect?.(entry.path, entry.node)
  }

  /** Arrow keys, Home/End, Enter, Ctrl+A, Ctrl+Space, and the files' keys; `across` is how many items a row has. */
  const onKeyDown = (
    event: React.KeyboardEvent,
    index: number,
    across: number
  ) => {
    const entry = shown[index]
    const acting = choice.has(entry.path)
      ? chosen(choice.selection)
      : [{ path: entry.path, node: entry.node }]
    if (selectionKey(event, acting, actions, platform)) return
    if (choice.isSelectAll(event)) {
      event.preventDefault()
      choice.selectAll()
      return
    }
    if (event.key === " " && event.ctrlKey && platform === "windows") {
      event.preventDefault()
      choice.toggle(entry.path)
      return
    }
    const moves: Record<string, number> = {
      ArrowDown: index + across,
      ArrowUp: index - across,
      ...(across > 1 ? { ArrowRight: index + 1, ArrowLeft: index - 1 } : {}),
      Home: 0,
      End: shown.length - 1,
    }
    if (event.key in moves) {
      event.preventDefault()
      const target =
        shown[Math.max(0, Math.min(shown.length - 1, moves[event.key]))]
      if (!target) return
      focus(target)
      choice.moved(target.path, event)
      onSelect?.(target.path, target.node)
    } else if (event.key === "Enter") {
      event.preventDefault()
      for (const item of acting) actions.onOpen?.(item.path, item.node)
    }
  }

  // The empty space: a click clears the choice; a right-click shows New folder, New file.
  const space = useContextMenu(() => folderSpaceMenu(folder, actions, platform))
  const onSpaceMenu = (event: React.MouseEvent) => {
    if (
      !event.defaultPrevented &&
      folderSpaceMenu(folder, actions, platform).length > 0
    )
      space.onContextMenu(event)
  }
  const onSpaceClick = () => choice.clear()

  /** An item's right-click: the menu for everything chosen with it (it alone when it was not chosen). */
  const itemMenu = (entry: FileEntry) => () =>
    selectionMenu(chosen(choice.forMenu(entry.path)), actions, platform)

  const tabStop = focused && byPath.has(focused) ? focused : shown[0]?.path
  return {
    platform,
    refs,
    actions,
    choice,
    onItemClick,
    onKeyDown,
    onSpaceMenu,
    onSpaceClick,
    itemMenu,
    tabStop,
  }
}

function Empty({ find }: { find?: string }) {
  return (
    <div className="nt-files-empty">
      {find?.trim() ? `Nothing matches “${find}”.` : "This folder is empty."}
    </div>
  )
}

// --- FileGrid ---------------------------------------------------------------------------------------

/**
 * A folder's contents as large icons, as Explorer's and Finder's icon view: the name under each, up to two
 * lines. Click, Ctrl/⌘+click and Shift+click choose; arrow keys move in all four directions (Shift grows the
 * choice); Enter or a double-click opens; every item has its menu.
 */
function FileGrid(props: FileViewProps) {
  const { items, folder = "", find, className } = props
  const entries = React.useMemo(
    () => entriesOf(items, folder, find),
    [items, folder, find]
  )
  const view = useFileView(props, entries)
  const grid = React.useRef<HTMLDivElement>(null)

  // How many icons a row holds now (for ↑ and ↓).
  const across = () => {
    const cells = Array.from(grid.current?.children ?? []) as HTMLElement[]
    const top = cells[0]?.offsetTop
    return Math.max(1, cells.filter((cell) => cell.offsetTop === top).length)
  }

  return (
    <div
      ref={grid}
      role="listbox"
      aria-label={props["aria-label"]}
      aria-multiselectable={props.multiple !== false}
      aria-orientation="horizontal"
      className={cn("nt-filegrid", className)}
      style={props.style}
      onClick={view.onSpaceClick}
      onContextMenu={view.onSpaceMenu}
    >
      {entries.length === 0 && <Empty find={find} />}
      {entries.map((entry, index) => (
        <GridItem
          key={entry.path}
          entry={entry}
          find={find}
          selected={view.choice.has(entry.path)}
          tabIndex={entry.path === view.tabStop ? 0 : -1}
          itemRef={(el) =>
            el
              ? view.refs.current.set(entry.path, el)
              : view.refs.current.delete(entry.path)
          }
          menu={view.itemMenu(entry)}
          onClick={(event) => view.onItemClick(entry, event)}
          onDoubleClick={() => view.actions.onOpen?.(entry.path, entry.node)}
          onKeyDown={(event) => view.onKeyDown(event, index, across())}
        />
      ))}
    </div>
  )
}

function GridItem({
  entry,
  find,
  selected,
  tabIndex,
  itemRef,
  menu,
  onClick,
  onDoubleClick,
  onKeyDown,
}: {
  entry: FileEntry
  find?: string
  selected: boolean
  tabIndex: number
  itemRef: (element: HTMLElement | null) => void
  menu: () => ReturnType<typeof selectionMenu>
  onClick: (event: React.MouseEvent) => void
  onDoubleClick: () => void
  onKeyDown: (event: React.KeyboardEvent) => void
}) {
  const { onContextMenu } = useContextMenu(menu)
  return (
    <div
      ref={itemRef}
      role="option"
      aria-selected={selected}
      tabIndex={tabIndex}
      title={entry.path}
      className="nt-filegrid-item"
      onClick={onClick}
      onDoubleClick={onDoubleClick}
      onKeyDown={onKeyDown}
      onContextMenu={onContextMenu}
    >
      <FileIcon
        name={entry.node.name}
        folder={isFolder(entry.node)}
        size={48}
      />
      <span className="nt-filegrid-name">
        <Highlight text={entry.node.name} find={find} />
      </span>
    </div>
  )
}

// --- FileList ---------------------------------------------------------------------------------------

type Column = "name" | "modified" | "type" | "size"

/**
 * A folder's contents as rows with columns, as Explorer's Details and Finder's List view: Name, Date modified,
 * Type (Kind), Size; a column's heading sorts by it (again: the other way). With Find, a Folder column says
 * where each match is. Click, Ctrl/⌘+click, Shift+click and Shift+arrows choose; Enter opens; every row's menu.
 */
function FileList(props: FileViewProps) {
  const { platform } = useNative()
  const { items, folder = "", find, className } = props
  const [sort, setSort] = React.useState<{ by: Column; down: boolean }>({
    by: "name",
    down: false,
  })
  const mac = platform === "mac"
  const finding = !!find?.trim()

  const rows = React.useMemo(() => {
    const value = (e: FileEntry): string | number => {
      if (sort.by === "size") return e.node.size ?? -1
      if (sort.by === "modified")
        return e.node.modified ? new Date(e.node.modified).getTime() : 0
      if (sort.by === "type") return fileType(e.node, platform)
      return e.node.name
    }
    return entriesOf(items, folder, find).sort((a, b) => {
      // Folders stay first, as both systems keep them.
      const folders = Number(isFolder(b.node)) - Number(isFolder(a.node))
      if (folders) return folders
      const x = value(a)
      const y = value(b)
      const order =
        typeof x === "number" && typeof y === "number"
          ? x - y
          : String(x).localeCompare(String(y), undefined, {
              numeric: true,
              sensitivity: "base",
            })
      return sort.down ? -order : order
    })
  }, [items, folder, find, sort, platform])

  const view = useFileView(props, rows)

  const columns: { key: Column | "folder"; label: string }[] = [
    { key: "name", label: "Name" },
    ...(finding
      ? [{ key: "folder" as const, label: mac ? "Where" : "Folder" }]
      : []),
    { key: "modified", label: mac ? "Date Modified" : "Date modified" },
    { key: "type", label: mac ? "Kind" : "Type" },
    { key: "size", label: "Size" },
  ]

  return (
    <div
      className={cn("nt-filelist", className)}
      style={props.style}
      onClick={view.onSpaceClick}
      onContextMenu={view.onSpaceMenu}
    >
      <table
        role="grid"
        aria-label={props["aria-label"]}
        aria-multiselectable={props.multiple !== false}
        aria-rowcount={rows.length + 1}
      >
        <thead>
          <tr>
            {columns.map((column) => {
              const sorted = column.key === sort.by
              return (
                <th
                  key={column.key}
                  data-column={column.key}
                  aria-sort={
                    sorted
                      ? sort.down
                        ? "descending"
                        : "ascending"
                      : undefined
                  }
                >
                  {column.key === "folder" ? (
                    column.label
                  ) : (
                    <button
                      type="button"
                      tabIndex={-1}
                      onClick={(event) => {
                        event.stopPropagation()
                        setSort((now) => ({
                          by: column.key as Column,
                          down: now.by === column.key ? !now.down : false,
                        }))
                      }}
                    >
                      {column.label}
                      {sorted && (
                        <svg
                          viewBox="0 0 16 16"
                          aria-hidden="true"
                          data-down={sort.down || undefined}
                        >
                          <path
                            d="m4 10 4-4 4 4"
                            fill="none"
                            stroke="currentColor"
                            strokeWidth="1.4"
                            strokeLinecap="round"
                            strokeLinejoin="round"
                          />
                        </svg>
                      )}
                    </button>
                  )}
                </th>
              )
            })}
          </tr>
        </thead>
        <tbody>
          {rows.map((entry, index) => (
            <ListRow
              key={entry.path}
              entry={entry}
              find={find}
              finding={finding}
              platform={platform}
              selected={view.choice.has(entry.path)}
              tabIndex={entry.path === view.tabStop ? 0 : -1}
              rowRef={(el) =>
                el
                  ? view.refs.current.set(entry.path, el)
                  : view.refs.current.delete(entry.path)
              }
              menu={view.itemMenu(entry)}
              onClick={(event) => view.onItemClick(entry, event)}
              onDoubleClick={() =>
                view.actions.onOpen?.(entry.path, entry.node)
              }
              onKeyDown={(event) => view.onKeyDown(event, index, 1)}
            />
          ))}
        </tbody>
      </table>
      {rows.length === 0 && <Empty find={find} />}
    </div>
  )
}

function ListRow({
  entry,
  find,
  finding,
  platform,
  selected,
  tabIndex,
  rowRef,
  menu,
  onClick,
  onDoubleClick,
  onKeyDown,
}: {
  entry: FileEntry
  find?: string
  finding: boolean
  platform: "windows" | "mac"
  selected: boolean
  tabIndex: number
  rowRef: (element: HTMLElement | null) => void
  menu: () => ReturnType<typeof selectionMenu>
  onClick: (event: React.MouseEvent) => void
  onDoubleClick: () => void
  onKeyDown: (event: React.KeyboardEvent) => void
}) {
  const { onContextMenu } = useContextMenu(menu)
  const folder = isFolder(entry.node)
  return (
    <tr
      ref={rowRef}
      aria-selected={selected}
      tabIndex={tabIndex}
      title={entry.path}
      onClick={onClick}
      onDoubleClick={onDoubleClick}
      onKeyDown={onKeyDown}
      onContextMenu={onContextMenu}
    >
      <td data-column="name">
        <span className="nt-filelist-name">
          <FileIcon name={entry.node.name} folder={folder} />
          <span>
            <Highlight text={entry.node.name} find={find} />
          </span>
        </span>
      </td>
      {finding && <td data-column="folder">{entry.parent || "/"}</td>}
      <td data-column="modified">
        {formatModified(entry.node.modified, platform)}
      </td>
      <td data-column="type">{fileType(entry.node, platform)}</td>
      <td data-column="size">
        {folder
          ? platform === "mac"
            ? "--"
            : ""
          : entry.node.size != null
            ? formatFileSize(entry.node.size, platform)
            : ""}
      </td>
    </tr>
  )
}

// --- FileExplorer -----------------------------------------------------------------------------------

type FileView = "grid" | "list" | "tree"

type FileExplorerProps = Omit<React.ComponentProps<"div">, "onSelect"> &
  FileActions & {
    /** Everything, from the top. */
    items: FileNode[]
    /** What the top is called in the path bar and the side pane (e.g. the project's or the drive's name). */
    rootName?: string
    /** The folder shown first ("" for the top). */
    defaultFolder?: string
    /** How the contents show first: "list" (Windows' Details, Finder's List) by default. */
    defaultView?: FileView
    /** The chosen items changed (one or several). */
    onSelectionChange?: (paths: string[]) => void
  }

const ArrowUpIcon = () => (
  <svg
    viewBox="0 0 16 16"
    fill="none"
    stroke="currentColor"
    strokeWidth="1.3"
    strokeLinecap="round"
    strokeLinejoin="round"
    aria-hidden="true"
  >
    <path d="M8 14V2.5M3 7l5-5 5 5" />
  </svg>
)

/**
 * A file browser, as Explorer and Finder: folders down the side, the folder's contents as a grid, a list with
 * columns, or a tree; back, forward and up (also the mouse's side buttons, Alt+← → ↑ on Windows, ⌘[ ] ↑ on a
 * Mac); where you are as a path you can click; Find, which looks through everything under the folder; how
 * many items there are and how many are chosen along the bottom. Several items can be chosen (Ctrl/⌘+click,
 * Shift+click, Ctrl+A); every file and folder has its right-click menu, from the actions given.
 */
function FileExplorer({
  items,
  rootName = "Files",
  defaultFolder = "",
  defaultView = "list",
  onSelectionChange,
  className,
  ...rest
}: FileExplorerProps) {
  const { platform } = useNative()
  const {
    onOpen,
    onRename,
    onDelete,
    onDeleteMany,
    onReveal,
    onNewFile,
    onNewFolder,
    onDownload,
    onProperties,
    menu,
    ...props
  } = rest
  const actions: FileActions = {
    onOpen,
    onRename,
    onDelete,
    onDeleteMany,
    onReveal,
    onNewFile,
    onNewFolder,
    onDownload,
    onProperties,
    menu,
  }

  const [history, setHistory] = React.useState({ list: [defaultFolder], at: 0 })
  const folder = history.list[history.at]
  const [view, setView] = React.useState<FileView>(defaultView)
  const [find, setFind] = React.useState("")
  const [selected, setSelected] = React.useState<string[]>([])

  const choose = (paths: string[]) => {
    setSelected(paths)
    onSelectionChange?.(paths)
  }
  const go = (path: string) => {
    if (path === folder) return
    setHistory((h) => ({
      list: [...h.list.slice(0, h.at + 1), path],
      at: h.at + 1,
    }))
    setFind("")
    choose([])
  }
  const step = (by: number) => {
    setHistory((h) => ({
      ...h,
      at: Math.max(0, Math.min(h.list.length - 1, h.at + by)),
    }))
    setFind("")
    choose([])
  }
  const parent = folder.includes("/")
    ? folder.slice(0, folder.lastIndexOf("/"))
    : ""
  const up = () => folder !== "" && go(parent)

  const contents = nodeAt(items, folder)?.children ?? []
  const shown = find.trim() ? findFiles(items, find, folder) : contents
  const chosenNodes = selected
    .map((p) => nodeAt(items, p))
    .filter((n): n is FileNode => !!n)
  const chosenSize = chosenNodes.reduce(
    (sum, n) => sum + (isFolder(n) ? 0 : (n.size ?? 0)),
    0
  )
  const folderName =
    folder === "" ? rootName : folder.slice(folder.lastIndexOf("/") + 1)

  const crumbs = [
    { label: rootName, onSelect: () => go("") },
    ...(folder === ""
      ? []
      : folder.split("/").map((name, i, parts) => ({
          label: name,
          onSelect: () => go(parts.slice(0, i + 1).join("/")),
        }))),
  ]

  const viewProps: FileViewProps = {
    "aria-label": `Files in ${folderName}`,
    items: contents,
    folder,
    find,
    selected,
    onSelectionChange: choose,
    onOpenFolder: (path) => go(path),
    ...actions,
  }

  // The side pane's paths start with the top's name; the actions get them without it.
  const inPane = (path: string) =>
    path === rootName ? "" : path.slice(rootName.length + 1)

  return (
    <div
      className={cn("nt-explorer", className)}
      onKeyDown={(event) => {
        // Up a folder: Alt+↑ (Windows), ⌘↑ (Mac).
        if (
          event.key === "ArrowUp" &&
          (platform === "mac" ? event.metaKey : event.altKey)
        ) {
          event.preventDefault()
          up()
        }
      }}
      {...props}
    >
      <Toolbar
        aria-label="Folder"
        className="nt-explorer-toolbar"
        onBack={() => step(-1)}
        canGoBack={history.at > 0}
        onForward={() => step(1)}
        canGoForward={history.at < history.list.length - 1}
      >
        <ToolbarButton
          icon={<ArrowUpIcon />}
          label="Up"
          disabled={folder === ""}
          onClick={up}
        />
        <Breadcrumb
          className="nt-explorer-path"
          aria-label="Where you are"
          items={crumbs}
        />
        <SegmentedControl
          aria-label="View"
          value={view}
          onValueChange={setView}
          segments={[
            { value: "grid", label: platform === "mac" ? "Icons" : "Grid" },
            { value: "list", label: platform === "mac" ? "List" : "Details" },
            { value: "tree", label: "Tree" },
          ]}
        />
        <SearchField
          aria-label={`Search ${folderName}`}
          placeholder={`Search ${folderName}`}
          value={find}
          onChange={(event) => setFind(event.target.value)}
          className="nt-explorer-find"
        />
      </Toolbar>
      <div className="nt-explorer-body">
        <FileTree
          aria-label="Folders"
          className="nt-explorer-pane"
          items={[{ name: rootName, children: items }]}
          foldersOnly
          multiple={false}
          selected={folder === "" ? rootName : `${rootName}/${folder}`}
          reveal={folder === "" ? rootName : `${rootName}/${folder}/`}
          defaultExpanded={[rootName]}
          onSelect={(path) => go(inPane(path))}
          onRename={onRename && ((path, node) => onRename(inPane(path), node))}
          onDelete={onDelete && ((path, node) => onDelete(inPane(path), node))}
          onReveal={onReveal && ((path, node) => onReveal(inPane(path), node))}
          onNewFile={onNewFile && ((path) => onNewFile(inPane(path)))}
          onNewFolder={onNewFolder && ((path) => onNewFolder(inPane(path)))}
          onProperties={
            onProperties && ((path, node) => onProperties(inPane(path), node))
          }
        />
        <div className="nt-explorer-view">
          {view === "grid" && <FileGrid {...viewProps} />}
          {view === "list" && <FileList {...viewProps} />}
          {view === "tree" && (
            <FileTree
              aria-label={viewProps["aria-label"]}
              items={contents}
              root={folder}
              find={find}
              selected={selected}
              onSelectionChange={choose}
              {...actions}
            />
          )}
        </div>
      </div>
      <StatusBar>
        <StatusItem>
          {find.trim()
            ? `${shown.length} ${shown.length === 1 ? "result" : "results"}`
            : `${contents.length} ${contents.length === 1 ? "item" : "items"}`}
        </StatusItem>
        {selected.length > 0 && (
          <StatusItem>
            {selected.length} {selected.length === 1 ? "item" : "items"}{" "}
            selected
            {chosenSize > 0 ? `  ${formatFileSize(chosenSize, platform)}` : ""}
          </StatusItem>
        )}
        <StatusBarSpacer />
      </StatusBar>
    </div>
  )
}

export { FileExplorer, FileGrid, FileList }
export type { FileEntry, FileExplorerProps, FileView, FileViewProps }
