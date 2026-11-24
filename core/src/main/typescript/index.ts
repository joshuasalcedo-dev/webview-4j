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
  /** The page's own window (see {@link currentWindow}). */
  window: NativeWindow;
}

/** What a page can do to its own window: for a title bar of its own, in a window without the system's frame. */
export interface NativeWindow {
  minimize(): void;
  /** Maximizes the window, or restores it if it is maximized. */
  toggleMaximize(): void;
  /** As the close button does: with `WindowOptions.confirmClose(true)`, Java decides. */
  close(): void;
  /** Starts moving the window with the mouse: call it on a mouse-down. */
  startDrag(): void;
}

/**
 * Where the routes answer as a plain HTTP API (Java's `HttpApi`): its base URL, such as
 * `http://127.0.0.1:52144/api` or `https://books.example.com/api`, and the bearer token it wants.
 */
export interface HttpConnection {
  baseUrl: string;
  token?: string;
}

declare global {
  interface Window {
    __webview4j?: Bridge;
    /** Set by a shell that starts the Java side as an HTTP API, such as a Tauri app. */
    __webview4j_http?: HttpConnection;
  }
}

let http: HttpConnection | undefined;

/**
 * Sends {@link request} to a plain HTTP API with `fetch` instead of a webview-4j window: from a
 * browser, another desktop shell, or a server. A page a shell opened with `window.__webview4j_http`
 * set is connected already.
 */
export function connect(connection: HttpConnection): void {
  http = { baseUrl: connection.baseUrl.replace(/\/+$/, ""), token: connection.token };
}

function httpConnection(): HttpConnection | undefined {
  if (http) return http;
  if (typeof window !== "undefined" && window.__webview4j_http) connect(window.__webview4j_http);
  return http;
}

/** A route over HTTP: the same URL, query and body rules as the window's own channel. */
async function fetchRoute(connection: HttpConnection, method: HttpMethod, path: string, query?: Record<string, unknown>, body?: unknown) {
  if (!path.startsWith("/")) throw new TypeError("a route path starts with /");
  const search: string[] = [];
  for (const [name, value] of Object.entries(query ?? {})) {
    if (value === undefined || value === null) continue;
    for (const item of Array.isArray(value) ? value : [value]) {
      search.push(encodeURIComponent(name) + "=" + encodeURIComponent(String(item)));
    }
  }
  const headers: Record<string, string> = {};
  if (connection.token) headers.Authorization = "Bearer " + connection.token;
  const init: RequestInit = { method, headers };
  if (body !== undefined && method !== "GET") {
    init.body = JSON.stringify(body);
    headers["Content-Type"] = "application/json";
  }
  const response = await fetch(connection.baseUrl + path + (search.length ? "?" + search.join("&") : ""), init);
  const text = await response.text();
  if (response.status >= 400) throw new RequestError(method, path, response.status, text);
  return response.status === 204 || text === "" ? null : JSON.parse(text);
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
  // Once connected to an HTTP API, it answers, even in a webview-4j window.
  const connection = httpConnection();
  if (connection) return (await fetchRoute(connection, method, path, options.query, options.body)) as T;
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

/**
 * The window this page is in. Outside a webview-4j window (a browser) every action does nothing. Elements with
 * `data-webview4j-drag-region` move the window when dragged, and maximize or restore it on a double click, without
 * any code.
 */
export const currentWindow: NativeWindow = {
  minimize: () => window.__webview4j?.window?.minimize(),
  toggleMaximize: () => window.__webview4j?.window?.toggleMaximize(),
  close: () => window.__webview4j?.window?.close(),
  startDrag: () => window.__webview4j?.window?.startDrag(),
};

// ---- TanStack Query: Java's state and the page's queries, one cache's worth of changes ----

/** The topic Java tells pages on when server state changed (webview-4j's ServerState.invalidate, a Mutation). */
export const INVALIDATE_TOPIC = "webview4j.state.invalidate";

/**
 * Whether a query key matches a webview-4j key pattern, as Java's do: the key's parts joined with dots
 * (`["orders", 2]` is `orders.2`), `*` one part, `>` one or more at the end.
 */
export function keyMatches(pattern: string, queryKey: readonly unknown[]): boolean {
  const key = queryKey.map((part) => (typeof part === "object" && part !== null ? JSON.stringify(part) : String(part)).replaceAll(" ", "_"))
  const parts = pattern.split(".")
  for (let i = 0; i < parts.length; i++) {
    if (parts[i] === ">") return i === parts.length - 1 && key.length > i
    if (i >= key.length) return false
    if (parts[i] !== "*" && parts[i] !== key[i]) return false
  }
  return parts.length === key.length
}

/** The little of TanStack Query's QueryClient this needs (so @webview4j/api does not depend on it). */
export interface InvalidatingClient {
  invalidateQueries(filters: { predicate: (query: { queryKey: readonly unknown[] }) => boolean }): unknown
}

/**
 * Refreshes the page's TanStack queries whose key matches what Java says changed: one line, once, where the
 * QueryClient is made. Java's `ServerState.invalidate("orders.>")` (or a Mutation's) then refreshes both the Java
 * screens and the page. Returns what stops it.
 * ```ts
 * const queryClient = new QueryClient()
 * invalidateFromJava(queryClient)
 * ```
 */
export function invalidateFromJava(client: InvalidatingClient): () => void {
  if (!isWebView4j()) return () => {}
  return listen<{ pattern: string }>(INVALIDATE_TOPIC, ({ pattern }) => {
    client.invalidateQueries({ predicate: (query) => keyMatches(pattern, query.queryKey) })
  })
}

/**
 * A TanStack query of a Java command: the key is the command's name and its arguments, so Java can invalidate it
 * by name (`ServerState.invalidate("orders")` refreshes `javaQuery("orders")`).
 * ```ts
 * const orders = useQuery(javaQuery<Order[]>("orders", { page: 2 }))
 * ```
 */
export function javaQuery<T>(command: string, args?: unknown): { queryKey: unknown[]; queryFn: () => Promise<T> } {
  return { queryKey: args === undefined ? [command] : [command, args], queryFn: () => invoke<T>(command, args) }
}
