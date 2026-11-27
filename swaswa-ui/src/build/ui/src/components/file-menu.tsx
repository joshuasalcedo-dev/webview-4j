import * as React from "react"

import type { FileNode } from "../lib/files"
import type { Platform } from "../lib/platform"
import type { MenuEntry } from "./context-menu"

/**
 * What can be done to files, for every file view (FileTree, FileGrid, FileList, FileExplorer, FileChip).
 * Each one given adds its item to every file's right-click menu, and its key (F2, Delete); left out, the item
 * is not there. Open, Copy name and Copy path are always there.
 */
type FileActions = {
  /** Double-click or Enter on a file (a folder opens in the view instead). */
  onOpen?: (path: string, node: FileNode) => void
  /** Rename… (F2 on Windows, Enter on a Mac's selected item is taken by Open, so F2 on both). */
  onRename?: (path: string, node: FileNode) => void
  /** Delete (Windows) / Move to Trash (Mac); the Delete key, ⌘⌫ on a Mac. */
  onDelete?: (path: string, node: FileNode) => void
  /** Show in Explorer (Windows) / Reveal in Finder (Mac). */
  onReveal?: (path: string, node: FileNode) => void
  /** New file… / New folder… in a folder's menu (and the empty space of a view). */
  onNewFile?: (folderPath: string) => void
  onNewFolder?: (folderPath: string) => void
  /** Download / Save as… (e.g. an attachment). */
  onDownload?: (path: string, node: FileNode) => void
  /** Properties (Windows) / Get Info (Mac). */
  onProperties?: (path: string, node: FileNode) => void
  /**
   * Several chosen items deleted at once (one question for all of them). Left out, onDelete is called for
   * each.
   */
  onDeleteMany?: (items: { path: string; node: FileNode }[]) => void
  /** More items, put before Delete (e.g. Share, Add to favourites). */
  menu?: (path: string, node: FileNode) => MenuEntry[]
}

const copy = (text: string) =>
  void navigator.clipboard?.writeText(text).catch(() => undefined)

/** A file's right-click menu, built from the actions given, in the system's words and order. */
function fileMenu(
  path: string,
  node: FileNode,
  actions: FileActions,
  platform: Platform
): MenuEntry[] {
  const mac = platform === "mac"
  const folder = node.children !== undefined
  const entries: MenuEntry[] = []
  const group = (items: (MenuEntry | false | undefined)[]) => {
    const kept = items.filter(Boolean) as MenuEntry[]
    if (kept.length === 0) return
    if (entries.length > 0) entries.push("separator")
    entries.push(...kept)
  }

  group([
    actions.onOpen && {
      label: "Open",
      shortcut: mac ? "Mod+O" : "Enter",
      onSelect: () => actions.onOpen?.(path, node),
    },
    actions.onDownload && {
      label: mac ? "Download" : "Save as…",
      onSelect: () => actions.onDownload?.(path, node),
    },
    actions.onReveal && {
      label: mac ? "Reveal in Finder" : "Show in Explorer",
      onSelect: () => actions.onReveal?.(path, node),
    },
  ])
  if (folder) {
    group([
      actions.onNewFile && {
        label: "New file…",
        onSelect: () => actions.onNewFile?.(path),
      },
      actions.onNewFolder && {
        label: "New folder…",
        shortcut: mac ? "Mod+Shift+N" : "Ctrl+Shift+N",
        onSelect: () => actions.onNewFolder?.(path),
      },
    ])
  }
  group([
    { label: "Copy name", onSelect: () => copy(node.name) },
    {
      label: mac ? "Copy path" : "Copy as path",
      shortcut: mac ? "Mod+Alt+C" : "Ctrl+Shift+C",
      onSelect: () => copy(path),
    },
  ])
  group([
    actions.onRename && {
      label: "Rename…",
      shortcut: "F2",
      onSelect: () => actions.onRename?.(path, node),
    },
  ])
  group(actions.menu?.(path, node) ?? [])
  group([
    actions.onDelete && {
      label: mac ? "Move to Trash" : "Delete",
      shortcut: mac ? "Mod+Backspace" : "Delete",
      onSelect: () => actions.onDelete?.(path, node),
    },
  ])
  group([
    actions.onProperties && {
      label: mac ? "Get Info" : "Properties",
      shortcut: mac ? "Mod+I" : "Alt+Enter",
      onSelect: () => actions.onProperties?.(path, node),
    },
  ])
  return entries
}

/** The menu for the empty space of a folder's view: New file, New folder. */
function folderSpaceMenu(
  folderPath: string,
  actions: FileActions,
  platform: Platform
): MenuEntry[] {
  const entries: MenuEntry[] = []
  if (actions.onNewFolder)
    entries.push({
      label: "New folder…",
      shortcut: platform === "mac" ? "Mod+Shift+N" : "Ctrl+Shift+N",
      onSelect: () => actions.onNewFolder?.(folderPath),
    })
  if (actions.onNewFile)
    entries.push({
      label: "New file…",
      onSelect: () => actions.onNewFile?.(folderPath),
    })
  return entries
}

/**
 * The files' keys, the same in every view: F2 renames, Delete (⌘⌫ on a Mac) deletes, Alt+Enter (⌘I) shows the
 * properties, Ctrl+Shift+C (⌘⌥C) copies the path. True when it was one of them.
 */
