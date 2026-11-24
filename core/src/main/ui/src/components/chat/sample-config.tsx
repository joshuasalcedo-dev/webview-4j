// The stories' chat: a seed for the memory adapter and a configuration, so every story runs the same way an
// app would (adapter + config). Made-up people; pictures drawn as SVG (nothing from the web).
import type { ChatComposerTool, GifResult } from "../composer-toolbar"
import {
  chatCategories,
  type ChatConfig,
  defineChatConfig,
  type PillType,
} from "./config"
import { createMemoryChatAdapter, type MemoryOptions } from "./memory-adapter"
import {
  olderMessages,
  people,
  picture,
  sampleConversations,
  sampleMessages,
} from "./sample"
import { formatAmount } from "./time"
import type { ChatMessage, Conversation } from "./types"

const Crown = () => (
  <svg viewBox="0 0 12 12" fill="currentColor" aria-hidden="true">
    <path d="M1.5 4 4 6l2-4 2 4 2.5-2-1 5.5h-7z" />
  </svg>
)
const Dollar = () => (
  <svg
    viewBox="0 0 12 12"
    fill="none"
    stroke="currentColor"
    strokeWidth="1.3"
    strokeLinecap="round"
    aria-hidden="true"
  >
    <path d="M6 1v10M8.5 3.5C8 2.8 7.1 2.5 6 2.5 4.6 2.5 3.5 3.2 3.5 4.3S4.6 5.8 6 6s2.5.8 2.5 1.8S7.4 9.5 6 9.5c-1.2 0-2.1-.4-2.6-1.1" />
  </svg>
)

/** The kinds of tag: two free ones, a status group (one at a time), and "Tipped", set by the app only. */
const pillTypes: PillType[] = [
  { id: "vip", label: "VIP", colour: "purple", icon: <Crown /> },
  {
    id: "tipped",
    label: "Tipped",
    colour: "green",
    icon: <Dollar />,
    iconOnly: true,
    editable: false,
  },
  { id: "new", label: "New", colour: "blue", exclusive: "status" },
  { id: "regular", label: "Regular", colour: "teal", exclusive: "status" },
  { id: "lapsed", label: "Lapsed", colour: "orange", exclusive: "status" },
  { id: "custom-work", label: "Work", colour: "grey" },
]

const tag = (id: string) => {
  const t = pillTypes.find((p) => p.id === id)
  return { id, label: t?.label ?? id, color: t?.colour }
}

const ago = (minutes: number) => new Date(Date.now() - minutes * 60_000)

/** Conversations with people, tags and groups (for presence, categories and pills). */
function seedConversations(): Conversation[] {
  const base = sampleConversations()
  const extra: Record<string, Partial<Conversation>> = {
    ana: {
      people: [people.ana],
      tags: [tag("vip"), tag("tipped"), tag("regular")],
      subtitle: "@ana.reyes · subscriber since March",
      unread: 0,
    },
    marco: { people: [people.marco], tags: [tag("new")] },
    kim: { people: [people.kim], tags: [tag("lapsed")] },
    team: {
      group: true,
      people: [people.lea, people.marco, people.kim],
      tags: [tag("custom-work")],
    },
    lea: { people: [people.lea], tags: [tag("vip"), tag("regular")] },
    ben: { people: [{ id: "ben", name: "Ben Ortiz" }] },
    sara: { people: [{ id: "sara", name: "Sara Diaz" }], archived: false },
  }
  return base.map((c) => ({ ...c, typing: undefined, ...extra[c.id] }))
}

/** Ana's messages, plus tips, a paywalled set and a message with things below it. */
function seedAnaMessages(): ChatMessage[] {
  const older = olderMessages(new Date(Date.now() - 4 * 86_400_000), 0)
  const main = sampleMessages().filter((m) => m.id !== "m14")
  return [
    ...older,
    ...main,
    {
      id: "tip-in",
      senderId: "ana",
      sentAt: ago(30),
      attachments: [
        {
          kind: "tip",
          amount: 50000,
          currency: "PHP",
          note: "For the beach set. Worth every peso!",
        },
      ],
    },
    {
      id: "pay-1",
      senderId: "me",
      text: "The full window set, 12 photos in high resolution.",
      sentAt: ago(25),
      state: "read",
      attachments: [
        { kind: "image", url: picture(30, "Window 1") },
        { kind: "image", url: picture(280, "Window 2") },
        { kind: "image", url: picture(120, "Window 3") },
      ],
      paywall: { price: { amount: 49900, currency: "PHP" }, unlocked: true },
    },
    {
      id: "pay-2",
      senderId: "ana",
      text: "My private behind-the-scenes clip 🎬",
      sentAt: ago(20),
      attachments: [
        { kind: "image", url: picture(200, "Behind 1") },
        { kind: "image", url: picture(330, "Behind 2") },
      ],
      paywall: { price: { amount: 29900, currency: "PHP" }, unlocked: false },
    },
    {
      id: "tip-out",
      senderId: "me",
      sentAt: ago(10),
      state: "delivered",
      attachments: [{ kind: "tip", amount: 10000, currency: "PHP" }],
    },
    {
      id: "last",
      senderId: "ana",
      text: "Which set should we shoot next?",
      sentAt: ago(5),
    },
  ]
}

