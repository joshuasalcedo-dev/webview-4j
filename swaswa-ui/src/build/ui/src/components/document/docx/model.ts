// What the Word components read from a document's model (@extend-ai/react-docx's DocModel): its headings, its
// blocks, its sections, where a text is. Types only from the library, so this loads without it.
import type { DocModel, DocNode, ParagraphNode, TableNode } from "@extend-ai/react-docx"

/** A paragraph's text. */
export function paragraphText(paragraph: ParagraphNode): string {
  return paragraph.children
    .map((child) => (child.type === "text" ? child.text : child.type === "form-field" ? (child.value ?? "") : ""))
    .join("")
}

/** A table's text, cell by cell. */
export function tableText(table: TableNode): string {
  return table.rows
    .map((row) => row.cells.map((cell) => cell.nodes.map(nodeText).join(" ")).join("\t"))
    .join("\n")
}

/** A body node's text: a paragraph's, or a table's. */
export function nodeText(node: DocNode): string {
  return node.type === "paragraph" ? paragraphText(node) : tableText(node)
}

/** A heading in the document's outline. */
export type DocxHeading = { nodeIndex: number; level: number; text: string }

/** The headings (Heading 1 to 6, and the Title), in order: the document's outline. */
export function docxOutline(model: DocModel): DocxHeading[] {
  const out: DocxHeading[] = []
  model.nodes.forEach((node, nodeIndex) => {
    if (node.type !== "paragraph") return
    const level = node.style?.headingLevel ?? (node.style?.styleId === "Title" ? 0 : undefined)
    if (level === undefined) return
    const text = paragraphText(node).trim()
    if (text) out.push({ nodeIndex, level, text })
  })
  return out
}

/** What a layout block is. */
export type DocxBlockType = "title" | "heading" | "paragraph" | "list" | "table" | "figure" | "header" | "footer"

/** A block of the document, as a layout reader (OCR) lists them: a heading, a paragraph, a list item, a table. */
export type DocxBlock = {
  id: string
  type: DocxBlockType
  text: string
  /** Where it is in the body (header and footer blocks have none). */
  nodeIndex?: number
  /** A heading's level (1 to 6), a list item's depth (0 first). */
  level?: number
  /** A table's size. */
  rows?: number
  columns?: number
}

/** The document's blocks: its header, every body paragraph, list item, table and picture, its footer. */
export function docxBlocks(model: DocModel): DocxBlock[] {
  const out: DocxBlock[] = []
  const first = model.metadata.sections?.[0]
  const headers = first?.headerSections ?? model.metadata.headerSections
  const footers = first?.footerSections ?? model.metadata.footerSections
  const region = (type: "header" | "footer", nodes: DocNode[] | undefined, n: number) => {
    const text = (nodes ?? []).map(nodeText).join(" ").trim()
    if (text) out.push({ id: `${type}-${n}`, type, text })
  }
  headers.slice(0, 1).forEach((h, n) => region("header", h.nodes, n))
  model.nodes.forEach((node, nodeIndex) => {
    const id = `node-${nodeIndex}`
    if (node.type === "table") {
      out.push({
        id,
        type: "table",
        text: tableText(node),
        nodeIndex,
        rows: node.rows.length,
        columns: Math.max(0, ...node.rows.map((row) => row.cells.length)),
      })
      return
    }
    const text = paragraphText(node).trim()
    const images = node.children.filter((child) => child.type === "image")
    if (!text) {
      if (images.length)
        out.push({ id, type: "figure", text: images.map((image) => image.alt ?? "").join(" ").trim(), nodeIndex })
      return
    }
    const style = node.style
    if (style?.styleId === "Title") out.push({ id, type: "title", text, nodeIndex })
    else if (style?.headingLevel) out.push({ id, type: "heading", text, nodeIndex, level: style.headingLevel })
    else if (style?.numbering) out.push({ id, type: "list", text, nodeIndex, level: style.numbering.ilvl })
    else out.push({ id, type: "paragraph", text, nodeIndex })
    if (images.length) out.push({ id: `${id}-figure`, type: "figure", text: images.map((image) => image.alt ?? "").join(" ").trim(), nodeIndex })
  })
  footers.slice(0, 1).forEach((f, n) => region("footer", f.nodes, n))
  return out
}

/** A section of the document: its own page size and orientation, its own header and footer. */
export type DocxSection = {
  index: number
  startNodeIndex: number
  endNodeIndex: number
  /** The page, in inches. */
  widthIn?: number
  heightIn?: number
  orientation: "portrait" | "landscape"
  headers: number
  footers: number
  /** Its first heading, or its first words. */
  title: string
}

const twipsAttr = (xml: string, element: string, attribute: string) => {
  const match = new RegExp(`<w:${element}\\b[^>]*\\bw:${attribute}="(\\d+)"`).exec(xml)
  return match ? Number(match[1]) : undefined
}

/** The document's sections (one, when it has no section breaks). */
export function docxSections(model: DocModel): DocxSection[] {
  const sections = model.metadata.sections?.length
    ? model.metadata.sections
    : [{
        startNodeIndex: 0,
        sectionPropertiesXml: model.metadata.sectionPropertiesXml,
        headerSections: model.metadata.headerSections,
        footerSections: model.metadata.footerSections,
      }]
  return sections.map((section, index) => {
    const xml = section.sectionPropertiesXml ?? ""
    const w = twipsAttr(xml, "pgSz", "w")
    const h = twipsAttr(xml, "pgSz", "h")
    const end = (sections[index + 1]?.startNodeIndex ?? model.nodes.length) - 1
    let title = ""
    for (let i = section.startNodeIndex; i <= end && !title; i++) {
      const node = model.nodes[i]
      if (node?.type === "paragraph" && node.style?.headingLevel) title = paragraphText(node).trim()
    }
    for (let i = section.startNodeIndex; i <= end && !title; i++) {
      const node = model.nodes[i]
      if (node) title = nodeText(node).trim().slice(0, 60)
    }
    return {
      index,
      startNodeIndex: section.startNodeIndex,
      endNodeIndex: Math.max(section.startNodeIndex, end),
      widthIn: w ? w / 1440 : undefined,
      heightIn: h ? h / 1440 : undefined,
      orientation: /w:orient="landscape"/.test(xml) || (w && h && w > h) ? "landscape" : "portrait",
      headers: section.headerSections.length,
      footers: section.footerSections.length,
      title: title || `Section ${index + 1}`,
    }
  })
}

/** A place a searched text is found: the body node, and the match's start in its text. */
export type DocxMatch = { nodeIndex: number; offset: number; length: number; snippet: string }

/** Where `query` is in the body (case is ignored), in order. */
export function searchDocx(model: DocModel, query: string, limit = 1000): DocxMatch[] {
  const needle = query.trim().toLocaleLowerCase()
  if (!needle) return []
  const out: DocxMatch[] = []
  model.nodes.forEach((node, nodeIndex) => {
    if (out.length >= limit) return
    const text = nodeText(node)
    const lower = text.toLocaleLowerCase()
    for (let at = lower.indexOf(needle); at !== -1 && out.length < limit; at = lower.indexOf(needle, at + needle.length)) {
      const from = Math.max(0, at - 30)
      out.push({
        nodeIndex,
        offset: at,
        length: needle.length,
        snippet: (from > 0 ? "…" : "") + text.slice(from, at + needle.length + 40).replace(/\s+/g, " "),
      })
    }
  })
  return out
}
