/**
 * @webview4j/api: the page's side of a webview-4j app.
 *
 * ```ts
 * import { invoke, listen } from "@webview4j/api";
 *
 * const user = await invoke<User>("users.find", { id: 42 });    // App.handle("users.find", ...) in Java
 * const stop = listen<Order>("orders.updated", order => render(order));   // view.emit(...) in Java
 * ```
 *
 * It wraps `window.__webview4j`, which webview-4j injects into every page it opens.
 */

/** What webview-4j injects into every page. */
export interface Bridge {
  invoke(command: string, args?: unknown): Promise<unknown>;
  listen(topic: string, handler: (data: unknown) => void): () => void;
}

declare global {
  interface Window {
    __webview4j?: Bridge;
  }
}

/** A command Java refused or failed: unknown, bad arguments, or its handler threw. */
export class InvokeError extends Error {
  override readonly name = "InvokeError";

  constructor(
    readonly command: string,
    message: string,
  ) {
    super(message);
  }
}

/** True inside a window webview-4j opened; false in a plain browser, e.g. while designing the page. */
export function isWebView4j(): boolean {
  return typeof window !== "undefined" && window.__webview4j !== undefined;
}

function bridge(): Bridge {
  const bridge = typeof window === "undefined" ? undefined : window.__webview4j;
  if (!bridge) throw new Error("@webview4j/api: this page is not running in a webview-4j window");
  return bridge;
}

/**
 * Calls the Java command registered as `command`, passing `args` (anything JSON can carry).
 * Resolves with what the handler returned; rejects with an {@link InvokeError} when it fails.
 */
export async function invoke<T = unknown>(command: string, args?: unknown): Promise<T> {
  try {
    return (await bridge().invoke(command, args)) as T;
  } catch (error) {
    if (error instanceof Error && error.name === "InvokeError") throw new InvokeError(command, error.message);
    throw error;
  }
}

/**
 * Calls `handler` with what Java emits on `topic`, until the returned function is called.
 */
export function listen<T = unknown>(topic: string, handler: (data: T) => void): () => void {
  return bridge().listen(topic, handler as (data: unknown) => void);
}
