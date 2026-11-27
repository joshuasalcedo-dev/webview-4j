// How chats write times and days, in the person's own language and clock.

const toDate = (value: Date | string) =>
  typeof value === "string" ? new Date(value) : value

const startOfDay = (date: Date) =>
  new Date(date.getFullYear(), date.getMonth(), date.getDate())

/** Whole days between two dates' calendar days (today 0, yesterday 1). */
function daysAgo(value: Date | string, now = new Date()): number {
  return Math.round(
    (startOfDay(now).getTime() - startOfDay(toDate(value)).getTime()) /
      86_400_000
  )
}

/** The time of day: "9:41" (or "9:41 AM", as the person's clock is). */
function formatTime(value: Date | string): string {
  return toDate(value).toLocaleTimeString(undefined, {
    hour: "numeric",
    minute: "2-digit",
  })
}

/** The time in a conversation list: "9:41" today, "Yesterday", "Mon" this week, otherwise a short date. */
function formatListTime(value: Date | string, now = new Date()): string {
  const date = toDate(value)
  const days = daysAgo(date, now)
  if (days <= 0) return formatTime(date)
  if (days === 1) return "Yesterday"
  if (days < 7) return date.toLocaleDateString(undefined, { weekday: "short" })
  return date.toLocaleDateString(undefined, {
    day: "numeric",
    month: "numeric",
    year: date.getFullYear() === now.getFullYear() ? undefined : "2-digit",
  })
}

/** A day between messages: "Today", "Yesterday", "Monday, 12 October" (with the year when not this year). */
function formatDay(value: Date | string, now = new Date()): string {
  const date = toDate(value)
  const days = daysAgo(date, now)
  if (days === 0) return "Today"
  if (days === 1) return "Yesterday"
  return date.toLocaleDateString(undefined, {
    weekday: "long",
    day: "numeric",
    month: "long",
    year: date.getFullYear() === now.getFullYear() ? undefined : "numeric",
  })
}

/** A voice note's length: "0:42", "1:05:09". */
function formatDuration(seconds: number): string {
  const s = Math.max(0, Math.round(seconds))
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const rest = String(s % 60).padStart(2, "0")
  return h > 0 ? `${h}:${String(m).padStart(2, "0")}:${rest}` : `${m}:${rest}`
}

/** "Last seen just now", "Last seen 5 min ago", "Last seen today at 9:41", "Last seen yesterday", a date. */
function formatLastSeen(value: Date | string, now = new Date()): string {
  const date = toDate(value)
  const minutes = Math.floor((now.getTime() - date.getTime()) / 60_000)
  if (minutes < 1) return "Last seen just now"
  if (minutes < 60) return `Last seen ${minutes} min ago`
  const days = daysAgo(date, now)
  if (days === 0) return `Last seen today at ${formatTime(date)}`
  if (days === 1) return "Last seen yesterday"
  return `Last seen ${date.toLocaleDateString(undefined, { day: "numeric", month: "short" })}`
}

/** Whether two times are on the same calendar day. */
const sameDay = (a: Date | string, b: Date | string) =>
  toDate(a).toDateString() === toDate(b).toDateString()

export {
  daysAgo,
  formatDay,
  formatDuration,
  formatLastSeen,
  formatListTime,
  formatTime,
  sameDay,
  toDate,
}

/** An amount in a currency's smallest unit, written as the person's language writes money ("₱500.00"). */
function formatAmount(amount: number, currency: string): string {
  const format = new Intl.NumberFormat(undefined, {
    style: "currency",
    currency,
  })
  const digits = format.resolvedOptions().maximumFractionDigits ?? 2
  return format.format(amount / 10 ** digits)
}

export { formatAmount }
