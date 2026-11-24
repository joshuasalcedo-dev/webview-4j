import * as React from "react"

import { useFitCount } from "../../lib/fit"
import { cn } from "../../lib/utils"
import { Avatar } from "../avatar"
import { type MenuEntry, useContextMenu } from "../context-menu"
import { type DialogsApi, useDialogs } from "../dialog"
import { Pill, PillGroup } from "../pill"
import { useNative } from "../provider"
import { SearchField } from "../search-field"
import { SegmentedControl } from "../segmented-control"
import { type SwipeAction, Swipeable } from "../swipeable"
import { muteMenuItem, mutedWords } from "./capabilities"
import { useChat } from "./chat-provider"
import "./chat.css"
import type { ChatCategory, PillType } from "./config"
import {
  ArchiveIcon,
  FileIconSmall,
  MarkUnreadIcon,
  MicIcon,
  MutedIcon,
  PhotoIcon,
  PinIcon,
  TrashIcon,
} from "./icons"
import { formatAmount, formatListTime } from "./time"
import type { ChatTag, Conversation } from "./types"

/** What can be done to a conversation (its menu, its swipes); each one given adds its item. */
type ConversationActions = {
  onPin?: (id: string, pinned: boolean) => void
  onMarkUnread?: (id: string, unread: boolean) => void
  /** Muted (until a time, or until turned back on) or not; the menu asks how long. */
  onMute?: (id: string, muted: boolean, until?: Date) => void
  onArchive?: (id: string) => void
  /** Asked first ("Delete this chat?"), then called. */
  onDelete?: (id: string) => void
  /** The conversation's tags changed (the menu's tags, or "Add tag…"). */
  onTagsChange?: (id: string, tags: ChatTag[]) => void
  /** More items, before Archive and Delete (ChatConfig.menus.conversation can change the whole menu). */
  menu?: (conversation: Conversation) => MenuEntry[]
}

const allowed = (
  c: Conversation,
  what: "pin" | "mute" | "addTags" | "delete"
) => c.capabilities?.[what] !== false

/** A tag as its pill type says it looks (or as itself, for a type the app doesn't list). */
const asTag = (type: PillType): ChatTag => ({
  id: type.id,
  label: type.label,
  color: type.colour,
  icon: type.icon,
})

/** The tags part of a conversation's menu: the editable pill types, grouped; exclusive groups pick one. */
function tagEntries(
  c: Conversation,
  types: PillType[],
  allowCustom: boolean,
  change: (tags: ChatTag[]) => void,
  dialogs: DialogsApi
): MenuEntry[] {
  const mine = c.tags ?? []
  const has = (id: string) => mine.some((t) => t.id === id)
  const editable = types.filter((t) => t.editable !== false)
  const entries: MenuEntry[] = []
  const free = editable.filter((t) => !t.exclusive)
  const groups = [
    ...new Set(
      editable.filter((t) => t.exclusive).map((t) => t.exclusive as string)
    ),
  ]
  if (free.length + groups.length === 0 && !allowCustom) return []
  entries.push("separator", { label: "Tags", disabled: true })
  for (const type of free) {
    entries.push({
      label: type.label,
      checked: has(type.id),
      onSelect: () =>
        change(
          has(type.id)
            ? mine.filter((t) => t.id !== type.id)
            : [...mine, asTag(type)]
        ),
    })
  }
  for (const group of groups) {
    entries.push("separator")
    for (const type of editable.filter((t) => t.exclusive === group)) {
      // Picking one of the group takes the others away; picking the one already there takes it away.
      const others = types.filter((t) => t.exclusive === group).map((t) => t.id)
      entries.push({
        label: type.label,
        checked: has(type.id),
        onSelect: () =>
          change(
            has(type.id)
              ? mine.filter((t) => t.id !== type.id)
              : [...mine.filter((t) => !others.includes(t.id)), asTag(type)]
          ),
      })
    }
  }
  if (allowCustom)
    entries.push({
      label: "Add tag…",
      onSelect: async () => {
        const label = await dialogs.prompt({
          title: `Add a tag to ${c.title}`,
          label: "Tag",
          placeholder: "e.g. Close friend",
          confirm: "Add",
          validate: (v) =>
            v.trim() === ""
              ? "Type a name for the tag."
              : v.length > 24
                ? "Keep it under 24 characters."
                : undefined,
        })
        if (label)
          change([
            ...mine,
            {
              id: label.trim().toLowerCase(),
              label: label.trim(),
              color: "grey",
            },
          ])
      },
    })
  return entries
}

