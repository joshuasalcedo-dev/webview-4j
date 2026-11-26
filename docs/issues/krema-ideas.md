# Ideas worth borrowing from Krema: permissions, a plugin contract, a dev error overlay

**Labels:** `enhancement` `design`  ·  **Reference:** `krema/` in this repository (Krema, "Tauri for Java")

## Read first

- **Ideas and API shapes only, no code.** Krema is under **BSL 1.1** (Business Source License): source-available, not
  open source. Its code must not be copied into webview-4j. What follows describes the ideas, written our way.
- **Not a Tauri clone.** webview-4j's direction stays its own: a typed, Swing-style Java UI over the kit's components
  (see #1). Krema has no Java component UI at all; it is IPC plus native APIs. So only its non-UI ideas apply.

## What Krema has that webview-4j lacks

### 1. Permissions, declared and typed (recommended)

Krema has a `Permission` enum (`fs:read`, `fs:write`, `fs:*`, `clipboard:read`, `shell:execute`, `notification`…,
where `fs:*` implies the `fs:` ones), `@RequiresPermission({...})` on a command, and a checker that refuses a call the
app has not been granted.

webview-4j today: embedded pages are kept from the bridge unless their origins are named (`Capture.API`). There is no
model of *what* a page or plugin may do.

Wanted, our way:
- A `Permission` type (enum or sealed interface) in core.
- `@RequiresPermission` on `@WebViewController` methods, checked by the generated `_Routes`, which the annotation
  processor already writes, so the check costs no reflection.
- Grants given in Java when a window is opened (`WindowOptions.permissions(...)`), not in a config file.
- A refused call answers a typed error to the page and is logged in the dev tools.

### 2. A plugin contract (recommended)

Krema has a `KremaPlugin` interface (id, init with a `PluginContext`, shutdown), a `PluginContext` that gives a
plugin what it may use (commands, events, config), a manifest, and a loader. Its built-ins (fs, log, updater, deep
link) are plugins too.

webview-4j today: the use cases are packages (`plugin.authentication`, `plugin.autoupdate`, `plugin.collection`…)
with no common interface and no lifecycle. The plan already noted is that each use case gets interfaces to implement
(an `AuthenticationProvider` first).

Wanted, our way:
- A `WebviewPlugin` interface with a lifecycle tied to `WebviewApplication` (`init`, `start(App)`, `stop`). Each
  plugin is registered in Java (`app.install(new AutoUpdate(...))`), discoverable by `ServiceLoader` for add-ons.
- A `PluginContext` with what a plugin may reach: its controllers, events, storage, and its permissions (1).
- The built-in use cases implement it, and the dev tools list installed plugins.

### 3. A dev error overlay (recommended, small)

Krema's dev mode shows compile and runtime errors over the page (`ErrorOverlay`), with its file watcher and dev
server.

webview-4j today: `LiveReload` recompiles on save. A failed compile is logged, and the app keeps running the old
classes, so the window shows nothing of it. A failing handler shows the `UiException` notice.

Wanted: while developing, a failed live-reload compile is drawn over the window (file, line, message; gone once it
compiles), and so is an exception from `start` or a handler. Built with the kit's own components.

### 4. Ports for native capabilities (maybe)

Krema puts each native capability behind an interface (`WindowPort`, `DialogPort`, `MenuPort`,
`NotificationPort`, `ScreenPort`, `GlobalShortcutPort`, `DockPort`), with one implementation per OS.

webview-4j today: native calls go through `View` / `App` to the Rust library.

Worth it only if apps need to test without a window: a port lets a test give a fake dialog or tray. Decide before
doing it.

## Not worth borrowing

- **`@KremaCommand` and its TypeScript generation.** `@WebViewController` with `WebViewProcessor` already does this
  (routes without reflection, a typed `api.ts`).
- **Its events (`on` / `off` / `once`).** The bridge has `listen`. Krema pastes JSON into an `eval` string. webview-4j's
  native side parses and writes the JSON out again, which is safer.
- **`krema.toml` configuration and the CLI.** Configuration stays in typed Java.

## Done when

- [ ] Permissions: a typed `Permission`, `@RequiresPermission` checked in the generated routes, grants in
      `WindowOptions`, and a typed refusal.
- [ ] Plugins: a `WebviewPlugin` lifecycle and `PluginContext`, the built-in use cases on it, listed in the dev tools.
- [ ] A dev error overlay for live-reload compile errors and start/handler exceptions.
- [ ] A decision on ports (4), recorded here.
