import type { Meta, StoryObj } from "@storybook/react-vite"
import * as React from "react"

import { NativeProvider } from "../../provider"
import { DataTable, type DataRow, type DataSort } from "./data-table"

const ORDERS: DataRow[] = Array.from({ length: 42 }, (_, i) => ({
  id: String(1000 + i),
  customer: ["Ana Reyes", "Marco Silva", "Jules", "Kim Park", "Grace Hopper"][i % 5],
  items: (i * 7) % 13 + 1,
  total: ((i * 3719) % 90000) + 1500,
  currency: i % 4 === 0 ? "USD" : "PHP",
  placed: new Date(2026, 10, 1 + (i % 28)).toISOString(),
  paid: i % 3 !== 0,
  status: ["New", "Packed", "Shipped", "Delivered"][i % 4],
}))

const COLUMNS = [
  { key: "id", header: "Order", width: 90 },
  { key: "customer", header: "Customer", editable: true },
  { key: "items", header: "Items", type: "number" as const },
  { key: "total", header: "Total", type: "money" as const, currency: "@currency" },
  { key: "placed", header: "Placed", type: "date" as const },
  { key: "paid", header: "Paid", type: "boolean" as const },
  { key: "status", header: "Status", type: "pill" as const },
]

/** Sorted, searched and paged here: the whole list in the page. */
function Here({ platform, theme }: { platform: "windows" | "mac"; theme: "light" | "dark" }) {
  const [rows, setRows] = React.useState(ORDERS)
  const [chosen, setChosen] = React.useState<string[]>([])
  return (
    <NativeProvider platform={platform} theme={theme} style={{ padding: 24, height: 560, display: "flex", flexDirection: "column" }}>
      <DataTable
        aria-label="Orders"
        columns={COLUMNS}
        rows={rows}
        selectable
        selected={chosen}
        onSelectionChange={setChosen}
        search
        pageSize={10}
        rowMenu={(row) => [{ label: `Open order ${row.id}` }, "separator", { label: "Delete" }]}
        onCellEdit={(id, key, value) => setRows((all) => all.map((r) => (r.id === id ? { ...r, [key]: value } : r)))}
        actions={[{ label: "Mark paid", onSelect: (ids) => setRows((all) => all.map((r) => (ids.includes(String(r.id)) ? { ...r, paid: true } : r))) }]}
        style={{ flex: 1 }}
      />
    </NativeProvider>
  )
}

/** Sorted and paged by "the server": the table says what it wants, and shows one page. */
function OnTheServer() {
  const [page, setPage] = React.useState(1)
  const [sort, setSort] = React.useState<DataSort>(null)
  const sorted = React.useMemo(() => {
    if (!sort) return ORDERS
    return [...ORDERS].sort((a, b) => String(a[sort.key]).localeCompare(String(b[sort.key]), undefined, { numeric: true }) * (sort.desc ? -1 : 1))
  }, [sort])
  return (
    <NativeProvider platform="windows" theme="light" style={{ padding: 24 }}>
      <DataTable
        aria-label="Orders"
        columns={COLUMNS}
        rows={sorted.slice((page - 1) * 8, page * 8)}
        sort={sort}
        onSortChange={setSort}
        pageSize={8}
        page={page}
        total={ORDERS.length}
        onPageChange={setPage}
      />
    </NativeProvider>
  )
}

const meta = { title: "Native/Use cases/Data table", component: Here, args: { platform: "windows", theme: "light" } } satisfies Meta<typeof Here>
export default meta
type Story = StoryObj<typeof meta>

export const Windows: Story = {}
export const Mac: Story = { args: { platform: "mac" } }
export const Dark: Story = { args: { theme: "dark" } }
export const ServerSide: Story = { render: () => <OnTheServer /> }