/** A conversation's right-click menu, in the systems' usual order. */
function conversationMenu(
  c: Conversation,
  actions: ConversationActions,
  dialogs: DialogsApi,
  types: PillType[],
  allowCustom: boolean
): MenuEntry[] {
  const unread = (c.unread ?? 0) > 0 || !!c.markedUnread
  const entries: MenuEntry[] = []
  if (actions.onPin && allowed(c, "pin"))
    entries.push({
      label: c.pinned ? "Unpin" : "Pin",
      onSelect: () => actions.onPin?.(c.id, !c.pinned),
    })
  if (actions.onMarkUnread)
    entries.push({
      label: unread ? "Mark as read" : "Mark as unread",
      onSelect: () => actions.onMarkUnread?.(c.id, !unread),
    })
  entries.push(
    ...muteMenuItem(
      !!c.muted,
      actions.onMute &&
        ((muted, until) => actions.onMute?.(c.id, muted, until)),
      dialogs,
      allowed(c, "mute")
    )
  )
  if (actions.onTagsChange && allowed(c, "addTags"))
    entries.push(
      ...tagEntries(
        c,
        types,
        allowCustom,
        (tags) => actions.onTagsChange?.(c.id, tags),
        dialogs
      )
    )
  const extra = actions.menu?.(c) ?? []
  if (extra.length > 0) entries.push("separator", ...extra)
  if (actions.onArchive || (actions.onDelete && allowed(c, "delete")))
    entries.push("separator")
  if (actions.onArchive)
    entries.push({
      label: c.archived ? "Unarchive" : "Archive",
      onSelect: () => actions.onArchive?.(c.id),
    })
  if (actions.onDelete && allowed(c, "delete"))
    entries.push({
      label: "Delete chat…",
      onSelect: () => void confirmDelete(c, dialogs, actions.onDelete),
    })
  return entries
}

async function confirmDelete(
  c: Conversation,
  dialogs: DialogsApi,
  onDelete?: (id: string) => void
) {
  const sure = await dialogs.confirm({
    title: `Delete the chat with ${c.title}?`,
    message: "Its messages are deleted from this device. This can't be undone.",
    kind: "warning",
    confirm: "Delete",
    destructive: true,
  })
  if (sure) onDelete?.(c.id)
}

const ATTACHMENT: Record<
  "photo" | "file" | "voice",
  { icon: React.ReactNode; word: string }
> = {
  photo: { icon: <PhotoIcon />, word: "Photo" },
  file: { icon: <FileIconSmall />, word: "File" },
  voice: { icon: <MicIcon />, word: "Voice message" },
}

/** The line under a conversation's name: typing…, a draft, or the last message ("You: ", a photo). */
function Preview({ c }: { c: Conversation }) {
  const chat = useChat()
  if (c.typing) {
    return (
      <span className="nt-conv-preview" data-typing="">
        {typeof c.typing === "string"
          ? chat.say("someoneTyping", { name: c.typing })
          : chat.say("typing")}
      </span>
    )
  }
  if (c.draft) {
    return (
      <span className="nt-conv-preview">
        <span className="nt-conv-draft">Draft:</span> {c.draft}
      </span>
    )
  }
  const last = c.lastMessage
  if (!last) return <span className="nt-conv-preview">No messages yet</span>
  if (last.attachment === "tip" && last.tip) {
    return (
      <span className="nt-conv-preview" data-tip="">
        {last.fromMe && <span className="nt-conv-you">You: </span>}
        {chat.say("tipPreview", {
          amount: formatAmount(last.tip.amount, last.tip.currency),
        })}
      </span>
    )
  }
  const attachment =
    last.attachment && last.attachment !== "tip"
      ? ATTACHMENT[last.attachment]
      : undefined
  return (
    <span className="nt-conv-preview">
      {last.fromMe && <span className="nt-conv-you">You: </span>}
      {attachment && (
        <span className="nt-conv-attachment">{attachment.icon}</span>
      )}
      {last.text || attachment?.word}
    </span>
  )
}

