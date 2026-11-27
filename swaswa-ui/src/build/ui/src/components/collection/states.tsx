import * as React from "react"

import { Button } from "../button"
import "./collection.css"

/**
 * What a collection shows when it has nothing to show, is loading, or could not load: the same everywhere (tables,
 * lists, grids, trees), as the systems do it.
 */

/** Nothing here: a title, a line about it, and what to do (an action). */
type EmptyStateProps = {
  title: React.ReactNode
  message?: React.ReactNode
  /** A picture above the title: an icon, an illustration. */
  icon?: React.ReactNode
  action?: { label: string; onClick: () => void }
  compact?: boolean
}

function EmptyState({ title, message, icon, action, compact }: EmptyStateProps) {
  return (
    <div className="nt-state" data-compact={compact ? "" : undefined} role="status">
      {icon != null && <div className="nt-state-icon">{icon}</div>}
      <div className="nt-state-title">{title}</div>
      {message != null && <div className="nt-state-message">{message}</div>}
      {action && (
        <Button variant="accent" onClick={action.onClick}>
          {action.label}
        </Button>
      )}
    </div>
  )
}

/** Could not load: what went wrong, and Try again. */
type ErrorStateProps = {
  title?: React.ReactNode
  message?: React.ReactNode
  onRetry?: () => void
}

function ErrorState({ title = "This could not be loaded", message, onRetry }: ErrorStateProps) {
  return (
    <div className="nt-state" data-error="" role="alert">
      <div className="nt-state-title">{title}</div>
      {message != null && <div className="nt-state-message">{message}</div>}
      {onRetry && <Button onClick={onRetry}>Try again</Button>}
    </div>
  )
}

/** Loading for the first time: grey shapes where the rows will be, so the page does not jump when they come. */
function SkeletonRows({ rows = 6, columns = 4 }: { rows?: number; columns?: number }) {
  return (
    <div className="nt-skeleton" aria-busy="true" aria-label="Loading">
      {Array.from({ length: rows }, (_, r) => (
        <div key={r} className="nt-skeleton-row">
          {Array.from({ length: columns }, (_, c) => (
            <span key={c} className="nt-skeleton-cell" style={{ width: `${55 + ((r * 7 + c * 13) % 40)}%` }} />
          ))}
        </div>
      ))}
    </div>
  )
}

/** Loading again with the rows still there: a thin bar running along the top. */
function LoadingBar() {
  return <div className="nt-loading-bar" role="progressbar" aria-label="Loading" />
}

/** Previous, "Page 2 of 7 · 130 in all", Next: under a collection with more than one page. */
function Pages({ page, pages, total, onPage }: { page: number; pages: number; total?: number; onPage: (page: number) => void }) {
  if (pages <= 1) return null
  return (
    <div className="nt-datatable-pages" role="navigation" aria-label="Pages">
      <Button variant="subtle" disabled={page <= 1} onClick={() => onPage(page - 1)}>
        Previous
      </Button>
      <span className="nt-datatable-count">
        Page {page} of {pages}
        {total != null ? ` · ${total} in all` : ""}
      </span>
      <Button variant="subtle" disabled={page >= pages} onClick={() => onPage(page + 1)}>
        Next
      </Button>
    </div>
  )
}

/** What a collection says (or nothing) for the state it is in, the same for all of them. */
function CollectionState({
  loading,
  loadedOnce,
  error,
  onRetry,
  count,
  filtering,
  onClear,
  empty,
  columns = 3,
}: {
  loading: boolean
  loadedOnce: boolean
  error?: { title?: string; message?: string } | null
  onRetry?: () => void
  count: number
  filtering: boolean
  onClear?: () => void
  empty?: React.ReactNode | EmptyStateProps
  columns?: number
}) {
  if (loading && !loadedOnce) return <SkeletonRows columns={columns} />
  if (loading) return null
  if (error) return <ErrorState title={error.title} message={error.message} onRetry={onRetry} />
  if (count > 0) return null
  if (filtering)
    return <EmptyState title="No matches" message="Nothing matches the search and the filters." action={onClear ? { label: "Clear filters", onClick: onClear } : undefined} compact />
  if (empty != null && typeof empty === "object" && "title" in (empty as object)) return <EmptyState {...(empty as EmptyStateProps)} />
  return <div className="nt-datatable-note">{(empty as React.ReactNode) ?? "Nothing here."}</div>
}

/** Whether a collection has shown rows yet: grey rows the first time it loads, the rows kept after. */
function useLoadedOnce(loading: boolean, count: number) {
  const once = React.useRef(false)
  if (!loading && count > 0) once.current = true
  return once.current
}

export { CollectionState, EmptyState, ErrorState, LoadingBar, Pages, SkeletonRows, useLoadedOnce }
export type { EmptyStateProps, ErrorStateProps }
