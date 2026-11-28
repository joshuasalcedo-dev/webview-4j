/**
 * What a person asks of a collection (a table, a list, a grid), as one object: which page and how big, the order,
 * the search box's text, and each column's filter. A collection with `onQueryChange` sends it whenever it changes,
 * and shows what it is given (the server's page); without, it does all of it itself.
 */

/** One sort order: by `key`, descending or not. The first in a list sorts first. */
type SortOrder = { key: string; desc: boolean }

/** A column's filter. */
type ColumnFilter =
  /** The text contains this (any case). */
  | { kind: "text"; text: string }
  /** One of these values (an enum, a status). */
  | { kind: "options"; values: string[] }
  /** Between these, either end open (numbers, money in its smallest unit, ISO dates). */
  | { kind: "range"; from?: number | string; to?: number | string }
  /** Yes or no. */
  | { kind: "boolean"; value: boolean }

type CollectionQuery = {
  /** From 1. */
  page: number
  size: number
  sort: SortOrder[]
  search: string
  filters: Record<string, ColumnFilter>
}

/** Where a collection is in its pages: the page (from 1), its size, and how many there are in all. */
type Paging = { page: number; size: number; total: number }

const EMPTY_QUERY: CollectionQuery = { page: 1, size: 0, sort: [], search: "", filters: {} }

/** Whether a filter asks for anything (an empty text or no options asks for nothing). */
function isActive(filter: ColumnFilter | undefined): boolean {
  if (!filter) return false
  switch (filter.kind) {
    case "text":
      return filter.text.trim() !== ""
    case "options":
      return filter.values.length > 0
    case "range":
      return (filter.from != null && filter.from !== "") || (filter.to != null && filter.to !== "")
    case "boolean":
      return true
  }
}

/** Whether `value` passes `filter`: for collections that filter in the page. */
function matches(value: unknown, filter: ColumnFilter): boolean {
  switch (filter.kind) {
    case "text":
      return String(value ?? "")
        .toLowerCase()
        .includes(filter.text.trim().toLowerCase())
    case "options":
      return filter.values.includes(String(value ?? ""))
    case "boolean":
      return Boolean(value) === filter.value
    case "range": {
      const compare = (a: unknown, b: number | string) =>
        typeof b === "number" ? Number(a) - b : String(a ?? "").localeCompare(String(b))
      if (filter.from != null && filter.from !== "" && compare(value, filter.from) < 0) return false
      if (filter.to != null && filter.to !== "" && compare(value, filter.to) > 0) return false
      return true
    }
  }
}

export { EMPTY_QUERY, isActive, matches }
export type { CollectionQuery, ColumnFilter, Paging, SortOrder }
