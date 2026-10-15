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

/** The methods a route on a Java `@WebViewController` can answer. */
export type HttpMethod = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";

/** What webview-4j injects into every page. */
export interface Bridge {
  invoke(command: string, args?: unknown): Promise<unknown>;
  request(method: HttpMethod, path: string, query?: Record<string, unknown>, body?: unknown): Promise<unknown>;
  listen(topic: string, handler: (data: unknown) => void): () => void;
}

declare global {
  interface Window {
    __webview4j?: Bridge;
  }
}

/**
 * A command Java refused or failed. `status` says how: 400 bad arguments, 404 no such command,
 * 500 its handler failed, 503 the window closed.
 */
export class InvokeError extends Error {
  override readonly name = "InvokeError";

  constructor(
    readonly command: string,
    message: string,
    readonly status: number,
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
 * A page whose origin the window does not allow gets a plain network error instead: the browser
 * hides why it was refused.
 */
export async function invoke<T = unknown>(command: string, args?: unknown): Promise<T> {
  try {
    return (await bridge().invoke(command, args)) as T;
  } catch (error) {
    if (error instanceof Error && error.name === "InvokeError") {
      throw new InvokeError(command, error.message, (error as Error & { status?: number }).status ?? 0);
    }
    throw error;
  }
}

/**
 * A route on a Java controller answered with an error status: 400 a bad parameter or body,
 * 404 no such route (or what the route looked for), 405 the wrong method, 500 it failed.
 */
export class RequestError extends Error {
  override readonly name = "RequestError";

  constructor(
    readonly method: HttpMethod,
    readonly path: string,
    readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

/**
 * Calls a route on a Java `@WebViewController`: data, over the custom protocol. The client the
 * webview-4j annotation processor generates calls this; pages rarely need to themselves.
 * Resolves with the route's JSON answer (null for none); rejects with a {@link RequestError}.
 */
export async function request<T = unknown>(
  method: HttpMethod,
  path: string,
  options: { query?: Record<string, unknown>; body?: unknown } = {},
): Promise<T> {
  try {
    return (await bridge().request(method, path, options.query, options.body)) as T;
  } catch (error) {
    if (error instanceof Error && error.name === "RequestError") {
      throw new RequestError(method, path, (error as Error & { status?: number }).status ?? 0, error.message);
    }
    throw error;
  }
}

/**
 * Calls `handler` with what Java emits on `topic`, until the returned function is called.
 */
export function listen<T = unknown>(topic: string, handler: (data: T) => void): () => void {
  return bridge().listen(topic, handler as (data: unknown) => void);
}
