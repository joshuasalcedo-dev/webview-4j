// What the file views show about a file, written as each system writes it.
import type { Platform } from "./platform"

/** A file, or a folder when it has children (an empty array is an empty folder). */
type FileNode = {
  name: string
  children?: FileNode[]
  /** In bytes (files). */
  size?: number
  /** When it last changed. */
  modified?: Date | string
}

const isFolder = (node: FileNode) => node.children !== undefined

/** "src/app.tsx" from a parent's path and a name. */
const joinPath = (parent: string | undefined, name: string) =>
  parent ? `${parent}/${name}` : name

/** The node at a path ("" is the top), or undefined. */
function nodeAt(items: FileNode[], path: string): FileNode | undefined {
  if (path === "") return { name: "", children: items }
  let level = items
  let found: FileNode | undefined
  for (const part of path.split("/")) {
    found = level.find((n) => n.name === part)
    if (!found) return undefined
    level = found.children ?? []
  }
  return found
}

/** Folders first, then by name as people read numbers (2 before 10), as Explorer, Finder and IDEs sort. */
function sortFiles(nodes: FileNode[]): FileNode[] {
  return [...nodes].sort(
    (a, b) =>
      Number(isFolder(b)) - Number(isFolder(a)) ||
      a.name.localeCompare(b.name, undefined, {
        numeric: true,
        sensitivity: "base",
      })
  )
}

/** The Type (Windows) or Kind (Mac) column: "PDF File" / "PDF document", "File folder" / "Folder". */
function fileType(node: FileNode, platform: Platform): string {
  if (isFolder(node)) return platform === "mac" ? "Folder" : "File folder"
  const dot = node.name.lastIndexOf(".")
  if (dot <= 0) return platform === "mac" ? "Document" : "File"
  const extension = node.name.slice(dot + 1).toUpperCase()
  return platform === "mac" ? `${extension} document` : `${extension} File`
}

/** The Date modified column: Windows "10/20/2026 3:24 AM"; a Mac "Today at 3:24 AM", "Oct 20, 2026 at 3:24 AM". */
function formatModified(
  value: Date | string | undefined,
  platform: Platform
): string {
  if (value == null) return ""
  const date = typeof value === "string" ? new Date(value) : value
  if (Number.isNaN(date.getTime())) return ""
  const time = date.toLocaleTimeString(undefined, {
    hour: "numeric",
    minute: "2-digit",
  })
  if (platform === "windows")
    return `${date.toLocaleDateString(undefined, { year: "numeric", month: "numeric", day: "numeric" })} ${time}`
  const today = new Date()
  const yesterday = new Date(
    today.getFullYear(),
    today.getMonth(),
    today.getDate() - 1
  )
  if (date.toDateString() === today.toDateString()) return `Today at ${time}`
  if (date.toDateString() === yesterday.toDateString())
    return `Yesterday at ${time}`
  return `${date.toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" })} at ${time}`
}

/** Whether a name matches what was typed in Find: every word, in any order, ignoring case. */
function matchesFind(name: string, query: string): boolean {
  const lower = name.toLowerCase()
  return query
    .toLowerCase()
    .split(/\s+/)
    .filter(Boolean)
    .every((word) => lower.includes(word))
}

type Found = { path: string; node: FileNode; parent: string }

/** Everything under a folder whose name matches, at any depth (as Explorer's and Finder's search). */
function findFiles(items: FileNode[], query: string, under = ""): Found[] {
  const found: Found[] = []
  const walk = (nodes: FileNode[], parent: string) => {
    for (const node of sortFiles(nodes)) {
      const path = joinPath(parent || undefined, node.name)
      if (matchesFind(node.name, query)) found.push({ path, node, parent })
      if (node.children) walk(node.children, path)
    }
  }
  walk(nodeAt(items, under)?.children ?? [], under)
  return found
}

export {
  fileType,
  findFiles,
  formatModified,
  isFolder,
  joinPath,
  matchesFind,
  nodeAt,
  sortFiles,
}
export type { FileNode, Found }
