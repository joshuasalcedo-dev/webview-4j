import * as React from "react"

import type { Platform } from "../lib/platform"

/** One path or several; the views take either. */
type Selected = string | string[] | undefined

const toList = (selected: Selected): string[] =>
  selected === undefined
    ? []
    : typeof selected === "string"
      ? [selected]
      : selected

type SelectionOptions = {
  /** Every path in the order shown (for Shift ranges and Select all). */
  order: string[]
  /** Controlled: the chosen paths. Left out, the view keeps its own. */
  selected?: Selected
  /** Ctrl/⌘ and Shift choose several; false: one at a time. */
  multiple?: boolean
  onSelectionChange?: (paths: string[]) => void
  platform: Platform
}

/**
 * Choosing files, as Explorer and Finder do: a click chooses one; Ctrl+click (⌘ on a Mac) adds or takes one
 * away; Shift+click chooses everything from the last one clicked; Shift+arrows grow the choice; Ctrl+A (⌘A)
 * chooses all; on Windows Ctrl+arrows move without choosing and Ctrl+Space adds or takes away.
 */
function useFileSelection({
  order,
  selected,
  multiple = true,
  onSelectionChange,
  platform,
}: SelectionOptions) {
  const controlled = selected !== undefined
  const [own, setOwn] = React.useState<string[]>(() => toList(selected))
  const current = controlled ? toList(selected) : own
  const anchor = React.useRef<string | undefined>(current[current.length - 1])

  const set = (paths: string[]) => {
    if (!controlled) setOwn(paths)
    onSelectionChange?.(paths)
  }
  const has = (path: string) => current.includes(path)
  const range = (to: string) => {
    const from = order.indexOf(anchor.current ?? to)
    const end = order.indexOf(to)
    if (from === -1 || end === -1) return [to]
    return order.slice(Math.min(from, end), Math.max(from, end) + 1)
  }
  /** The key that adds one item to the choice: Ctrl on Windows, ⌘ on a Mac. */
  const adds = (event: React.MouseEvent | React.KeyboardEvent) =>
    platform === "mac" ? event.metaKey : event.ctrlKey

  return {
    selection: current,
    has,

    /** A click on an item. */
    click(path: string, event: React.MouseEvent) {
      if (multiple && event.shiftKey) {
        // Ctrl+Shift+click adds the range to what was chosen (Windows); Shift alone replaces it.
        const span = range(path)
        set(adds(event) ? Array.from(new Set([...current, ...span])) : span)
        return
      }
      if (multiple && adds(event)) {
        set(has(path) ? current.filter((p) => p !== path) : [...current, path])
        anchor.current = path
        return
      }
      anchor.current = path
      set([path])
    },

    /**
     * The focus moved to an item with the keys. Shift grows the choice from the anchor; Ctrl on Windows only
     * moves the focus (Ctrl+Space then chooses); otherwise the item is chosen alone.
     */
    moved(path: string, event: React.KeyboardEvent) {
      if (multiple && event.shiftKey) set(range(path))
      else if (multiple && platform === "windows" && event.ctrlKey) return
      else {
        anchor.current = path
        set([path])
      }
    },

    /** Ctrl+Space (Windows): adds the focused item or takes it away. */
    toggle(path: string) {
      if (!multiple) return
      anchor.current = path
      set(has(path) ? current.filter((p) => p !== path) : [...current, path])
    },

    selectAll() {
      if (multiple) set(order)
    },

    clear() {
      if (current.length > 0) set([])
    },

    /**
     * A right-click on an item: when it is not chosen, it becomes the only one chosen (as both systems do).
     * The paths the menu acts on.
     */
    forMenu(path: string): string[] {
      if (has(path)) return current
      anchor.current = path
      set([path])
      return [path]
    },

    /** Whether this key is Select all (Ctrl+A, ⌘A). */
    isSelectAll(event: React.KeyboardEvent) {
      return multiple && adds(event) && event.key.toLowerCase() === "a"
    },
  }
}

export { toList, useFileSelection }
export type { Selected }
