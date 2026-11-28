import * as React from "react"

import { fileIconName, fileIconUrl, formatFileSize } from "../lib/file-icons"
import {
  type FileNode,
  isFolder,
  joinPath,
  matchesFind,
  sortFiles,
} from "../lib/files"
import { Close } from "../lib/icons"
import { cn } from "../lib/utils"
import { useContextMenu } from "./context-menu"
import {
  type FileActions,
  fileKey,
  fileMenu,
  Highlight,
  selectionKey,
  selectionMenu,
} from "./file-menu"
import { type Selected, toList, useFileSelection } from "./file-selection"
import { useNative } from "./provider"

// --- FileIcon -------------------------------------------------------------------------------------

type FileIconProps = Omit<React.ComponentProps<"span">, "children"> & {
  /** The file's or folder's name (package.json, photo.png, src): it picks the icon. */
  name: string
  folder?: boolean
  /** An open folder (a folder's open icon). */
  open?: boolean
  /** In px; 16 by default. */
  size?: number
}

/**
 * A file's or folder's icon, as in VS Code (Material Icon Theme): by its whole name first (package.json,
 * Dockerfile), then its extension; folders by name too (src, components, .git). Lighter variants in light
 * theme. The icon loads when shown; until then the space is kept.
 */
function FileIcon({
  name,
  folder = false,
  open = false,
  size = 16,
  className,
  style,
  ...props
}: FileIconProps) {
  const { theme } = useNative()
  const icon = fileIconName(name, { folder, open, theme })
  const [url, setUrl] = React.useState<string>()
  React.useEffect(() => {
    let current = true
    void fileIconUrl(icon).then((loaded) => current && setUrl(loaded))
    return () => {
      current = false
    }
  }, [icon])
  return (
    <span
      aria-hidden="true"
      data-icon={icon}
      className={cn("nt-file-icon", className)}
      style={{ width: size, height: size, ...style }}
      {...props}
    >
      {url && (
        <img src={url} alt="" width={size} height={size} draggable={false} />
      )}
    </span>
  )
}

// --- FileTree -------------------------------------------------------------------------------------

type FileTreeProps = Omit<React.ComponentProps<"div">, "onSelect"> &
  FileActions & {
    /** What the tree shows, for screen readers (e.g. "Files"). */
    "aria-label": string
    items: FileNode[]
    /** The chosen paths ("src/app.tsx"): one, or a list. Left out, the tree keeps its own choice. */
    selected?: Selected
    /** The choice changed: every chosen path. */
    onSelectionChange?: (paths: string[]) => void
    /** Ctrl/⌘+click, Shift+click, Shift+arrows and Ctrl+A choose several. True by default. */
    multiple?: boolean
    /** The item the focus went to (a click or the arrow keys). */
    onSelect?: (path: string, node: FileNode) => void
    /** Folders open to begin with, by path. */
    defaultExpanded?: string[]
    /** A path whose folders on the way must be open now (e.g. the folder shown beside the tree). */
    reveal?: string
    /** The path of the folder items are in, when they are not the top (paths start with it). */
    root?: string
    /** Only folders (Explorer's and Finder's side pane). */
    foldersOnly?: boolean
    /** Folders first, then by name. True by default; false keeps the order given. */
    sort?: boolean
    /** What Find typed: only matching items show, with the folders on the way to them, open; matches marked. */
    find?: string
  }

type Row = {
  path: string
  node: FileNode
  level: number
  folder: boolean
  parent?: string
}

/** The rows showing now: open folders' children under them; with Find, the matches and their folders. */
function visibleRows(
  items: FileNode[],
  expanded: Set<string>,
  options: { sort: boolean; foldersOnly: boolean; find?: string; root?: string }
): Row[] {
  const rows: Row[] = []
  const finding = !!options.find?.trim()
  // With Find: whether a node or anything under it matches.
  const keeps = (node: FileNode): boolean =>
    matchesFind(node.name, options.find ?? "") ||
    (node.children ?? []).some(keeps)
  const walk = (nodes: FileNode[], level: number, parent?: string) => {
    const shown = (options.sort ? sortFiles(nodes) : nodes).filter(
      (n) => (!options.foldersOnly || isFolder(n)) && (!finding || keeps(n))
    )
    for (const node of shown) {
      const path = joinPath(parent, node.name)
      const folder = isFolder(node)
      rows.push({ path, node, level, folder, parent })
      const open = finding
        ? (node.children ?? []).some(keeps)
        : expanded.has(path)
      if (folder && open) walk(node.children ?? [], level + 1, path)
    }
  }
  walk(items, 1, options.root || undefined)
  return rows
}