/** A memory adapter with the stories' data. */
function storyAdapter(options: MemoryOptions = {}) {
  return createMemoryChatAdapter(
    {
      me: people.me,
      people,
      conversations: seedConversations(),
      messages: {
        ana: seedAnaMessages(),
        marco: [
          {
            id: "mk1",
            senderId: "marco",
            text: "See you then",
            sentAt: ago(90),
          },
        ],
        team: [
          {
            id: "t0",
            senderId: "system",
            kind: "system",
            text: "Lea Cruz added you.",
            sentAt: ago(3000),
          },
          {
            id: "t1",
            senderId: "lea",
            text: "Who has the reflector?",
            sentAt: ago(2900),
          },
          {
            id: "t2",
            senderId: "kim",
            text: "I do! Bringing it Saturday.",
            sentAt: ago(2890),
          },
        ],
      },
    },
    {
      latency: 250,
      simulate: { everyMs: 20000 },
      // The second paywall fails once, to show the error and Try again.
      unlock: (m) =>
        m.id === "pay-2" && !failedOnce.has(m.id) && failedOnce.add(m.id)
          ? "The card was declined. Use another card, then try again."
          : true,
      ...options,
    }
  )
}
const failedOnce = new Set<string>()

/** Sample GIF search (drawn tiles, nothing from the web). */
async function sampleGifSearch(q: string): Promise<GifResult[]> {
  return Array.from({ length: q ? 6 : 9 }, (_, i) => ({
    url: `data:image/svg+xml,${encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" width="80" height="80"><rect width="80" height="80" fill="hsl(${i * 40} 70% 65%)"/><text x="40" y="46" text-anchor="middle" font-size="12" fill="white">${q || "GIF"} ${i + 1}</text></svg>`)}`,
    title: `${q || "GIF"} ${i + 1}`,
  }))
}

const PriceIcon = () => (
  <svg
    viewBox="0 0 16 16"
    fill="none"
    stroke="currentColor"
    strokeWidth="1.3"
    strokeLinecap="round"
    strokeLinejoin="round"
    aria-hidden="true"
  >
    <path d="M8.5 1.5h5v5l-7 7-5-5zM11 4v.1" />
  </svg>
)

/** An app's own tool: puts a price (as the person's language writes money) where the caret is. */
const insertPriceTool: ChatComposerTool = {
  id: "price",
  label: "Insert price",
  icon: <PriceIcon />,
  shortcut: "Mod+Shift+P",
  onClick: (ctx) => ctx.insert(formatAmount(49900, "PHP")),
}

/** The stories' configuration: categories, pill types, rules and tools. */
const storyConfig: ChatConfig = defineChatConfig({
  me: people.me,
  capabilities: {
    attach: true,
    voice: true,
    canFormatText: true,
    maxUploadBytes: 25_000_000,
  },
  getMessageCapabilities: () => ({ unsendDuration: 24 * 60 * 60_000 }),
  categories: [
    chatCategories.all,
    chatCategories.unread,
    chatCategories.tagged("vip", "VIP"),
    chatCategories.groups,
    chatCategories.pinned,
    chatCategories.muted,
    chatCategories.archived,
  ],
  pillTypes,
  allowCustomTags: true,
  composer: {
    // No `tools`: the usual set (defaultComposerTools), with what it needs from the app.
    toolOptions: {
      people: [
        { id: "ana", name: "Ana Reyes" },
        { id: "lea", name: "Lea Cruz" },
        { id: "kim", name: "Kim Park" },
      ],
      gifSearch: sampleGifSearch,
      simulateVoice: true,
      onSchedule: () => undefined,
      tip: { currency: "PHP", onSend: async () => undefined },
      more: [{ label: "Send as a file", onSelect: () => undefined }],
    },
  },
})

export {
  insertPriceTool,
  pillTypes,
  sampleGifSearch,
  seedConversations,
  storyAdapter,
  storyConfig,
}

/** A memory adapter holding one conversation ("c") with the given messages (for a part shown alone). */
function oneConversationAdapter(
  messages: ChatMessage[],
  options: { group?: boolean } = {}
) {
  const failed = new Set<string>()
  return createMemoryChatAdapter(
    {
      me: people.me,
      people,
      conversations: [
        {
          id: "c",
          title: options.group ? "Weekend shoot crew" : "Ana Reyes",
          group: options.group,
          people: options.group ? [people.ana, people.lea] : [people.ana],
        },
      ],
      messages: { c: messages },
    },
    {
      latency: 200,
      simulate: null,
      // "locked-fails" is declined once, to show the error and Try again.
      unlock: (m) =>
        m.id === "locked-fails" && !failed.has(m.id) && failed.add(m.id)
          ? "The card was declined. Use another card, then try again."
          : true,
    }
  )
}

export { oneConversationAdapter }
