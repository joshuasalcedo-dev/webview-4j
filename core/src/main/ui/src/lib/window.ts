/**
 * The window this page is in, when it runs in a webview-4j app (window.__webview4j.window): what a title bar of its
 * own needs in a window opened without the system's frame (WindowOptions.decorations(false)). Outside webview-4j
 * (a browser, Storybook) every action does nothing.
 */
type NativeWindow = {
  minimize(): void
  toggleMaximize(): void
  close(): void
  startDrag(): void
}

const bridge = (): NativeWindow | undefined =>
  (globalThis as { __webview4j?: { window?: NativeWindow } }).__webview4j?.window

export const currentWindow: NativeWindow = {
  minimize: () => bridge()?.minimize(),
  toggleMaximize: () => bridge()?.toggleMaximize(),
  /** As the close button does: with WindowOptions.confirmClose(true), Java decides. */
  close: () => bridge()?.close(),
  startDrag: () => bridge()?.startDrag(),
}

/** Whether the page runs in a webview-4j window. */
export const inWebview4j = (): boolean => bridge() !== undefined
