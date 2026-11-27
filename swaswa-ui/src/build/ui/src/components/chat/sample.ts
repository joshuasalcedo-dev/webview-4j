// Sample people, conversations and messages for the chat stories (pictures drawn here, nothing from the web).
import type { ChatMessage, ChatPerson, Conversation } from "./types"

/** A made-up picture: a soft gradient with a few shapes, as an SVG data address. */
function picture(hue: number, label = ""): string {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="480" height="360" viewBox="0 0 480 360">
<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="hsl(${hue},70%,72%)"/><stop offset="1" stop-color="hsl(${(hue + 40) % 360},60%,45%)"/></linearGradient></defs>
<rect width="480" height="360" fill="url(#g)"/><circle cx="360" cy="90" r="46" fill="hsla(50,100%,92%,.85)"/>
<path d="M0 300 140 170 250 260 330 200 480 320V360H0z" fill="hsla(${(hue + 180) % 360},35%,25%,.55)"/>
<text x="240" y="340" text-anchor="middle" font-family="Segoe UI, sans-serif" font-size="22" fill="white" opacity=".9">${label}</text></svg>`
  return `data:image/svg+xml,${encodeURIComponent(svg)}`
}

const at = (daysAgo: number, hour: number, minute = 0) => {
  const d = new Date()
  d.setDate(d.getDate() - daysAgo)
  d.setHours(hour, minute, 0, 0)
  return d
}

const people: Record<string, ChatPerson> = {
  me: { id: "me", name: "You" },
  ana: { id: "ana", name: "Ana Reyes", online: true },
  marco: {
    id: "marco",
    name: "Marco Silva",
    lastSeen: new Date(Date.now() - 5 * 60_000),
  },
  kim: { id: "kim", name: "Kim Park", lastSeen: at(1, 22, 10) },
  lea: { id: "lea", name: "Lea Cruz", online: true },
}

/** A conversation with every kind of message, oldest first. */
function sampleMessages(): ChatMessage[] {
  return [
    {
      id: "s1",
      senderId: "system",
      kind: "system",
      text: "Ana Reyes started the conversation.",
      sentAt: at(3, 9, 0),
    },
    {
      id: "m1",
      senderId: "ana",
      text: "Hi! Are the photos from Saturday ready?",
      sentAt: at(3, 9, 2),
    },
    {
      id: "m2",
      senderId: "me",
      text: "Almost! Editing the last few now.",
      sentAt: at(3, 9, 5),
      state: "read",
    },
    {
      id: "m3",
      senderId: "me",
      text: "Here's a first look 👇",
      sentAt: at(3, 9, 6),
      state: "read",
    },
    {
      id: "m4",
      senderId: "me",
      sentAt: at(3, 9, 6),
      state: "read",
      attachments: [
        { kind: "image", url: picture(200, "Beach"), name: "beach.jpg" },
      ],
      reactions: [{ emoji: "❤️", count: 1 }],
    },
    {
      id: "m5",
      senderId: "ana",
      text: "These are beautiful!!",
      sentAt: at(1, 20, 14),
      reactions: [{ emoji: "😂", count: 1, mine: true }],
    },
    {
      id: "m6",
      senderId: "ana",
      text: "Can you send the ones by the window too?",
      sentAt: at(1, 20, 15),
    },
    {
      id: "m7",
      senderId: "me",
      sentAt: at(1, 20, 30),
      state: "read",
      replyTo: "m6",
      text: "Of course, here they are:",
      attachments: [
        { kind: "image", url: picture(30, "Window 1") },
        { kind: "image", url: picture(280, "Window 2") },
        { kind: "image", url: picture(120, "Window 3") },
        { kind: "image", url: picture(330, "Window 4") },
        { kind: "image", url: picture(60, "Window 5") },
      ],
    },
    {
      id: "m8",
      senderId: "me",
      sentAt: at(1, 20, 31),
      state: "read",
      attachments: [
        {
          kind: "file",
          name: "Saturday shoot - full set.zip",
          size: 248_000_000,
        },
      ],
    },
    {
      id: "m9",
      senderId: "ana",
      sentAt: at(0, 9, 12),
      attachments: [{ kind: "voice", duration: 14 }],
    },
    {
      id: "m10",
      senderId: "ana",
      text: "Also, this is the place I mentioned: https://example.com/studio",
      sentAt: at(0, 9, 13),
      link: {
        url: "https://example.com/studio",
        title: "Window Light Studio",
        description: "A daylight studio with tall windows, open on weekends.",
        image: picture(40, "Studio"),
        site: "example.com",
      },
    },
    {
      id: "m11",
      senderId: "me",
      sentAt: at(0, 9, 20),
      state: "delivered",
      text: "The script I use to rename them:",
      code: {
        fileName: "rename.py",
        code: 'from pathlib import Path\n\nfor i, photo in enumerate(sorted(Path("shoot").glob("*.jpg")), 1):\n    photo.rename(photo.with_name(f"saturday-{i:03}.jpg"))',
      },
    },
    {
      id: "m12",
      senderId: "ana",
      text: "Perfect, thank you!",
      sentAt: at(0, 9, 41),
      edited: true,
      pinned: true,
    },
    { id: "m13", senderId: "ana", text: "Talk soon 😊", sentAt: at(0, 9, 41) },
    {
      id: "m14",
      senderId: "me",
      text: "This one didn't go through.",
      sentAt: at(0, 9, 45),
      state: "failed",
    },
  ]
}

/** Older small talk, for "load older" (each call gives the 20 before `before`). */
function olderMessages(before: Date, page: number): ChatMessage[] {
  return Array.from({ length: 20 }, (_, i) => {
    const sentAt = new Date(before.getTime() - (20 - i) * 37 * 60_000)
    const mine = (i + page) % 3 === 0
    return {
      id: `old-${page}-${i}`,
      senderId: mine ? "me" : "ana",
      text: mine
        ? `Older message ${page * 20 + i + 1} from you.`
        : `Older message ${page * 20 + i + 1} from Ana.`,
      sentAt,
      state: mine ? ("read" as const) : undefined,
    }
  })
}

function sampleConversations(): Conversation[] {
  return [
    {
      id: "ana",
      title: "Ana Reyes",
      online: true,
      unread: 2,
      pinned: true,
      lastMessage: { text: "Talk soon 😊", at: at(0, 9, 41) },
    },
    {
      id: "marco",
      title: "Marco Silva",
      typing: true,
      lastMessage: { text: "See you then", at: at(0, 8, 5) },
    },
    {
      id: "kim",
      title: "Kim Park",
      lastMessage: { fromMe: true, attachment: "photo", at: at(1, 18, 0) },
    },
    {
      id: "team",
      title: "Weekend shoot crew",
      unread: 14,
      muted: true,
      lastMessage: { text: "Lea: who has the reflector?", at: at(2, 11, 0) },
    },
    {
      id: "lea",
      title: "Lea Cruz",
      online: true,
      draft: "I'll bring the",
      lastMessage: { text: "Thanks!", at: at(4, 15, 0) },
    },
    {
      id: "ben",
      title: "Ben Ortiz",
      markedUnread: true,
      lastMessage: { attachment: "voice", at: at(12, 10, 0) },
    },
    {
      id: "sara",
      title: "Sara Diaz",
      lastMessage: {
        text: "The invoice is in your email.",
        fromMe: false,
        attachment: "file",
        at: at(40, 10, 0),
      },
    },
  ]
}

export { olderMessages, people, picture, sampleConversations, sampleMessages }