/** A conversation's tags as pills, in the order of the pill types, looking as their type says ("+N" past max). */
function TagPills({
  tags,
  max = 2,
  types,
}: {
  tags?: ChatTag[]
  max?: number
  types?: PillType[]
}) {
  const chat = useChat()
  const known = types ?? chat.pillTypes ?? []
  if (!tags || tags.length === 0) return null
  const order = (t: ChatTag) => {
    const at = known.findIndex((k) => k.id === t.id)
    return at < 0 ? known.length : at
  }
  const sorted = [...tags].sort((a, b) => order(a) - order(b))
  return (
    <PillGroup fit max={max} className="nt-conv-tags">
      {sorted.map((t) => {
        const type = known.find((k) => k.id === t.id)
        const icon = type?.icon ?? t.icon
        const iconOnly = !!type?.iconOnly && !!icon
        return (
          <Pill
            key={t.id}
            color={type?.colour ?? t.color ?? "grey"}
            icon={icon}
            label={iconOnly ? (type?.label ?? t.label) : undefined}
          >
            {iconOnly ? undefined : (type?.label ?? t.label)}
          </Pill>
        )
      })}
    </PillGroup>
  )
}

type ConversationRowProps = ConversationActions & {
  conversation: Conversation
  selected?: boolean
  onSelect?: (id: string) => void
  /** The swipes: on by default (right: read/unread; left: archive, delete), or the config's. */
  swipe?: boolean
}

/**
 * One conversation: the picture with an online dot, the name with pinned and muted marks and its pills, the
 * time of the last message, the line under it, and the unread count (grey when muted). Right-click for its
 * menu; drag it sideways for its swipe actions.
 */
function ConversationRow({
  conversation: c,
  selected = false,
  onSelect,
  swipe = true,
  ...actions
}: ConversationRowProps) {
  const dialogs = useDialogs()
  const chat = useChat()
  const { platform } = useNative()
  const unread = (c.unread ?? 0) > 0 || !!c.markedUnread
  const count = c.unread ?? 0
  const types = chat.pillTypes ?? []
  const { onContextMenu } = useContextMenu(() => {
    const defaults = conversationMenu(
      c,
      actions,
      dialogs,
      types,
      !!chat.allowCustomTags
    )
    return chat.menus?.conversation
      ? chat.menus.conversation(defaults, c)
      : defaults
  })

  const on = swipe && chat.features.swipe
  const defaultRight: SwipeAction[] = actions.onMarkUnread
    ? [
        {
          label: unread ? "Read" : "Unread",
          icon: <MarkUnreadIcon />,
          tone: "accent",
          onAction: () => actions.onMarkUnread?.(c.id, !unread),
        },
      ]
    : []
  const defaultLeft: SwipeAction[] = [
    ...(actions.onArchive
      ? [
          {
            label: c.archived ? "Unarchive" : "Archive",
            icon: <ArchiveIcon />,
            tone: "warning" as const,
            onAction: () => actions.onArchive?.(c.id),
          },
        ]
      : []),
    ...(actions.onDelete && allowed(c, "delete")
      ? [
          {
            label: "Delete",
            icon: <TrashIcon />,
            tone: "error" as const,
            onAction: () => void confirmDelete(c, dialogs, actions.onDelete),
          },
        ]
      : []),
  ]
  const swipeRight = on ? (chat.swipe?.right?.(c) ?? defaultRight) : []
  const swipeLeft = on ? (chat.swipe?.left?.(c) ?? defaultLeft) : []

  return (
    <Swipeable
      as="li"
      role="none"
      className="nt-conv-swipe"
      swipeRight={swipeRight}
      swipeLeft={swipeLeft}
    >
      <div
        role="option"
        aria-selected={selected}
        tabIndex={0}
        data-leading="true"
        data-unread={unread || undefined}
        className="nt-list-item nt-conv"
        onClick={() => onSelect?.(c.id)}
        onContextMenu={onContextMenu}
        onKeyDown={(event) => {
          if (event.key === "Enter" || event.key === " ") {
            event.preventDefault()
            onSelect?.(c.id)
          }
        }}
      >
        <span className="nt-conv-avatar">
          <Avatar name={c.title} src={c.avatar} size={40} />
          {c.online && (
            <span className="nt-online-dot" aria-label="Online" role="img" />
          )}
        </span>
        <span className="nt-list-item-text">
          <span className="nt-list-item-title nt-conv-title">
            <span className="nt-conv-name">{c.title}</span>
            {c.pinned && (
              <span className="nt-conv-mark" title="Pinned" aria-label="Pinned">
                <PinIcon />
              </span>
            )}
            {c.muted && (
              <span
                className="nt-conv-mark"
                title={mutedWords({ muted: true, mutedUntil: c.mutedUntil })}
                aria-label="Muted"
              >
                <MutedIcon />
              </span>
            )}
            <TagPills
              tags={c.tags}
              max={platform === "mac" ? 2 : 3}
              types={types}
            />
          </span>
          <span className="nt-list-item-subtitle">
            <Preview c={c} />
          </span>
        </span>
        <span className="nt-list-item-trailing nt-conv-side">
          {c.lastMessage && (
            <span className="nt-conv-time">
              {formatListTime(c.lastMessage.at)}
            </span>
          )}
          {count > 0 ? (
            <span
              className="nt-badge"
              data-muted={c.muted || undefined}
              aria-label={`${count} unread`}
            >
              {count > 99 ? "99+" : count}
            </span>
          ) : c.markedUnread ? (
            <span
              className="nt-badge nt-badge-dot"
              data-muted={c.muted || undefined}
              aria-label="Unread"
            />
          ) : null}
        </span>
      </div>
    </Swipeable>
  )
}

