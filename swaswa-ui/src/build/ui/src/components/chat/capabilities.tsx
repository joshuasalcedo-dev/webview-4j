import { type MenuEntry } from "../context-menu"
import { type DialogsApi } from "../dialog"
import { useChat } from "./chat-provider"
import { type ChatApi, type ChatState } from "./config"

/** The chat's rules (capabilities and state) and can(); the same as useChat(). */
function useChatCapabilities(): ChatApi {
  return useChat()
}

/** How long to mute for, as the choices people expect. */
const MUTE_CHOICES = [
  { value: "1h", label: "For 1 hour", ms: 3_600_000 },
  { value: "8h", label: "For 8 hours", ms: 8 * 3_600_000 },
  { value: "1w", label: "For 1 week", ms: 7 * 86_400_000 },
  { value: "always", label: "Until I turn it back on", ms: 0 },
] as const

/** Asks how long to mute for; resolves with the end (undefined: until turned back on), or null when cancelled. */
async function askMuteFor(
  dialogs: DialogsApi
): Promise<Date | undefined | null> {
  const choice = await dialogs.choose({
    title: "Mute notifications",
    message: "You won't be notified of new messages. They still arrive.",
    kind: "question",
    choices: [
      ...MUTE_CHOICES.map((c, i) => ({
        value: c.value as string,
        label: c.label,
        primary: i === 0,
      })),
      { value: "cancel", label: "Cancel", cancel: true },
    ],
  })
  const picked = MUTE_CHOICES.find((c) => c.value === choice)
  if (!picked) return null
  return picked.ms ? new Date(Date.now() + picked.ms) : undefined
}

/**
 * "Mute notifications" for a menu: ticked while muted (choosing it then unmutes); otherwise it asks how long.
 * Nothing when muting is not allowed or nobody handles it.
 */
function muteMenuItem(
  muted: boolean,
  onMuteChange: ((muted: boolean, until?: Date) => void) | undefined,
  dialogs: DialogsApi,
  allowed = true
): MenuEntry[] {
  if (!allowed || !onMuteChange) return []
  return [
    {
      label: muted ? "Mute notifications" : "Mute notifications…",
      checked: muted,
      onSelect: async () => {
        if (muted) return onMuteChange(false)
        const until = await askMuteFor(dialogs)
        if (until !== null) onMuteChange(true, until)
      },
    },
  ]
}

/** "Muted until 9:41 PM", "Muted until Mon 26 Oct", "Muted". */
function mutedWords(state: Pick<ChatState, "muted" | "mutedUntil">): string {
  if (!state.mutedUntil) return "Muted"
  const sameDay = state.mutedUntil.toDateString() === new Date().toDateString()
  return `Muted until ${
    sameDay
      ? state.mutedUntil.toLocaleTimeString(undefined, {
          hour: "numeric",
          minute: "2-digit",
        })
      : state.mutedUntil.toLocaleDateString(undefined, {
          weekday: "short",
          day: "numeric",
          month: "short",
        })
  }`
}

export { ChatProvider } from "./chat-provider"
export type { ChatCapabilities, ChatCapability, ChatState } from "./config"
export { askMuteFor, muteMenuItem, mutedWords, useChatCapabilities }