function fileKey(
  event: React.KeyboardEvent,
  path: string,
  node: FileNode,
  actions: FileActions,
  platform: Platform
): boolean {
  const mac = platform === "mac"
  const mod = mac ? event.metaKey : event.ctrlKey
  if (event.key === "F2" && actions.onRename) actions.onRename(path, node)
  else if (
    (mac
      ? event.metaKey && event.key === "Backspace"
      : event.key === "Delete") &&
    actions.onDelete
  )
    actions.onDelete(path, node)
  else if (
    (mac
      ? event.metaKey && event.key.toLowerCase() === "i"
      : event.altKey && event.key === "Enter") &&
    actions.onProperties
  )
    actions.onProperties(path, node)
  else if (
    mod &&
    event.key.toLowerCase() === "c" &&
    (mac ? event.altKey : event.shiftKey)
  )
    copy(path)
  else return false
  event.preventDefault()
  return true
}

/** Marks what Find matched in a name. */
function Highlight({ text, find }: { text: string; find?: string }) {
  const words = (find ?? "").toLowerCase().split(/\s+/).filter(Boolean)
  if (words.length === 0) return <>{text}</>
  const lower = text.toLowerCase()
  const marked = new Array<boolean>(text.length).fill(false)
  for (const word of words) {
    for (
      let at = lower.indexOf(word);
      at !== -1;
      at = lower.indexOf(word, at + 1)
    )
      marked.fill(true, at, at + word.length)
  }
  const parts: React.ReactNode[] = []
  let start = 0
  for (let i = 1; i <= text.length; i++) {
    if (i === text.length || marked[i] !== marked[start]) {
      const piece = text.slice(start, i)
      parts.push(
        marked[start] ? (
          <mark key={start} className="nt-find-match">
            {piece}
          </mark>
        ) : (
          piece
        )
      )
      start = i
    }
  }
  return <>{parts}</>
}

type Chosen = { path: string; node: FileNode }[]

/** Several names or paths for the clipboard, one per line. */
const lines = (texts: string[]) => texts.join(String.fromCharCode(10))

/** Deletes several: onDeleteMany when given, otherwise onDelete for each. */
function deleteAll(items: Chosen, actions: FileActions) {
  if (actions.onDeleteMany) actions.onDeleteMany(items)
  else for (const item of items) actions.onDelete?.(item.path, item.node)
}

/**
 * The right-click menu when several items are chosen: what can be done to all of them at once (Open the files,
 * Download, Copy names, Copy paths, Delete N items); Rename and Properties are for one item, so not here.
 */
function selectionMenu(
  items: Chosen,
  actions: FileActions,
  platform: Platform
): MenuEntry[] {
  if (items.length === 1)
    return fileMenu(items[0].path, items[0].node, actions, platform)
  const mac = platform === "mac"
  const files = items.filter((i) => i.node.children === undefined)
  const entries: MenuEntry[] = []
  if (actions.onOpen && files.length > 0)
    entries.push({
      label:
        files.length === items.length ? "Open" : `Open ${files.length} files`,
      onSelect: () => files.forEach((f) => actions.onOpen?.(f.path, f.node)),
    })
  if (actions.onDownload && files.length > 0)
    entries.push({
      label: mac ? "Download" : "Save as…",
      onSelect: () =>
        files.forEach((f) => actions.onDownload?.(f.path, f.node)),
    })
  if (entries.length > 0) entries.push("separator")
  entries.push(
    {
      label: "Copy names",
      onSelect: () => copy(lines(items.map((i) => i.node.name))),
    },
    {
      label: mac ? "Copy paths" : "Copy as paths",
      shortcut: mac ? "Mod+Alt+C" : "Ctrl+Shift+C",
      onSelect: () => copy(lines(items.map((i) => i.path))),
    }
  )
  if (actions.onDelete || actions.onDeleteMany)
    entries.push("separator", {
      label: mac
        ? `Move ${items.length} Items to Trash`
        : `Delete ${items.length} items`,
      shortcut: mac ? "Mod+Backspace" : "Delete",
      onSelect: () => deleteAll(items, actions),
    })
  return entries
}

/** The files' keys for what is chosen: with one item as fileKey; with several, Delete and Copy paths for all. */
function selectionKey(
  event: React.KeyboardEvent,
  items: Chosen,
  actions: FileActions,
  platform: Platform
): boolean {
  if (items.length === 1)
    return fileKey(event, items[0].path, items[0].node, actions, platform)
  if (items.length === 0) return false
  const mac = platform === "mac"
  if (
    (mac
      ? event.metaKey && event.key === "Backspace"
      : event.key === "Delete") &&
    (actions.onDelete || actions.onDeleteMany)
  )
    deleteAll(items, actions)
  else if (
    (mac ? event.metaKey && event.altKey : event.ctrlKey && event.shiftKey) &&
    event.key.toLowerCase() === "c"
  )
    copy(lines(items.map((i) => i.path)))
  else return false
  event.preventDefault()
  return true
}

export {
  fileKey,
  fileMenu,
  folderSpaceMenu,
  Highlight,
  selectionKey,
  selectionMenu,
}
export type { FileActions }