/**
 * The filters above the list: a Mac's segmented control, pill toggles on Windows. They stay on one line:
 * what doesn't fit (measured as the list resizes) goes in "More ▾". Each can show how many it holds.
 */
function CategoryBar({
  categories,
  value,
  onChange,
  counts,
}: {
  categories: ChatCategory[]
  value: string
  onChange: (id: string) => void
  counts: Record<string, number>
}) {
  const { platform } = useNative()
  const label = (c: ChatCategory) =>
    c.count && counts[c.id] ? `${c.label} ${counts[c.id]}` : c.label
  const {
    box,
    measure,
    count: fits,
  } = useFitCount<HTMLDivElement, HTMLSpanElement>(
    `${platform}:${categories.map(label).join("|")}`
  )
  const count = Math.max(1, Math.min(fits, categories.length))
  const head = categories.slice(0, count)
  const rest = categories.slice(count)
  const chosenRest = rest.find((c) => c.id === value)
  const { onContextMenu } = useContextMenu(() =>
    rest.map((c) => ({
      label: label(c),
      checked: c.id === value,
      onSelect: () => onChange(c.id),
    }))
  )
  const more = rest.length > 0 && (
    <button
      type="button"
      className="nt-category-more"
      data-on={chosenRest ? "" : undefined}
      aria-haspopup="menu"
      onClick={onContextMenu}
    >
      {chosenRest ? label(chosenRest) : "More"} ▾
    </button>
  )
  // The hidden ruler: every filter as wide as it is drawn, then the More button.
  const ruler = (
    <span
      ref={measure}
      className="nt-category-ruler"
      data-platform-bar={platform}
      aria-hidden="true"
    >
      {categories.map((c) => (
        <span
          key={c.id}
          className={platform === "mac" ? "nt-category-ruler-seg" : "nt-pill"}
          data-size="medium"
        >
          {label(c)}
        </span>
      ))}
      <span className="nt-category-more">More ▾</span>
    </span>
  )
  if (platform === "mac") {
    return (
      <div ref={box} className="nt-convlist-filters" data-platform-bar="mac">
        {ruler}
        <SegmentedControl
          aria-label="Show"
          value={chosenRest ? "" : value}
          onValueChange={onChange}
          segments={head.map((c) => ({ value: c.id, label: label(c) }))}
        />
        {more}
      </div>
    )
  }
  return (
    <div
      ref={box}
      className="nt-convlist-filters"
      role="radiogroup"
      aria-label="Show"
    >
      {ruler}
      {head.map((c) => (
        <Pill
          key={c.id}
          role="radio"
          aria-checked={c.id === value}
          color="accent"
          size="medium"
          icon={c.icon}
          pressed={c.id === value}
          onPressedChange={() => onChange(c.id)}
        >
          {c.label}
          {c.count && counts[c.id] ? (
            <span className="nt-category-count">{counts[c.id]}</span>
          ) : null}
        </Pill>
      ))}
      {more}
    </div>
  )
}

type ConversationListProps = Omit<React.ComponentProps<"div">, "onSelect"> &
  ConversationActions & {
    conversations: Conversation[]
    selectedId?: string
    onSelect?: (id: string) => void
    /** Shows a search box that filters by name, last message and tag. True by default (and features.search). */
    search?: boolean
    /** The filters above the list (or ChatConfig.categories). */
    categories?: ChatCategory[]
    /** The chosen filter (controlled); left out, the list keeps its own. */
    category?: string
    onCategoryChange?: (id: string) => void
    /** Shown when there are no conversations at all. */
    emptyText?: React.ReactNode
    /** Something above the list, beside the search (e.g. a "New chat" button). */
    header?: React.ReactNode
    /** Swipe actions on the rows. True by default (and features.swipe). */
    swipe?: boolean
  }

