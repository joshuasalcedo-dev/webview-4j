import * as React from "react"

import { Avatar } from "../avatar"
import { useContextMenu } from "../context-menu"
import { useDialogs } from "../dialog"
import { FileChip } from "../files"
import { Inspector, InspectorSection } from "../inspector"
import { Pill } from "../pill"
import { SegmentedControl } from "../segmented-control"
import { Link, Text } from "../text"
import { TextArea } from "../text-inputs"
import { askMuteFor, mutedWords } from "./capabilities"
import { useChat } from "./chat-provider"
import "./chat.css"
import { tagEntries, TagPills } from "./conversation-list"
import {
  BellIcon,
  BlockIcon,
  FlagIcon,
  MutedIcon,
  PhoneIcon,
  PinIcon,
  SearchIcon,
  TagIcon,
  TrashIcon,
} from "./icons"
import { formatLastSeen, formatListTime } from "./time"
import type {
  ChatAttachment,
  ChatMessage,
  ChatTag,
  Conversation,
} from "./types"

/** Which of the usual parts show (all by default). */
type ChatDetailsParts = {
  profile?: boolean
  tags?: boolean
  note?: boolean
  shared?: boolean
  pinned?: boolean
  danger?: boolean
}

type ChatDetailsProps = {
  open: boolean
  onOpenChange?: (open: boolean) => void
  conversation: Conversation
  /** The conversation's messages (for what was shared and what is pinned). */
  messages?: ChatMessage[]
  /** The private note about this person, and where it is kept (autosaved as it is typed). */
  note?: string
  onNoteChange?: (note: string) => void | Promise<void>
  onTagsChange?: (tags: ChatTag[]) => void
  onSearch?: () => void
  onCall?: () => void
  onMuteChange?: (muted: boolean, until?: Date) => void
  onBlock?: () => void
  onReport?: () => void
  onDelete?: () => void
  /** A pinned message was chosen (e.g. to scroll to it). */
  onJumpTo?: (messageId: string) => void
  /** The app's own sections (e.g. a subscription with its plan and tips), after the profile. */
  sections?: React.ReactNode
  parts?: ChatDetailsParts
  storageKey?: string
  /** Narrower than this (px), the panel lies over the conversation as a sheet (720 by default). */
  overlayBelow?: number
}

/** The note, saved a moment after typing stops, with a quiet "Saved". */
function NoteField({
  value,
  onChange,
}: {
  value: string
  onChange: (note: string) => void | Promise<void>
}) {
  const [text, setText] = React.useState(value)
  const [state, setState] = React.useState<
    "idle" | "saving" | "saved" | "failed"
  >("idle")
  const timer = React.useRef<number | undefined>(undefined)
  React.useEffect(() => () => window.clearTimeout(timer.current), [])
  return (
    <TextArea
      aria-label="Private note"
      placeholder="Only you can see this."
      autoGrow
      minRows={2}
      maxRows={8}
      value={text}
      hint={
        state === "saving" ? "Saving…" : state === "saved" ? "Saved" : undefined
      }
      error={
        state === "failed"
          ? "Not saved. Check the connection; it saves again when you type."
          : undefined
      }
      onChange={(event) => {
        const next = event.target.value
        setText(next)
        setState("idle")
        window.clearTimeout(timer.current)
        timer.current = window.setTimeout(() => {
          setState("saving")
          Promise.resolve(onChange(next))
            .then(() => setState("saved"))
            .catch(() => setState("failed"))
        }, 700)
      }}
    />
  )
}

