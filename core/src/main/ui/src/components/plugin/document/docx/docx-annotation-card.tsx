import type {
  DocxComment,
  DocxCommentCardRenderProps,
  DocxTrackedChange,
  DocxTrackedChangeCardRenderProps,
  DocxTrackedChangeKind,
} from "@extend-ai/react-docx"
import * as React from "react"

import { cn } from "../../../../lib/utils"
import { Pill, type PillColor } from "../../../pill"
import "./docx.css"

/** What an annotation is: a comment (or a reply, or one marked done), or a tracked change of one kind. */
export type DocxAnnotationKind = "comment" | "reply" | "resolved" | DocxTrackedChangeKind

const COLOR: Record<DocxAnnotationKind, PillColor> = {
  comment: "blue",
  reply: "purple",
  resolved: "grey",
  insertion: "green",
  "move-to": "green",
  deletion: "red",
  "move-from": "red",
  "format-change": "orange",
  "paragraph-format-change": "orange",
}

const LABEL: Record<DocxAnnotationKind, string> = {
  comment: "Comment",
  reply: "Reply",
  resolved: "Resolved",
  insertion: "Inserted",
  deletion: "Removed",
  "move-from": "Moved from",
  "move-to": "Moved to",
  "format-change": "Formatted",
  "paragraph-format-change": "Paragraph",
}

type DocxAnnotationCardProps = Omit<React.ComponentProps<"div">, "children"> & {
  kind: DocxAnnotationKind
  /** Who wrote it ("Unknown author" if left out). */
  author?: string
  /** When, as the viewer formats it. */
  date?: string
  /** The commented text, quoted above the comment. */
  anchorText?: string
  /** The comment, or the changed text. */
  text: string
  /** The badge's text (the kind's own name unless set). */
  badge?: string
  /** It is the one chosen (outlined in the accent colour). */
  active?: boolean
}

/**
 * A comment or a tracked change of a Word document, as a card: who and when, a coloured badge for what it is, the
 * text it is about. The viewer puts these in the page's margin, by the text they belong to; DocxAnnotationList lists
 * them in a panel. Clickable when given an onClick.
 */
export function DocxAnnotationCard({
  kind,
  author,
  date,
  anchorText,
  text,
  badge,
  active,
  className,
  onClick,
  ...props
}: DocxAnnotationCardProps) {
  const clickable = onClick != null
  return (
    <div
      role={clickable ? "button" : undefined}
      tabIndex={clickable ? 0 : undefined}
      data-kind={kind}
      data-active={active || undefined}
      data-clickable={clickable || undefined}
      className={cn("nt-docx-card", className)}
      onClick={onClick}
      onKeyDown={
        clickable
          ? (event) => {
              if (event.key === "Enter" || event.key === " ") {
                event.preventDefault()
                event.currentTarget.click()
              }
            }
          : undefined
      }
      {...props}
    >
      <div className="nt-docx-card-head">
        <div className="nt-docx-card-meta">
          <div className="nt-docx-card-author">{author?.trim() || "Unknown author"}</div>
          {date && <div className="nt-docx-card-date">{date}</div>}
        </div>
        <Pill color={COLOR[kind] ?? "grey"} className="nt-docx-card-badge">
          {badge ?? LABEL[kind] ?? kind}
        </Pill>
      </div>
      {anchorText && <div className="nt-docx-card-anchor">{anchorText}</div>}
      {text && <div className="nt-docx-card-text">{text}</div>}
    </div>
  )
}

/** A comment's kind: a reply, one marked done, or a comment. */
export function commentKind(comment: Pick<DocxComment, "resolved" | "parentId">): DocxAnnotationKind {
  return comment.resolved ? "resolved" : comment.parentId !== undefined ? "reply" : "comment"
}

/** How the viewer draws a tracked change in the page's margin: as a DocxAnnotationCard. */
export function createDocxTrackedChangeCardRenderer() {
  return function renderTrackedChangeCard({ change, formattedDate, kindLabel, snippet, style }: DocxTrackedChangeCardRenderProps) {
    const known = change.kind in LABEL
    return (
      <DocxAnnotationCard
        className="nt-docx-card-margin"
        style={style}
        kind={change.kind}
        badge={known ? undefined : kindLabel}
        author={change.author}
        date={formattedDate}
        text={snippet}
      />
    )
  }
}

/** How the viewer draws a comment in the page's margin: as a DocxAnnotationCard. */
export function createDocxCommentCardRenderer() {
  return function renderCommentCard({ comment, formattedDate, snippet, style }: DocxCommentCardRenderProps) {
    return (
      <DocxAnnotationCard
        className="nt-docx-card-margin"
        style={style}
        kind={commentKind(comment)}
        author={comment.author}
        date={formattedDate}
        anchorText={comment.anchorText}
        text={snippet}
      />
    )
  }
}

/** A date as the cards show it (e.g. "Nov 22, 2026, 10:00 AM"), or undefined. */
export function formatAnnotationDate(date: string | undefined): string | undefined {
  if (!date) return undefined
  const parsed = new Date(date)
  if (Number.isNaN(parsed.getTime())) return date
  return parsed.toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" })
}

/** One entry of DocxAnnotationList: a comment or a tracked change. */
export type DocxAnnotation =
  | { type: "comment"; comment: DocxComment }
  | { type: "change"; change: DocxTrackedChange }

/**
 * A document's comments and tracked changes, as a list of cards in body order: for a panel beside the pages.
 * Choosing one calls onSelect with it (the viewer then shows its place).
 */
export function DocxAnnotationList({
  comments = [],
  changes = [],
  active,
  onSelect,
  empty = "No comments or changes",
  className,
}: {
  comments?: DocxComment[]
  changes?: DocxTrackedChange[]
  /** The id of the chosen one. */
  active?: string
  onSelect?: (annotation: DocxAnnotation) => void
  empty?: React.ReactNode
  className?: string
}) {
  const all: (DocxAnnotation & { id: string; nodeIndex: number })[] = [
    ...comments.map((comment) => ({ type: "comment" as const, comment, id: comment.id, nodeIndex: comment.nodeIndex })),
    ...changes.map((change) => ({ type: "change" as const, change, id: change.id, nodeIndex: change.nodeIndex })),
  ].sort((a, b) => a.nodeIndex - b.nodeIndex)
  if (!all.length) return <div className="nt-docx-sidebar-empty">{empty}</div>
  return (
    <div className={cn("nt-docx-annotations", className)}>
      {all.map((entry) =>
        entry.type === "comment" ? (
          <DocxAnnotationCard
            key={`c-${entry.id}`}
            kind={commentKind(entry.comment)}
            author={entry.comment.author}
            date={formatAnnotationDate(entry.comment.date)}
            anchorText={entry.comment.anchorText}
            text={entry.comment.text}
            active={active === entry.id}
            onClick={onSelect ? () => onSelect(entry) : undefined}
          />
        ) : (
          <DocxAnnotationCard
            key={`t-${entry.id}`}
            kind={entry.change.kind}
            author={entry.change.author}
            date={formatAnnotationDate(entry.change.date)}
            text={entry.change.text ?? ""}
            active={active === entry.id}
            onClick={onSelect ? () => onSelect(entry) : undefined}
          />
        )
      )}
    </div>
  )
}
