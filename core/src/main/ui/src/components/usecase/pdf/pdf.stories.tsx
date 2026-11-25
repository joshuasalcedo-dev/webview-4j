import type { Meta, StoryObj } from "@storybook/react-vite"
import * as React from "react"

import { bothPlatforms, dark } from "../../../stories/side-by-side"
import { BoundingBoxCitations, type CitationField } from "./bounding-box-citations"
import { createInitialSplits, DocumentSplits, type DocumentSplit } from "./document-splits"
import { FileThumbnail, PdfThumbnail } from "./file-thumbnail"
import { PdfViewer, type PdfViewerHandle } from "./pdf-viewer"
import sample from "./sample/sample.pdf?url"

/** The sample's values, each with its box on the page (percent of the page, as the sample was drawn). */
const FIELDS: CitationField[] = [
  {
    key: "statement_period",
    label: "Statement period",
    description: "Date range covered by the statement.",
    value: "Jan 1-31, 2026",
    expected: "January 1-31, 2026",
    location: { page: 1, area: { left: 11.11, top: 17.17, width: 37.48, height: 2.83 } },
  },
  {
    key: "account_details",
    label: "Account details",
    type: "object",
    value: null,
    fields: [
      { key: "holder_name", label: "Holder name", value: "Jordan Lee", location: { page: 1, area: { left: 11.11, top: 20.2, width: 30.4, height: 2.83 } } },
      { key: "account_type", label: "Account type", value: "Checking", expected: "Premier Checking", location: { page: 1, area: { left: 11.11, top: 23.23, width: 44.63, height: 2.83 } } },
      { key: "mailing_address", label: "Mailing address", value: "42 Market Street, Brooklyn, NY", expected: "42 Market Street, New York, NY", location: { page: 1, area: { left: 11.11, top: 26.26, width: 48.01, height: 2.83 } } },
    ],
  },
  {
    key: "transactions",
    label: "Transactions",
    value: [
      { date: "2026-01-03", description: "ACH CREDIT PAYROLL", amount: 4250 },
      { date: "2026-01-12", description: "POS PURCHASE GROCERY MART", amount: -86.42 },
      { date: "2026-01-19", description: "ONLINE TRANSFER TO SAVINGS", amount: -500 },
      { date: "2026-01-27", description: "CARD PURCHASE CITY BOOKS", amount: -23.4 },
    ],
    location: { page: 1, area: { left: 11.11, top: 39.14, width: 77.78, height: 11.01 } },
  },
  {
    key: "ending_balance",
    label: "Ending balance",
    value: 12840.18,
    expected: 12858.18,
    location: { page: 1, area: { left: 11.11, top: 53.03, width: 77.78, height: 2.98 } },
  },
  {
    key: "overdraft_protection_enabled",
    label: "Overdraft protection enabled",
    value: false,
    expected: true,
    location: { page: 2, area: { left: 11.11, top: 13.38, width: 55.23, height: 2.83 } },
  },
]

function Box({ height = 560, children }: { height?: number; children: React.ReactNode }) {
  return <div style={{ display: "flex", flexDirection: "column", width: "100%", height }}>{children}</div>
}

function Viewer({ thumbnails = false }: { thumbnails?: boolean }) {
  const [page, setPage] = React.useState(1)
  const [pages, setPages] = React.useState(0)
  return (
    <Box>
      <PdfViewer src={sample} fileName="Northwind statement.pdf" defaultShowThumbnails={thumbnails} onActivePageChange={setPage} onDocumentLoadSuccess={setPages} />
      <span style={{ marginTop: 6, color: "var(--nt-text-secondary)" }}>
        Page {page} of {pages || "…"}
      </span>
    </Box>
  )
}

function Thumbnails() {
  return (
    <div style={{ display: "flex", gap: 12, alignItems: "flex-start", flexWrap: "wrap" }}>
      <PdfThumbnail src={sample} fileName="statement.pdf" style={{ width: 120 }} />
      <PdfThumbnail src={sample} page={2} fileName="statement.pdf" style={{ width: 120 }} />
      <FileThumbnail file={{ name: "notes.txt" }} style={{ width: 120 }} />
      <FileThumbnail file={{ name: "photo.jpg" }} isLoading style={{ width: 120 }} />
    </div>
  )
}

function Citations({ review = false }: { review?: boolean }) {
  const [active, setActive] = React.useState<string>()
  return (
    <Box height={600}>
      <BoundingBoxCitations src={sample} fileName="statement.pdf" fields={FIELDS} showExpected={review} activeKey={active} onActiveChange={setActive} viewerProps={{ showUpload: false, defaultZoom: "fit-width" }} />
    </Box>
  )
}

function Splits() {
  const viewer = React.useRef<PdfViewerHandle>(null)
  const [splits, setSplits] = React.useState<DocumentSplit[]>(createInitialSplits(4, 2))
  return (
    <Box height={600}>
      <div style={{ display: "grid", gridTemplateColumns: "minmax(0, 1fr) 320px", gap: 0, height: "100%" }}>
        <PdfViewer ref={viewer} src={sample} showUpload={false} onDocumentLoadSuccess={(n) => setSplits(createInitialSplits(n, 2))} />
        <DocumentSplits src={sample} splits={splits} onSplitsChange={setSplits} onSelectPage={(n) => viewer.current?.scrollToPage(n)} />
      </div>
    </Box>
  )
}

function Example({ show = "viewer" as "viewer" | "thumbnails-open" | "thumbnails" | "citations" | "review" | "splits" | "empty" }) {
  switch (show) {
    case "thumbnails-open":
      return <Viewer thumbnails />
    case "thumbnails":
      return <Thumbnails />
    case "citations":
      return <Citations />
    case "review":
      return <Citations review />
    case "splits":
      return <Splits />
    case "empty":
      return (
        <Box height={320}>
          <PdfViewer />
        </Box>
      )
    default:
      return <Viewer />
  }
}

const meta = {
  title: "Use case/Documents/PDF",
  component: Example,
  decorators: bothPlatforms,
  parameters: { nativeStretch: true },
} satisfies Meta<typeof Example>

export default meta
type Story = StoryObj<typeof meta>

export const Viewer_: Story = { name: "Viewer" }
export const ViewerDark: Story = { name: "Viewer, dark", parameters: { ...dark, nativeStretch: true } }
export const WithThumbnails: Story = { args: { show: "thumbnails-open" } }
export const FileThumbnails: Story = { args: { show: "thumbnails" } }
export const FileThumbnailsDark: Story = { args: { show: "thumbnails" }, parameters: { ...dark, nativeStretch: true } }
export const Citations_: Story = { name: "Bounding box citations", args: { show: "citations" } }
export const Review: Story = { name: "Bounding box citations, review", args: { show: "review" } }
export const ReviewDark: Story = { name: "Review, dark", args: { show: "review" }, parameters: { ...dark, nativeStretch: true } }
export const Splits_: Story = { name: "Document splits", args: { show: "splits" } }
export const SplitsDark: Story = { name: "Document splits, dark", args: { show: "splits" }, parameters: { ...dark, nativeStretch: true } }
export const Empty: Story = { args: { show: "empty" } }
