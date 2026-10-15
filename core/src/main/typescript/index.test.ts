/// <reference types="bun" />
import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { InvokeError, invoke, isWebView4j, listen } from "./index.ts";

/** A page with the real bridge script, whose posts go to `posted`. */
function installBridge() {
  const posted: { event: string; payload: { id: number; command: string; args: unknown } }[] = [];
  const page = { ipc: { postMessage: (raw: string) => posted.push(JSON.parse(raw)) } } as unknown as Window;
  (globalThis as { window?: Window }).window = page;
  const script = readFileSync(new URL("../../../../crates/webview/src/bridge.js", import.meta.url), "utf8");
  new Function("window", script)(page);
  const host = page.__webview4j as unknown as {
    resolve(id: number, ok: boolean, value: unknown): void;
    emit(topic: string, data: unknown): void;
  };
  return { posted, host };
}

describe("inside a webview-4j window", () => {
  let page: ReturnType<typeof installBridge>;
  beforeEach(() => {
    page = installBridge();
  });
  afterEach(() => {
    delete (globalThis as { window?: Window }).window;
  });

  test("invoke resolves with what Java answered", async () => {
    const user = invoke<{ name: string }>("users.find", { id: 42 });
    expect(page.posted[0]).toEqual({ event: "invoke", payload: { id: 1, command: "users.find", args: { id: 42 } } });
    page.host.resolve(1, true, { name: "Ada" });
    expect((await user).name).toBe("Ada");
    expect(isWebView4j()).toBe(true);
  });

  test("a failed command rejects with an InvokeError naming it", async () => {
    const failing = invoke("users.broken");
    page.host.resolve(1, false, "the database is down");
    const error = await failing.catch((e: unknown) => e);
    expect(error).toBeInstanceOf(InvokeError);
    expect(error).toMatchObject({ command: "users.broken", message: "the database is down" });
  });

  test("listen receives what Java emits until it stops", () => {
    const seen: number[] = [];
    const stop = listen<{ id: number }>("orders.updated", (order) => seen.push(order.id));
    page.host.emit("orders.updated", { id: 1 });
    stop();
    page.host.emit("orders.updated", { id: 2 });
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
