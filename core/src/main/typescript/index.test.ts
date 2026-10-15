/// <reference types="bun" />
import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { InvokeError, invoke, isWebView4j, listen } from "./index.ts";

const BASE = "https://webview4j.localhost/";

type Pending = { url: string; body: unknown; answer(response: Response): void };

/** A page with the real bridge script; its fetches wait in `requests` until the test answers. */
function installBridge() {
  const requests: Pending[] = [];
  const page = {} as Window;
  (globalThis as { window?: Window }).window = page;
  const fetch = (url: string, init: RequestInit) =>
    new Promise<Response>((answer) => requests.push({ url, body: init.body, answer }));
  const script = readFileSync(new URL("../../../../crates/webview/src/bridge.js", import.meta.url), "utf8")
    .replace("__WEBVIEW4J_BASE__", BASE);
  new Function("window", "fetch", script)(page, fetch);
  return requests;
}

/** A batch of emits as Rust sends it: protocol frames. */
function frames(topic: string, data: unknown): Uint8Array<ArrayBuffer> {
  const payload = new TextEncoder().encode(JSON.stringify(data));
  const block = "Content-Type: application/json\r\n\r\n";
  const line = `MSG view.wv-1.emit.${topic} ${block.length} ${payload.length}\r\n`;
  return new Uint8Array([...new TextEncoder().encode(line + block), ...payload]);
}

const tick = () => new Promise((resolve) => setTimeout(resolve, 0));

describe("inside a webview-4j window", () => {
  let requests: Pending[];
  beforeEach(() => {
    requests = installBridge();
  });
  afterEach(() => {
    delete (globalThis as { window?: Window }).window;
  });

  test("invoke resolves with what Java answered", async () => {
    const user = invoke<{ name: string }>("users.find", { id: 42 });
    await tick();
    expect(requests[0]!.url).toBe(BASE + "invoke/users.find");
    expect(requests[0]!.body).toBe('{"id":42}');
    requests[0]!.answer(new Response('{"name":"Ada"}', { status: 200 }));
    expect((await user).name).toBe("Ada");
    expect(isWebView4j()).toBe(true);
  });

  test("a failed command rejects with an InvokeError naming it, with its status", async () => {
    const failing = invoke("users.broken");
    await tick();
    requests[0]!.answer(new Response("the database is down", { status: 500 }));
    const error = await failing.catch((e: unknown) => e);
    expect(error).toBeInstanceOf(InvokeError);
    expect(error).toMatchObject({ command: "users.broken", message: "the database is down", status: 500 });
  });

  test("listen receives what Java emits until it stops", async () => {
    const seen: number[] = [];
    const stop = listen<{ id: number }>("orders.updated", (order) => seen.push(order.id));
    await tick();
    requests[0]!.answer(new Response(frames("orders.updated", { id: 1 }), { status: 200 }));
    await tick();
    await tick();
    stop();
    requests[1]!.answer(new Response(frames("orders.updated", { id: 2 }), { status: 200 }));
    await tick();
    await tick();
    expect(seen).toEqual([1]);
  });
});

describe("outside webview-4j", () => {
  test("calls fail with a clear message, and isWebView4j says why", async () => {
    expect(isWebView4j()).toBe(false);
    await expect(invoke("users.find")).rejects.toThrow("not running in a webview-4j window");
    expect(() => listen("orders.updated", () => {})).toThrow("not running in a webview-4j window");
  });
});