/** What was shared in the conversation: photos, files, links. */
function Shared({ messages }: { messages: ChatMessage[] }) {
  const [tab, setTab] = React.useState<"photos" | "files" | "links">("photos")
  const all = messages
    .filter((m) => !m.paywall || m.paywall.unlocked)
    .flatMap((m) => m.attachments ?? [])
  const photos = all.filter(
    (a): a is Extract<ChatAttachment, { kind: "image" }> => a.kind === "image"
  )
  const files = all.filter(
    (a): a is Extract<ChatAttachment, { kind: "file" }> => a.kind === "file"
  )
  const links = messages.flatMap((m) => (m.link ? [m.link] : []))
  return (
    <div className="nt-details-shared">
      <SegmentedControl
        aria-label="Shared"
        value={tab}
        onValueChange={setTab}
        segments={[
          { value: "photos", label: `Photos ${photos.length}` },
          { value: "files", label: `Files ${files.length}` },
          { value: "links", label: `Links ${links.length}` },
        ]}
      />
      {tab === "photos" &&
        (photos.length ? (
          <div className="nt-details-photos">
            {photos.map((p, i) => (
              <img
                key={p.url + i}
                src={p.url}
                alt={p.name ?? ""}
                loading="lazy"
              />
            ))}
          </div>
        ) : (
          <Text tone="secondary">No photos yet.</Text>
        ))}
      {tab === "files" &&
        (files.length ? (
          <div className="nt-details-files">
            {files.map((f, i) => (
              <FileChip key={f.name + i} name={f.name} size={f.size} />
            ))}
          </div>
        ) : (
          <Text tone="secondary">No files yet.</Text>
        ))}
      {tab === "links" &&
        (links.length ? (
          <ul className="nt-details-links">
            {links.map((l) => (
              <li key={l.url}>
                <Link href={l.url} external>
                  {l.title}
                </Link>
              </li>
            ))}
          </ul>
        ) : (
          <Text tone="secondary">No links yet.</Text>
        ))}
    </div>
  )
}

/**
 * The details of a conversation, on the Inspector: who it is with (picture, name, status, quick actions), its
 * tags, a private note, what was shared, pinned messages, and Block, Report and Delete at the bottom (each
 * asked first). Apps add their own sections; `parts` hides the usual ones.
 */
