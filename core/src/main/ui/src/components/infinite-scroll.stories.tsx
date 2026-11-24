import type { Meta, StoryObj } from "@storybook/react-vite"
import * as React from "react"

import { bothPlatforms, dark } from "../stories/side-by-side"
import { InfiniteScroll, Spinner } from "./infinite-scroll"
import { Money } from "./money"
import { Text } from "./text"

const names = [
  "Ana Reyes",
  "Marco Silva",
  "Kim Park",
  "Lea Cruz",
  "Tom Lee",
  "Sara Diaz",
  "Ben Ortiz",
]
const wait = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

type Order = { id: number; name: string; amount: number }
const order = (id: number): Order => ({
  id,
  name: names[id % names.length],
  amount: ((id * 7919) % 90000) + 9900,
})

/**
 * Orders, 30 at a time: scroll down and the next page comes before the end. `failOnce` makes the third page
 * fail the first time (Try again then works); `pageSize` 3 shows short pages filling the box by themselves.
 */
function Orders({
  failOnce = false,
  pageSize = 30,
  total = 200,
}: {
  failOnce?: boolean
  pageSize?: number
  total?: number
}) {
  const [orders, setOrders] = React.useState<Order[]>([])
  const failed = React.useRef(false)
  const loadMore = async () => {
    await wait(600)
    const page = Math.floor(orders.length / pageSize)
    if (failOnce && page === 2 && !failed.current) {
      failed.current = true
      throw new Error("Couldn't load more orders: the server didn't answer.")
    }
    const start = orders.length
    setOrders((now) => [
      ...now,
      ...Array.from({ length: Math.min(pageSize, total - start) }, (_, i) =>
        order(start + i + 1)
      ),
    ])
  }
  return (
    <InfiniteScroll
      onLoadMore={loadMore}
      hasMore={orders.length < total}
      endText={`That's all ${total} orders.`}
      style={{ height: 300, width: "100%" }}
    >
      {orders.map((o) => (
        <div
          key={o.id}
          style={{
            display: "flex",
            justifyContent: "space-between",
            padding: "6px 12px",
            borderBottom: "1px solid var(--nt-separator)",
          }}
        >
          <Text selectable={false}>
            #{o.id} · {o.name}
          </Text>
          <Money amount={o.amount} currency="PHP" />
        </div>
      ))}
    </InfiniteScroll>
  )
}

/** A chat's history: it opens at the newest; scroll up and earlier messages come in above without a jump. */
function History() {
  const [messages, setMessages] = React.useState(() =>
    Array.from({ length: 20 }, (_, i) => 200 - 19 + i)
  )
  const box = React.useRef<HTMLDivElement>(null)
  React.useLayoutEffect(() => {
    const scroller = box.current?.querySelector(".nt-infinite")
    if (scroller) scroller.scrollTop = scroller.scrollHeight
  }, [])
  return (
    <div ref={box} style={{ width: "100%" }}>
      <InfiniteScroll
        direction="up"
        onLoadMore={async () => {
          await wait(600)
          setMessages((now) => [
            ...Array.from({ length: 20 }, (_, i) => now[0] - 20 + i).filter(
              (n) => n > 0
            ),
            ...now,
          ])
        }}
        hasMore={messages[0] > 1}
        endText="This is the start of your messages with Ana."
        style={{ height: 300 }}
      >
        {messages.map((n) => (
          <div
            key={n}
            style={{
              display: "flex",
              justifyContent: n % 3 ? "flex-start" : "flex-end",
              padding: "3px 12px",
            }}
          >
            <Text
              style={{
                padding: "6px 10px",
                borderRadius: 12,
                background: "var(--nt-subtle-hover)",
              }}
            >
              Message {n}
            </Text>
          </div>
        ))}
      </InfiniteScroll>
    </div>
  )
}

const meta = {
  title: "Native/InfiniteScroll",
  component: Orders,
  decorators: [bothPlatforms],
  parameters: { nativeStretch: true },
} satisfies Meta<typeof Orders>
export default meta
type Story = StoryObj<typeof meta>

export const LoadsAsYouScroll: Story = {}
export const LoadsAsYouScrollDark: Story = {
  parameters: { nativeStretch: true, ...dark },
}
export const FailsThenTryAgain: Story = { args: { failOnce: true } }
export const ShortPagesFillTheBox: Story = { args: { pageSize: 3, total: 40 } }
export const ChatHistoryUpwards: Story = { render: () => <History /> }
export const Spinners: Story = {
  render: () => (
    <div style={{ display: "flex", gap: 20, alignItems: "center" }}>
      <Spinner />
      <Spinner size={24} />
      <Spinner size={32} label="Loading the page" />
    </div>
  ),
}
export const SpinnersDark: Story = {
  ...Spinners,
  parameters: { nativeStretch: true, ...dark },
}