/**
 * Files and folders in a tree, as an IDE's explorer: a chevron (Windows) or disclosure triangle (Mac) opens a
 * folder, icons from FileIcon, indent lines. Arrow keys move (→ opens a folder or goes in, ← closes it or goes
 * up), Enter opens a file, Home/End jump. Every item has a right-click menu built from the actions given
 * (Open, Copy name, Copy path always), and their keys (F2, Delete).
 */
function FileTree({
  items,
  selected,
  onSelectionChange,
  multiple = true,
  onSelect,
  defaultExpanded = [],
  reveal,
  root,
  foldersOnly = false,
  sort = true,
  find,
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
  className,
  ...props
}: FileTreeProps) {
  const { platform } = useNative()
  const [expanded, setExpanded] = React.useState(() => new Set(defaultExpanded))
  const [focused, setFocused] = React.useState<string | undefined>(() =>
    toList(selected).at(-1)
  )
  const refs = React.useRef(new Map<string, HTMLDivElement>())

  // Open the folders on the way to the one shown beside the tree, each time it changes (and only then, so
  // they can still be closed by hand).
  const [revealed, setRevealed] = React.useState<string | undefined>()
  if (reveal !== revealed) {
    setRevealed(reveal)
    if (reveal) {
      const next = new Set(expanded)
      const parts = reveal.split("/")
      for (let i = 1; i < parts.length; i++)
        next.add(parts.slice(0, i).join("/"))
      setExpanded(next)
    }
  }

  const rows = React.useMemo(
    () => visibleRows(items, expanded, { sort, foldersOnly, find, root }),
    [items, expanded, sort, foldersOnly, find, root]
  )
  const byPath = React.useMemo(
    () => new Map(rows.map((r) => [r.path, r])),
    [rows]
  )
  const choice = useFileSelection({
    order: rows.map((r) => r.path),
    selected,
    multiple,
    onSelectionChange,
    platform,
  })
  const chosen = (paths: string[]) =>
    paths
      .map((p) => byPath.get(p))
      .filter((r): r is Row => !!r)
      .map(({ path, node }) => ({ path, node }))

  const toggle = (path: string, open?: boolean) =>
    setExpanded((now) => {
      const next = new Set(now)
      if (open ?? !next.has(path)) next.add(path)
      else next.delete(path)
      return next
    })

  // In the tree, Open on a folder opens it in place.
  const actions: FileActions = {
    onOpen: (path, node) =>
      isFolder(node) ? toggle(path, true) : onOpen?.(path, node),
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

  const focus = (path: string) => {
    setFocused(path)
    refs.current.get(path)?.focus()
  }

  function onKeyDown(event: React.KeyboardEvent, row: Row, index: number) {
    const acting = choice.has(row.path)
      ? chosen(choice.selection)
      : [{ path: row.path, node: row.node }]
    if (selectionKey(event, acting, actions, platform)) return
    if (choice.isSelectAll(event)) {
      event.preventDefault()
      choice.selectAll()
      return
    }
    if (event.key === " " && event.ctrlKey && platform === "windows") {
      event.preventDefault()
      choice.toggle(row.path)
      return
    }
    const go = (to: number) => {
      const target = rows[Math.max(0, Math.min(rows.length - 1, to))]
      if (target) {
        focus(target.path)
        choice.moved(target.path, event)
        onSelect?.(target.path, target.node)
      }
    }
    switch (event.key) {
      case "ArrowDown":
        go(index + 1)
        break
      case "ArrowUp":
        go(index - 1)
        break
      case "Home":
        go(0)
        break
      case "End":
        go(rows.length - 1)
        break
      case "ArrowRight":
        if (row.folder && !expanded.has(row.path)) toggle(row.path, true)
        else if (row.folder) go(index + 1)
        break
      case "ArrowLeft":
        if (row.folder && expanded.has(row.path)) toggle(row.path, false)
        else if (row.parent) go(rows.findIndex((r) => r.path === row.parent))
        break
      case "Enter":
        if (row.folder) toggle(row.path)
        else onOpen?.(row.path, row.node)
        break
      default:
        return
    }
    event.preventDefault()
  }

  const finding = !!find?.trim()
  const tabStop =
    focused && rows.some((r) => r.path === focused) ? focused : rows[0]?.path

  return (
    <div
      role="tree"
      aria-multiselectable={multiple}
      className={cn("nt-filetree", className)}
      onClick={(event) => {
        // A click on the empty space below the items clears the choice.
        if (event.target === event.currentTarget) choice.clear()
      }}
      {...props}
    >
      {rows.length === 0 && (
        <div className="nt-files-empty">
          {finding ? `Nothing matches “${find}”.` : "This folder is empty."}
        </div>
      )}
      {rows.map((row, index) => (
        <FileTreeRow
          key={row.path}
          row={row}
          open={finding ? row.folder : expanded.has(row.path)}
          selected={choice.has(row.path)}
          tabIndex={row.path === tabStop ? 0 : -1}
          find={find}
          rowRef={(element) => {
            if (element) refs.current.set(row.path, element)
            else refs.current.delete(row.path)
          }}
          menu={() =>
            selectionMenu(chosen(choice.forMenu(row.path)), actions, platform)
          }
          onClick={(event) => {
            setFocused(row.path)
            choice.click(row.path, event)
            onSelect?.(row.path, row.node)
            // In an IDE's tree a plain click opens a folder; in a side pane it only chooses it (the chevron
            // opens); Ctrl/⌘ or Shift only choose.
            const plain = !event.shiftKey && !event.ctrlKey && !event.metaKey
            if (row.folder && !foldersOnly && plain) toggle(row.path)
          }}
          onTwisty={() => toggle(row.path)}
          onDoubleClick={() => {
            if (!row.folder) onOpen?.(row.path, row.node)
            else if (foldersOnly) toggle(row.path)
          }}
          onKeyDown={(event) => onKeyDown(event, row, index)}
        />
      ))}
    </div>
  )
}

function FileTreeRow({
  row,
  open,
  selected,
  tabIndex,
  find,
  rowRef,
  menu,
  onClick,
  onTwisty,
  onDoubleClick,
  onKeyDown,
}: {
  row: Row
  open: boolean
  selected: boolean
  tabIndex: number
  find?: string
  rowRef: (element: HTMLDivElement | null) => void
  menu: () => ReturnType<typeof fileMenu>
  onClick: (event: React.MouseEvent) => void
  onTwisty: () => void
  onDoubleClick: () => void
  onKeyDown: (event: React.KeyboardEvent) => void
}) {
  const { onContextMenu } = useContextMenu(menu)
  return (
    <div
      ref={rowRef}
      role="treeitem"
      aria-level={row.level}
      aria-expanded={row.folder ? open : undefined}
      aria-selected={selected}
      tabIndex={tabIndex}
      title={row.path}
      className="nt-filetree-row"
      style={{ "--nt-level": row.level - 1 } as React.CSSProperties}
      onClick={onClick}
      onDoubleClick={onDoubleClick}
      onKeyDown={onKeyDown}
      onContextMenu={onContextMenu}
    >
      <span className="nt-filetree-guides" aria-hidden="true" />
      <span
        className="nt-filetree-twisty"
        aria-hidden="true"
        onClick={(event) => {
          if (!row.folder) return
          event.stopPropagation()
          onTwisty()
        }}
      >
        {row.folder && (
          <svg viewBox="0 0 16 16">
            {/* Windows: a chevron; a Mac: a filled triangle (CSS shows the one for the system). */}
            <path
              className="nt-twisty-chevron"
              d="m6 4 4 4-4 4"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.4"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
            <path
              className="nt-twisty-triangle"
              d="M5.5 3.5v9l6-4.5z"
              fill="currentColor"
            />
          </svg>
        )}
      </span>
      <FileIcon name={row.node.name} folder={row.folder} open={open} />
      <span className="nt-filetree-name">
        <Highlight text={row.node.name} find={find} />
      </span>
    </div>
  )
}

// --- FileChip -------------------------------------------------------------------------------------

type FileChipProps = Omit<React.ComponentProps<"div">, "children"> &
  Omit<FileActions, "onOpen" | "onNewFile" | "onNewFolder"> & {
    name: string
    /**
     * "card" (the default): an attachment, with icon, name and size. "inline": a small pill with the icon and
     * the name, for a file named inside text or a message.
     */
    variant?: "card" | "inline"
    /** In bytes; written as the system writes sizes (cards). */
    size?: number
    /** Instead of the size: "Uploading 40%", "Failed: too large". */
    status?: React.ReactNode
    /** The status says what went wrong (in the error colour). */
    error?: boolean
    /** 0 to 100 while it uploads: a thin bar along the bottom (cards). */
    progress?: number
    /** Opens the file: a click or Enter. */
    onOpen?: () => void
    /** Shows the × that takes it off (e.g. before sending). */
    onRemove?: () => void
    /** Its path, for Copy path (the name when left out). */
    path?: string
  }

/** Splits "a-very-long-name.final.pdf" so the end stays visible when the name is cut short. */
function splitName(name: string): [string, string] {
  const dot = name.lastIndexOf(".")
  if (dot <= 0) return [name, ""]
  const keep = Math.max(dot, name.length - 8)
  return [name.slice(0, keep), name.slice(keep)]
}

/**
 * A file shown on its own: an attachment ("card": icon, name with its extension always showing, size, ×), or a
 * file named in text ("inline": a small pill). Right-click: Open, Copy name, Copy path, and the actions given.
 */
function FileChip({
  name,
  variant = "card",
  size,
  status,
  error = false,
  progress,
  onOpen,
  onRemove,
  path,
  onRename,
  onDelete,
  onReveal,
  onDownload,
  onProperties,
  menu,
  className,
  ...props
}: FileChipProps) {
  const { platform } = useNative()
  const node: FileNode = { name, size }
  const where = path ?? name
  const actions: FileActions = {
    onOpen: onOpen && (() => onOpen()),
    onRename,
    onDelete,
    onReveal,
    onDownload,
    onProperties,
    menu,
  }
  const { onContextMenu } = useContextMenu(() =>
    fileMenu(where, node, actions, platform)
  )
  const [start, end] = splitName(name)
  const inline = variant === "inline"
  return (
    <div
      role={onOpen ? "button" : undefined}
      tabIndex={onOpen ? 0 : undefined}
      title={where}
      data-variant={variant}
      className={cn("nt-filechip", className)}
      onClick={onOpen}
      onKeyDown={(event) => {
        if (fileKey(event, where, node, actions, platform)) return
        if (onOpen && (event.key === "Enter" || event.key === " ")) {
          event.preventDefault()
          onOpen()
        }
      }}
      onContextMenu={onContextMenu}
      {...props}
    >
      <FileIcon name={name} size={inline ? 16 : 28} />
      <span className="nt-filechip-text">
        <span className="nt-filechip-name">
          <span className="nt-filechip-start">{start}</span>
          <span className="nt-filechip-end">{end}</span>
        </span>
        {!inline && (
          <span className="nt-filechip-meta" data-error={error || undefined}>
            {status ?? (size != null ? formatFileSize(size, platform) : null)}
          </span>
        )}
      </span>
      {onRemove && (
        <button
          type="button"
          className="nt-filechip-remove"
          aria-label={`Remove ${name}`}
          title="Remove"
          onClick={(event) => {
            event.stopPropagation()
            onRemove()
          }}
        >
          <Close />
        </button>
      )}
      {progress != null && !inline && (
        <span
          className="nt-filechip-progress"
          role="progressbar"
          aria-label={`Uploading ${name}`}
          aria-valuenow={progress}
          aria-valuemin={0}
          aria-valuemax={100}
        >
          <span style={{ width: `${Math.min(100, Math.max(0, progress))}%` }} />
        </span>
      )}
    </div>
  )
}

export { FileChip, FileIcon, FileTree }
export type { FileActions } from "./file-menu"
export type { FileNode } from "../lib/files"
export type { FileChipProps, FileIconProps, FileTreeProps }