function ChatDetails({
  open,
  onOpenChange,
  conversation: c,
  messages = [],
  note,
  onNoteChange,
  onTagsChange,
  onSearch,
  onCall,
  onMuteChange,
  onBlock,
  onReport,
  onDelete,
  onJumpTo,
  sections,
  parts = {},
  storageKey = "chat-details",
  overlayBelow,
}: ChatDetailsProps) {
  const chat = useChat()
  const dialogs = useDialogs()
  const show = {
    profile: true,
    tags: true,
    note: true,
    shared: true,
    pinned: true,
    danger: true,
    ...parts,
  }
  const person = c.people?.[0]
  const status = c.online
    ? "Online"
    : person?.lastSeen
      ? formatLastSeen(person.lastSeen)
      : c.group
        ? `${(c.people?.length ?? 0) + 1} people`
        : undefined
  const pinned = messages.filter((m) => m.pinned)
  const tagsMenu = useContextMenu(() =>
    onTagsChange
      ? tagEntries(
          c,
          chat.pillTypes ?? [],
          !!chat.allowCustomTags,
          onTagsChange,
          dialogs
        ).filter((e, i) => !(i === 0 && e === "separator"))
      : []
  )
  const confirmThen = async (
    title: string,
    message: string,
    confirm: string,
    run?: () => void
  ) => {
    if (!run) return
    if (
      await dialogs.confirm({
        title,
        message,
        kind: "warning",
        confirm,
        destructive: true,
      })
    )
      run()
  }
  const mute = async () => {
    const change = onMuteChange ?? chat.actions.onMuteChange
    if (!change) return
    if (c.muted) return change(false)
    const until = await askMuteFor(dialogs)
    if (until !== null) change(true, until)
  }

  return (
    <Inspector
      open={open}
      onOpenChange={onOpenChange}
      title="Details"
      storageKey={storageKey}
      overlayBelow={overlayBelow}
    >
      {show.profile && (
        <div className="nt-details-profile">
          <span className="nt-conv-avatar">
            <Avatar name={c.title} src={c.avatar} size={72} />
            {c.online && <span className="nt-online-dot" aria-hidden="true" />}
          </span>
          <Text variant="subtitle" as="h3">
            {c.title}
          </Text>
          {c.subtitle && <Text tone="secondary">{c.subtitle}</Text>}
          {status && (
            <Text variant="caption" tone={c.online ? "success" : "secondary"}>
              {status}
            </Text>
          )}
          <div className="nt-details-actions">
            {onSearch && (
              <button type="button" onClick={onSearch}>
                <SearchIcon />
                Search
              </button>
            )}
            {chat.can("mute") &&
              (onMuteChange ?? chat.actions.onMuteChange) && (
                <button
                  type="button"
                  aria-pressed={!!c.muted}
                  title={c.muted ? mutedWords(c) : undefined}
                  onClick={() => void mute()}
                >
                  {c.muted ? <MutedIcon /> : <BellIcon />}
                  {c.muted ? "Unmute" : "Mute"}
                </button>
              )}
            {onCall && chat.can("call") && (
              <button type="button" onClick={onCall}>
                <PhoneIcon />
                Call
              </button>
            )}
          </div>
        </div>
      )}
      {sections}
      {show.tags && (
        <InspectorSection
          title="Tags"
          count={c.tags?.length}
          action={
            onTagsChange && chat.can("addTags") ? (
              <button
                type="button"
                className="nt-details-small"
                aria-haspopup="menu"
                onClick={tagsMenu.onContextMenu}
              >
                <TagIcon /> Edit
              </button>
            ) : undefined
          }
        >
          {c.tags?.length ? (
            <div className="nt-details-tags">
              {onTagsChange && chat.can("addTags") ? (
                c.tags.map((t) => (
                  <Pill
                    key={t.id}
                    color={
                      chat.pillTypes?.find((p) => p.id === t.id)?.colour ??
                      t.color ??
                      "grey"
                    }
                    size="medium"
                    onRemove={
                      chat.pillTypes?.find((p) => p.id === t.id)?.editable ===
                      false
                        ? undefined
                        : () =>
                            onTagsChange(
                              c.tags?.filter((x) => x.id !== t.id) ?? []
                            )
                    }
                  >
                    {t.label}
                  </Pill>
                ))
              ) : (
                <TagPills tags={c.tags} max={20} />
              )}
            </div>
          ) : (
            <Text tone="secondary">No tags.</Text>
          )}
        </InspectorSection>
      )}
      {show.note && onNoteChange && (
        <InspectorSection title="Note">
          <NoteField value={note ?? ""} onChange={onNoteChange} />
        </InspectorSection>
      )}
      {show.shared && (
        <InspectorSection title="Shared">
          <Shared messages={messages} />
        </InspectorSection>
      )}
      {show.pinned && (
        <InspectorSection title="Pinned" count={pinned.length}>
          {pinned.length ? (
            <ul className="nt-details-pinned">
              {pinned.map((m) => (
                <li key={m.id}>
                  <button type="button" onClick={() => onJumpTo?.(m.id)}>
                    <PinIcon />
                    <span className="nt-details-pinned-text">
                      {m.text ?? "Attachment"}
                    </span>
                    <span className="nt-details-pinned-time">
                      {formatListTime(m.sentAt)}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          ) : (
            <Text tone="secondary">Nothing pinned.</Text>
          )}
        </InspectorSection>
      )}
      {show.danger && (onBlock || onReport || onDelete) && (
        <div className="nt-details-danger">
          {onBlock && chat.can("block") && (
            <button
              type="button"
              onClick={() =>
                void confirmThen(
                  `Block ${c.title}?`,
                  "They won't be able to send you messages. You can unblock them later.",
                  "Block",
                  onBlock
                )
              }
            >
              <BlockIcon /> Block
            </button>
          )}
          {onReport && chat.can("report") && (
            <button
              type="button"
              onClick={() =>
                void confirmThen(
                  `Report ${c.title}?`,
                  "The last messages are sent with the report for review.",
                  "Report",
                  onReport
                )
              }
            >
              <FlagIcon /> Report
            </button>
          )}
          {onDelete && (
            <button
              type="button"
              onClick={() =>
                void confirmThen(
                  `Delete the chat with ${c.title}?`,
                  "Its messages are deleted from this device. This can't be undone.",
                  "Delete",
                  onDelete
                )
              }
            >
              <TrashIcon /> Delete chat
            </button>
          )}
        </div>
      )}
    </Inspector>
  )
}

export { ChatDetails }
export type { ChatDetailsParts, ChatDetailsProps }
