// Clicking again grows the selection, the same in every text box and every piece of text people can select:
// double-click a word (the browser does that), three clicks the sentence, four the paragraph, five everything.
// Browsers on their own jump from the word straight to the paragraph; NativeProvider calls this on every press.

/** Where a span of text starts and ends (end not included). */
type Span = [number, number]

const segmenters = new Map<string, Intl.Segmenter>()
function segmenter(
  granularity: "sentence" | "word"
): Intl.Segmenter | undefined {
  if (typeof Intl === "undefined" || !("Segmenter" in Intl)) return undefined
  let found = segmenters.get(granularity)
  if (!found) {
    found = new Intl.Segmenter(undefined, { granularity })
    segmenters.set(granularity, found)
  }
  return found
}

/** The sentence around a place in a text, without the spaces after it (in the text's language rules). */
function sentenceAt(text: string, offset: number): Span {
  const sentences = segmenter("sentence")
  if (sentences) {
    for (const piece of sentences.segment(text)) {
      const end = piece.index + piece.segment.length
      if (offset >= piece.index && (offset < end || end === text.length)) {
        return [piece.index, piece.index + piece.segment.trimEnd().length]
      }
    }
  }
  // No Segmenter: up to the nearest . ! ? on each side.
  const before = Math.max(
    text.lastIndexOf(". ", offset - 1),
    text.lastIndexOf("! ", offset - 1),
    text.lastIndexOf("? ", offset - 1)
  )
  const after = text.slice(offset).search(/[.!?](\s|$)/)
  return [
    before === -1 ? 0 : before + 2,
    after === -1 ? text.length : offset + after + 1,
  ]
}

/** The paragraph around a place in a multi-line text: between empty lines, or the line itself. */
function paragraphAt(text: string, offset: number): Span {
  const start = text.lastIndexOf("\n", offset - 1) + 1
  const next = text.indexOf("\n", offset)
  return [start, next === -1 ? text.length : next]
}

const TEXT_INPUTS = new Set([
  "text",
  "search",
  "url",
  "email",
  "tel",
  "password",
  "",
])

/** Grows the selection in a text box. */
function growInBox(
  box: HTMLInputElement | HTMLTextAreaElement,
  clicks: number
): boolean {
  if (box instanceof HTMLInputElement && !TEXT_INPUTS.has(box.type))
    return false
  const text = box.value
  // After a double-click the word is selected; its start is where the pointer was.
  const offset = box.selectionStart ?? 0
  let span: Span
  if (clicks === 3) span = sentenceAt(text, offset)
  else if (clicks === 4 && box instanceof HTMLTextAreaElement)
    span = paragraphAt(text, offset)
  else span = [0, text.length]
  box.setSelectionRange(span[0], span[1])
  return true
}

/** The nearest element around a node that is laid out as a block (a paragraph, a list item, a cell). */
function blockAround(node: Node, limit: Element): HTMLElement {
  let element = node instanceof HTMLElement ? node : node.parentElement
  while (element && element !== limit) {
    const display = getComputedStyle(element).display
    if (!display.startsWith("inline") && display !== "contents") return element
    element = element.parentElement
  }
  return limit as HTMLElement
}

/** The place in the page under the pointer, as a text node and an offset. */
function caretAt(
  x: number,
  y: number
): { node: Node; offset: number } | undefined {
  const doc = document as Document & {
    caretPositionFromPoint?: (
      x: number,
      y: number
    ) => { offsetNode: Node; offset: number } | null
  }
  const position = doc.caretPositionFromPoint?.(x, y)
  if (position) return { node: position.offsetNode, offset: position.offset }
  const range = document.caretRangeFromPoint?.(x, y)
  return range
    ? { node: range.startContainer, offset: range.startOffset }
    : undefined
}

/** A DOM range for characters start to end of an element's text. */
function rangeOf(element: Element, [start, end]: Span): Range {
  const range = document.createRange()
  const walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT)
  let seen = 0
  let startSet = false
  for (let node = walker.nextNode(); node; node = walker.nextNode()) {
    const length = node.textContent?.length ?? 0
    if (!startSet && start <= seen + length) {
      range.setStart(node, start - seen)
      startSet = true
    }
    if (startSet && end <= seen + length) {
      range.setEnd(node, end - seen)
      return range
    }
    seen += length
  }
  range.selectNodeContents(element)
  return range
}

/** Grows the selection in text on the page (an editor, or text marked selectable). */
function growInText(
  event: MouseEvent,
  target: Element,
  clicks: number
): boolean {
  const editable = target.closest(
    '[contenteditable=""], [contenteditable="true"]'
  )
  if (!editable && getComputedStyle(target).userSelect === "none") return false
  // "Everything": the editor, or the selectable piece of text (.nt-selectable), or the block.
  const whole = editable ?? target.closest(".nt-selectable") ?? target
  const caret = caretAt(event.clientX, event.clientY)
  if (!caret || !whole.contains(caret.node)) return false
  const block = blockAround(caret.node, whole)

  let element: Element = block
  let span: Span
  if (clicks >= 5 || (clicks === 4 && block === whole)) {
    element = whole
    span = [0, whole.textContent?.length ?? 0]
  } else if (clicks === 4) {
    span = [0, block.textContent?.length ?? 0]
  } else {
    const before = document.createRange()
    before.setStart(block, 0)
    before.setEnd(caret.node, caret.offset)
    span = sentenceAt(block.textContent ?? "", before.toString().length)
  }
  const selection = window.getSelection()
  if (!selection) return false
  selection.removeAllRanges()
  selection.addRange(rangeOf(element, span))
  return true
}

/**
 * On a press of the main button: from the third click on, selects the sentence, then the paragraph, then
 * everything, and stops the browser's own (paragraph) selection. Leaves code editors alone (they select the
 * line, as IDEs do). True when it selected something.
 */
function growSelection(event: MouseEvent): boolean {
  const clicks = event.detail
  if (clicks < 3 || event.button !== 0) return false
  const target = event.target as Element | null
  if (!target || target.closest(".monaco-editor")) return false
  const done =
    target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement
      ? growInBox(target, clicks)
      : growInText(event, target, clicks)
  if (done) event.preventDefault()
  return done
}

export { growSelection, paragraphAt, sentenceAt }
