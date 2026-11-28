import * as React from "react"
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Line,
  LineChart,
  Pie,
  PieChart,
  PolarAngleAxis,
  PolarGrid,
  Radar,
  RadarChart,
  RadialBar,
  RadialBarChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts"

import { cn } from "../lib/utils"
import "./chart.css"

type ChartKind = "line" | "area" | "bar" | "pie" | "radar" | "radialBar"

/** A measure in the rows: its field and what it is called. */
type ChartSeries = { key: string; label: string }

type ChartProps = {
  kind: ChartKind
  /** One row per point (a month, a browser): the x field and each series' field. */
  data: Record<string, unknown>[]
  /** The field that names each row: the x axis, or a pie's slices. */
  x: string
  /** The measures, in the order their colours go (fixed: a series keeps its colour). At most 8. */
  series: ChartSeries[]
  title?: string
  description?: string
  /** Bars and areas on top of each other instead of side by side. */
  stacked?: boolean
  /** Bars along, not up. */
  horizontal?: boolean
  /** How tall the plot is, in px (260 unless set). */
  height?: number
  /** After each value: "%", " km", " ms". */
  unit?: string
  /** Decimals in values (as many as needed unless set). */
  decimals?: number
  /** Opens on the table instead of the chart. */
  table?: boolean
  className?: string
  style?: React.CSSProperties
}

const color = (i: number) => `var(--nt-chart-${(i % 8) + 1})`

function useFormat(unit?: string, decimals?: number) {
  return React.useMemo(() => {
    const f = new Intl.NumberFormat(undefined, decimals === undefined ? { maximumFractionDigits: 2 } : { minimumFractionDigits: decimals, maximumFractionDigits: decimals })
    return (v: unknown) => (typeof v === "number" ? f.format(v) + (unit ?? "") : String(v ?? ""))
  }, [unit, decimals])
}

type TipPayload = { name?: string; value?: unknown; dataKey?: unknown; color?: string; payload?: Record<string, unknown> }

/** The hover card: the row's name, then each series with its colour beside it (text stays in the text colours). */
function Tip({ active, payload, label, x, series, format }: { active?: boolean; payload?: TipPayload[]; label?: unknown; x: string; series: ChartSeries[]; format: (v: unknown) => string }) {
  if (!active || !payload?.length) return null
  const name = label ?? payload[0]?.payload?.[x]
  return (
    <div className="nt-chart-tooltip">
      {name != null && <div className="nt-chart-tooltip-label">{String(name)}</div>}
      {payload.map((p, i) => {
        const s = series.find((s) => s.key === p.dataKey)
        return (
          <div key={i} className="nt-chart-tooltip-row">
            <span className="nt-chart-swatch" style={{ background: p.color }} />
            <span>{s?.label ?? p.name}</span>
            <span>{format(p.value)}</span>
          </div>
        )
      })}
    </div>
  )
}

function Legend({ items }: { items: { label: string; color: string }[] }) {
  return (
    <div className="nt-chart-legend" aria-hidden="true">
      {items.map((it) => (
        <span key={it.label}>
          <span className="nt-chart-swatch" style={{ background: it.color }} />
          {it.label}
        </span>
      ))}
    </div>
  )
}

/**
 * A chart as shadcn/ui's (Recharts): line, area, bar, pie, radar or radial bar, with the kit's colours in light
 * and dark, values on hover, a legend for two or more series, and the same numbers as a table (the Chart / Table
 * switch, also for screen readers). One value axis only.
 */
