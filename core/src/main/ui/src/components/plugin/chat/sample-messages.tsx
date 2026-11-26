// Sample messages for the message stories: tips, paywalls, per-message rules, and slots (shared by every story).
import { Button } from "../../button"
import { Money } from "../../money"
import { Pill } from "../../pill"
import { ProgressBar } from "../../progress-bar"
import { Text } from "../../text"
import type { ChatConfig } from "./config"
import { picture } from "./sample"
import type { ChatMessage } from "./types"

const ago = (minutes: number) => new Date(Date.now() - minutes * 60_000)

export const sampleTips = (): ChatMessage[] => [
  { id: "a", senderId: "ana", text: "Thanks for the photos!", sentAt: ago(12) },
  {
    id: "t1",
    senderId: "ana",
    sentAt: ago(11),
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
    id: "t2",
    senderId: "ana",
    sentAt: ago(9),
    attachments: [{ kind: "tip", amount: 10000, currency: "PHP" }],
  },
  {
    id: "t3",
    senderId: "me",
    sentAt: ago(5),
    state: "read",
    attachments: [
      {
        kind: "tip",
        amount: 25000,
        currency: "PHP",
        note: "Coffee's on me ☕",
      },
    ],
  },
  {
    id: "t4",
    senderId: "me",
    sentAt: ago(2),
    state: "delivered",
    attachments: [{ kind: "tip", amount: 5000, currency: "PHP" }],
  },
]

export const samplePaywalls = (): ChatMessage[] => [
  { id: "p0", senderId: "ana", text: "New set is up 📸", sentAt: ago(30) },
  {
    id: "locked",
    senderId: "ana",
    text: "The sunset set (8 photos)",
    sentAt: ago(29),
    attachments: [
      { kind: "image", url: picture(30, "Sunset 1") },
      { kind: "image", url: picture(10, "Sunset 2") },
      { kind: "image", url: picture(340, "Sunset 3") },
    ],
    paywall: { price: { amount: 49900, currency: "PHP" }, unlocked: false },
  },
  {
    id: "locked-fails",
    senderId: "ana",
    sentAt: ago(28),
    attachments: [{ kind: "image", url: picture(200, "Studio") }],
    paywall: { price: { amount: 19900, currency: "PHP" }, unlocked: false },
  },
  {
    id: "locked-hidden",
    senderId: "ana",
    sentAt: ago(27),
    paywall: {
      price: { amount: 9900, currency: "PHP" },
      preview: "hidden",
      unlocked: false,
    },
  },
  {
    id: "locked-teaser",
    senderId: "ana",
    sentAt: ago(26),
    text: "The whole story behind the shoot…",
    paywall: {
      price: { amount: 4900, currency: "PHP" },
      preview: "teaser",
      teaserText:
        "It started with a broken tripod and a sunset we almost missed…",
      unlocked: false,
    },
  },
]

export const sampleRules = (): ChatMessage[] => [
  {
    id: "r1",
    senderId: "ana",
    text: "This one takes only 👍 and ❤️.",
    sentAt: ago(40),
    capabilities: { allowedReactions: ["👍", "❤️"] },
  },
  {
    id: "r2",
    senderId: "ana",
    text: "This one takes no reactions at all.",
    sentAt: ago(39),
    capabilities: { allowedReactions: [] },
  },
  {
    id: "r3",
    senderId: "ana",
    text: "You can't reply to this one (an announcement).",
    sentAt: ago(38),
    capabilities: { canReplyTo: false },
  },
  {
    id: "r4",
    senderId: "me",
    text: "Sent 5 minutes ago: right-click → Unsend (10 min left).",
    sentAt: ago(5),
    state: "read",
  },
  {
    id: "r5",
    senderId: "me",
    text: "Sent 2 hours ago: past its unsend time.",
    sentAt: ago(120),
    state: "read",
  },
]

export const sampleSlots = (): ChatMessage[] => [
  {
    id: "s1",
    senderId: "ana",
    text: "Loved the session! What should we shoot next?",
    sentAt: ago(15),
    below: (
      <>
        {["Beach", "Studio", "Street"].map((q) => (
          <Pill
            key={q}
            color="accent"
            size="medium"
            onPressedChange={() => undefined}
          >
            {q}
          </Pill>
        ))}
      </>
    ),
  },
  {
    id: "s2",
    senderId: "me",
    text: "Quick poll for everyone 👇",
    sentAt: ago(10),
    state: "read",
    below: (
      <div style={{ display: "grid", gap: 6, width: 240 }}>
        {[
          ["Beach", 62],
          ["Studio", 25],
          ["Street", 13],
        ].map(([name, part]) => (
          <div key={name} style={{ display: "grid", gap: 2 }}>
            <Text variant="caption">
              {name} · {part}%
            </Text>
            <ProgressBar aria-label={`${name} votes`} value={Number(part)} />
          </div>
        ))}
      </div>
    ),
  },
  {
    id: "s3",
    senderId: "ana",
    sentAt: ago(6),
    attachments: [
      {
        kind: "custom",
        type: "order",
        data: { item: "Signed print, A3", amount: 150000, currency: "PHP" },
      },
    ],
  },
]

/** Under every message from Ana: a tip row (render.below), and the app's own "order" attachment. */
export const sampleSlotConfig: Partial<ChatConfig> = {
  render: {
    below: (m) =>
      m.senderId === "ana" && m.id === "s1" ? undefined : m.senderId ===
        "ana" ? (
        <>
          {[5000, 10000, 50000].map((a) => (
            <Button key={a} variant="subtle">
              Tip <Money amount={a} currency="PHP" trimZeros />
            </Button>
          ))}
        </>
      ) : undefined,
    attachment: {
      order: (a) => (
        <div style={{ display: "grid", gap: 4, minWidth: 200 }}>
          <Text variant="caption" tone="secondary">
            Order
          </Text>
          <Text strong>{String(a.data.item)}</Text>
          <Money
            amount={Number(a.data.amount)}
            currency={String(a.data.currency)}
          />
          <Button variant="accent">Pay now</Button>
        </div>
      ),
    },
  },
}
