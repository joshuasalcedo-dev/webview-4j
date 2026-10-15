# @webview4j/api

The page's side of a webview-4j app: call Java commands and listen to what Java sends.

```ts
import { invoke, listen, InvokeError } from "@webview4j/api";

const user = await invoke<User>("users.find", { id: 42 });
const stop = listen<Order>("orders.updated", (order) => render(order));
```

The Java side:

```java
app.handle("users.find", call -> users.find(call.number("id")));   // a value, a record, or a CompletableFuture
view.emit("orders.updated", order);
```

## Controllers

For data, Java declares routes with annotations, and the webview-4j annotation processor
generates a typed client for them; the page never builds a request by hand:

```java
@WebViewController("/users")
public class UserController {
    @Get("/{id}") public User find(@Path long id) { ... }
    @Post public CompletableFuture<User> create(@Body CreateUser body) { ... }
}
app.register(new UserController());
```

```ts
import { user } from "./generated/api";   // written by the processor: -Awebview4j.typescript=<path>
const found = await user.find(42);          // a User, typed from the Java record
```

The generated client is built on `request(method, path, { query, body })` from this package,
which rejects with a `RequestError` (`method`, `path`, `status`) for an error status.

## Notes

- `invoke` rejects with an `InvokeError` (with the `command`) when the command is unknown, its
  arguments are wrong, or its handler throws.
- Pages can call only commands registered with `App.handle`.
- `isWebView4j()` is false in a plain browser, where `invoke` and `listen` throw.

The package wraps `window.__webview4j`, which webview-4j injects into every page
(`crates/webview/src/bridge.js`); the tests run against that script.

```bash
bun install
bun test           # against crates/webview/src/bridge.js
bun run typecheck
bun run build      # dist/index.js and dist/index.d.ts
```