function Chart({ kind, data, x, series, title, description, stacked, horizontal, height = 260, unit, decimals, table, className, style }: ChartProps) {
  const [asTable, setAsTable] = React.useState(!!table)
  React.useEffect(() => setAsTable(!!table), [table])
  const format = useFormat(unit, decimals)
  const shown = series.slice(0, 8)
  const pie = kind === "pie" || kind === "radialBar"
  // A pie's slices are the rows (one series); the others' colours are the series'.
  const legend = pie
    ? data.map((row, i) => ({ label: String(row[x]), color: color(i) }))
    : shown.length >= 2
      ? shown.map((s, i) => ({ label: s.label, color: color(i) }))
      : []
  const tooltip = <Tooltip cursor={kind === "bar" ? { fill: "var(--nt-subtle-hover)" } : { stroke: "var(--nt-chart-axis)", strokeWidth: 1 }} content={<Tip x={x} series={shown} format={format} />} />
  const grid = <CartesianGrid stroke="var(--nt-chart-grid)" vertical={false} />
  const axisProps = { stroke: "var(--nt-chart-axis)", tickLine: false, axisLine: false, tickMargin: 8 } as const
  const valueAxis = <YAxis {...axisProps} width={48} tickFormatter={(v) => format(v)} />

  const plot = (() => {
    switch (kind) {
      case "line":
        return (
          <LineChart data={data} margin={{ top: 8, right: 12, left: 0, bottom: 0 }}>
            {grid}
            <XAxis dataKey={x} {...axisProps} />
            {valueAxis}
            {tooltip}
            {shown.map((s, i) => (
              <Line key={s.key} dataKey={s.key} name={s.label} type="monotone" stroke={color(i)} strokeWidth={2} dot={false} activeDot={{ r: 4, strokeWidth: 2, stroke: "var(--nt-surface)" }} />
            ))}
          </LineChart>
        )
      case "area":
        return (
          <AreaChart data={data} margin={{ top: 8, right: 12, left: 0, bottom: 0 }}>
            {grid}
            <XAxis dataKey={x} {...axisProps} />
            {valueAxis}
            {tooltip}
            {shown.map((s, i) => (
              <Area key={s.key} dataKey={s.key} name={s.label} type="monotone" stackId={stacked ? "a" : undefined} stroke={color(i)} strokeWidth={2} fill={color(i)} fillOpacity={stacked ? 0.55 : 0.18} activeDot={{ r: 4, strokeWidth: 2, stroke: "var(--nt-surface)" }} />
            ))}
          </AreaChart>
        )
      case "bar":
        return (
          <BarChart data={data} layout={horizontal ? "vertical" : "horizontal"} margin={{ top: 8, right: 12, left: 0, bottom: 0 }} barGap={2} barCategoryGap="20%">
            <CartesianGrid stroke="var(--nt-chart-grid)" vertical={!!horizontal} horizontal={!horizontal} />
            {horizontal ? (
              <>
                <XAxis type="number" {...axisProps} tickFormatter={(v) => format(v)} />
                <YAxis type="category" dataKey={x} {...axisProps} width={80} />
              </>
            ) : (
              <>
                <XAxis dataKey={x} {...axisProps} />
                {valueAxis}
              </>
            )}
            {tooltip}
            {shown.map((s, i) => {
              const last = !stacked || i === shown.length - 1
              const end = last ? 4 : 0
              return (
                <Bar
                  key={s.key}
                  dataKey={s.key}
                  name={s.label}
                  fill={color(i)}
                  stackId={stacked ? "a" : undefined}
                  // the data end rounded (4 px), the baseline square; a 2 px surface gap between stacked parts
                  radius={horizontal ? [0, end, end, 0] : [end, end, 0, 0]}
                  stroke={stacked ? "var(--nt-surface)" : undefined}
                  strokeWidth={stacked ? 2 : 0}
                />
              )
            })}
          </BarChart>
        )
      case "pie": {
        const s = shown[0]
        return (
          <PieChart>
            {tooltip}
            <Pie data={data} dataKey={s?.key ?? "value"} nameKey={x} innerRadius="55%" outerRadius="85%" stroke="var(--nt-surface)" strokeWidth={2} paddingAngle={1}>
              {data.map((_, i) => (
                <Cell key={i} fill={color(i)} />
              ))}
            </Pie>
          </PieChart>
        )
      }
      case "radar":
        return (
          <RadarChart data={data} outerRadius="75%">
            <PolarGrid stroke="var(--nt-chart-grid)" />
            <PolarAngleAxis dataKey={x} />
            {tooltip}
            {shown.map((s, i) => (
              <Radar key={s.key} dataKey={s.key} name={s.label} stroke={color(i)} strokeWidth={2} fill={color(i)} fillOpacity={0.2} />
            ))}
          </RadarChart>
        )
      case "radialBar": {
        const s = shown[0]
        return (
          <RadialBarChart data={data.map((row, i) => ({ ...row, fill: color(i) }))} innerRadius="25%" outerRadius="95%" startAngle={90} endAngle={-270}>
            {tooltip}
            <RadialBar dataKey={s?.key ?? "value"} background={{ fill: "var(--nt-subtle-hover)" }} cornerRadius={4} />
          </RadialBarChart>
        )
      }
    }
  })()

  return (
    <figure className={cn("nt-chart", className)} style={{ margin: 0, ...style }} aria-label={title ?? "Chart"}>
      <div className="nt-chart-head">
        <div>
          {title && <div className="nt-chart-title">{title}</div>}
          {description && <div className="nt-chart-description">{description}</div>}
        </div>
        <div className="nt-chart-view" role="group" aria-label="Show as">
          <button type="button" aria-pressed={!asTable} onClick={() => setAsTable(false)}>
            Chart
          </button>
          <button type="button" aria-pressed={asTable} onClick={() => setAsTable(true)}>
            Table
          </button>
        </div>
      </div>
      {asTable ? (
        <table className="nt-chart-table">
          <thead>
            <tr>
              <th scope="col">{x}</th>
              {shown.map((s) => (
                <th key={s.key} scope="col">
                  {s.label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {data.map((row, i) => (
              <tr key={i}>
                <th scope="row" style={{ fontWeight: 400, color: "var(--nt-text)" }}>
                  {String(row[x] ?? "")}
                </th>
                {shown.map((s) => (
                  <td key={s.key}>{format(row[s.key])}</td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      ) : (
        <>
          <div className="nt-chart-plot" style={{ height }} aria-hidden="true">
            <ResponsiveContainer width="100%" height="100%">
              {plot}
            </ResponsiveContainer>
          </div>
          {legend.length > 0 && <Legend items={legend} />}
        </>
      )}
    </figure>
  )
}

export { Chart }
export type { ChartKind, ChartProps, ChartSeries }
