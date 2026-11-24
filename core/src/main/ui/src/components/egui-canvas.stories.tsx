import type { Meta, StoryObj } from "@storybook/react-vite"
import * as React from "react"

import { bothPlatforms, dark } from "../stories/side-by-side"
import { EguiCanvas, type EguiCanvasHandle, type EguiEvent } from "./egui-canvas"

const meta = {
  title: "Native/EguiCanvas",
  component: EguiCanvas,
  decorators: [bothPlatforms],
  parameters: { nativeStretch: true },
  args: { component: "line-chart", height: 280, "aria-label": "A chart" },
} satisfies Meta<typeof EguiCanvas>
export default meta
type Story = StoryObj<typeof meta>

const wave = (n: number, f: (x: number) => number) =>
  Array.from({ length: n }, (_, i) => [i / 10, f(i / 10)] as [number, number])

const STATIC = {
  series: [
    { name: "sin", points: wave(120, Math.sin) },
    { name: "cos", points: wave(120, Math.cos), color: "#ff9f0a" },
  ],
  xLabel: "x",
  legend: true,
}

/** Lines given all at once. Zoom with the wheel, drag to pan, double-click to fit the data again. */
export const LineChart: Story = { args: { props: STATIC } }
export const LineChartDark: Story = { args: { props: STATIC }, parameters: dark }

/** Points streaming in through the handle (no render per point), the latest 300 kept. */
function Streaming() {
  const chart = React.useRef<EguiCanvasHandle>(null)
  React.useEffect(() => {
    let t = 0
    const timer = setInterval(() => {
      t += 0.05
      try {
        chart.current?.update({
          append: [
            { series: "cpu", points: [[t, 50 + 30 * Math.sin(t * 1.3) + 8 * Math.sin(t * 7.1)]] },
            { series: "memory", points: [[t, 40 + 10 * Math.sin(t * 0.4) + Math.min(t, 20)]] },
          ],
        })
      } catch {
        // not shown yet
      }
    }, 50)
    return () => clearInterval(timer)
  }, [])
  return (
    <EguiCanvas
      ref={chart}
      component="line-chart"
      props={{ maxPoints: 300, xLabel: "seconds", yLabel: "%" }}
      height={280}
      aria-label="CPU and memory, live"
    />
  )
}
export const LineChartStreaming: Story = { render: () => <Streaming /> }
export const LineChartStreamingDark: Story = { render: () => <Streaming />, parameters: dark }

/** What the chart reports, as Java would get it: click near a point, pan, zoom, double-click. */
function WithEvents() {
  const [events, setEvents] = React.useState<EguiEvent[]>([])
  return (
    <div style={{ display: "grid", gap: 8 }}>
      <EguiCanvas
        component="line-chart"
        props={STATIC}
        height={240}
        aria-label="A chart"
        onEvent={(e) => setEvents((list) => [...list.slice(-5), e])}
      />
      <pre style={{ margin: 0, minHeight: 96, font: "12px ui-monospace, monospace", color: "var(--nt-text-secondary)" }}>
        {events.map((e) => JSON.stringify(e)).join("\n") || "(no events yet)"}
      </pre>
    </div>
  )
}
export const Events: Story = { render: () => <WithEvents /> }

/** Bad props are refused with the reason, and nothing is drawn wrong. */
export const RefusedProps: Story = { args: { props: { maxPonts: 10 } } }
export const UnknownComponent: Story = { args: { component: "nope", props: {} } }