/**
 * The column of conversations: a search box, the category filters, then the rows, pinned ones first. ↑ and ↓
 * move between conversations; Enter opens one; right-click or swipe a row for what can be done to it. Kinds of
 * tag, categories, menus and swipes come from ChatConfig (props win).
 */
function ConversationList({
  conversations,
  selectedId,
  onSelect,
  search = true,
  categories,
  category,
  onCategoryChange,
  emptyText = "No conversations yet.",
  header,
  swipe = true,
  onPin,
  onMarkUnread,
  onMute,
  onArchive,
  onDelete,
  onTagsChange,
  menu,
  className,
  ...props
}: ConversationListProps) {
  const chat = useChat()
  const [find, setFind] = React.useState("")
  const cats = React.useMemo(
    () => categories ?? chat.categories ?? [],
    [categories, chat.categories]
  )
  const [ownCategory, setOwnCategory] = React.useState(cats[0]?.id ?? "")
  const current = category ?? ownCategory
  const chooseCategory = (id: string) => {
    if (category === undefined) setOwnCategory(id)
    onCategoryChange?.(id)
  }
  const tagsChange =
    onTagsChange ??
    (chat.actions.onTagsChange &&
      ((id: string, tags: ChatTag[]) => {
        const c = conversations.find((x) => x.id === id)
        if (c) chat.actions.onTagsChange?.(c, tags)
      }))
  const actions = {
    onPin,
    onMarkUnread,
    onMute,
    onArchive,
    onDelete,
    onTagsChange: tagsChange,
    menu,
  }

  const counts = React.useMemo(() => {
    const result: Record<string, number> = {}
    for (const c of cats)
      if (c.count && c.match)
        result[c.id] = conversations.filter(c.match).length
    return result
  }, [cats, conversations])

  const shown = React.useMemo(() => {
    const words = find.toLowerCase().split(/\s+/).filter(Boolean)
    const chosen = cats.find((c) => c.id === current)
    const matching = conversations.filter((c) => {
      // Archived ones show only in a category that asks for them.
      if (c.archived && current !== "archived") return false
      if (chosen?.match && !chosen.match(c)) return false
      if (words.length === 0) return true
      const text =
        `${c.title} ${c.lastMessage?.text ?? ""} ${(c.tags ?? []).map((t) => t.label).join(" ")}`.toLowerCase()
      return words.every((w) => text.includes(w))
    })
    // Pinned first, keeping the order given otherwise.
    return [
      ...matching.filter((c) => c.pinned),
      ...matching.filter((c) => !c.pinned),
    ]
  }, [conversations, find, cats, current])

  const searching = search && chat.features.search
  return (
    <div className={cn("nt-convlist", className)} {...props}>
      {(searching || header) && (
        <div className="nt-convlist-head">
          {searching && (
            <SearchField
              aria-label="Search conversations"
              placeholder="Search"
              value={find}
              onChange={(event) => setFind(event.target.value)}
            />
          )}
          {header}
        </div>
      )}
      {cats.length > 1 && (
        <CategoryBar
          categories={cats}
          value={current}
          onChange={chooseCategory}
          counts={counts}
        />
      )}
      {shown.length === 0 ? (
        <div className="nt-chat-empty">
          {find || (current && current !== cats[0]?.id)
            ? "No conversations match."
            : emptyText}
        </div>
      ) : (
        <ul
          role="listbox"
          aria-label="Conversations"
          className="nt-list nt-convlist-rows"
          onKeyDown={(event) => {
            if (event.key !== "ArrowDown" && event.key !== "ArrowUp") return
            const rows = Array.from(
              event.currentTarget.querySelectorAll<HTMLElement>("[role=option]")
            )
            const at = rows.indexOf(document.activeElement as HTMLElement)
            const next =
              rows[
                Math.max(
                  0,
                  Math.min(
                    rows.length - 1,
                    at + (event.key === "ArrowDown" ? 1 : -1)
                  )
                )
              ]
            if (next) {
              event.preventDefault()
              next.focus()
            }
          }}
        >
          {shown.map((c) => (
            <ConversationRow
              key={c.id}
              conversation={c}
              selected={c.id === selectedId}
              onSelect={onSelect}
              swipe={swipe}
              {...actions}
            />
          ))}
        </ul>
      )}
    </div>
  )
}

export {
  CategoryBar,
  ConversationList,
  conversationMenu,
  ConversationRow,
  TagPills,
  tagEntries,
}
export type { ConversationActions, ConversationListProps, ConversationRowProps }
