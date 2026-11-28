# swaswa: what is left

One at a time, in this order: each item lands with real-window tests (no person at the keyboard) and, where it shows,
a section in the showcase (`just ffi-showcase`).

## Next: swaswa-ffi

1. **Immediate mode**, as egui does it: the UI drawn anew from Java's state every frame, no widgets kept. Built on
   the canvas and `animate`: an input stream from the page (mouse, keys, wheel, text, every frame, no reply each) in
   place of `invoke`, and a small immediate-mode API over it (a frame's input in, its drawing out).
2. **Canvas: faster.** A shared-memory buffer the page reads without copies (WebView2 shared buffers); sending only the
   parts that changed. Today: ~50 frames a second of 640×480 RGBA, 1 frame per display refresh with `animate`.
3. **Kiosk mode.** Fullscreen that Escape does not leave (`escapeExitsFullscreen(false)` is the start), no taskbar,
   keys the system would take kept.
4. **Page permissions.** Java decides what a page may use (camera, microphone, location, notifications, clipboard),
   instead of the engine asking the person (WebView2 `PermissionRequested`).
5. **Windows: a number on the taskbar button** (an overlay icon; `setBadge` is macOS only today).

## Then: app-wide features (after the base)

Menus, the system tray, global shortcuts, notifications, single instance, deep links, running with no window open.

## Then: the base, swaswa-core

`BaseComponent`, typed HTML, typed Tailwind, the theme from Tailwind's tokens, Swing's interfaces as the contract, its
own small page script, its generators as Maven plugins; then `swaswa`, what users add. Custom components later in a
`.ui` package. Then `swaswa-test`: a public test kit for apps (find a window, answer a dialog, wait for a page), from
the helpers in swaswa-ffi's tests (`Win32`, `until`).

## Build and release

- The release build of the native library in `swaswa-ffi/native/lib/` (`just native`): it is the dev build now.
- Builds for every system and processor: windows-aarch64, linux-x64/aarch64, macos-x64/aarch64 (CI on a version tag).
- The names left from webview-4j: the library (`webview4j.dll`), `window.__webview4j`, `Assets.SHARED`
  (`webview4j-ui`), `@webview4j/api`, the `webview4j.localhost` origin.
- A `CHANGELOG.md` for swaswa.

## Known issues

- `DialogWindowTest` shows real dialogs: a person's click on one while the tests run still changes the result. Its
  helper presses buttons by the command a click sends (not BM_CLICK, which fails when the dialog is not active, as
  when someone works in another app), and waits for a slow file dialog's OK button.
- `View.focus()` says whether the window came to the front (Windows refuses a background app while the person works
  in another); tests that need the front are skipped then, with that reason, instead of failing.
- A recording that reached its time limit keeps the window's recording slot until `stop()` collects it.
- Harper (grammar) does not flag "This are".
- swaswa-core: a docking component over `Docking` (its look), tooltips on drag handles, an MDI child's title bar.
- Many native "invalid" errors have a reason but no hint yet: add them as they are met.
- The showcase clones its pixel buffer each frame: reuse two in turn.

## Done (this round)

Window decorations, Escape leaves fullscreen, focus that is true (the webview having the keyboard), a profile per
app (`%LOCALAPPDATA%\<app>\webview`, tests and showcase under `target/`), a test for every public method, the canvas
(`drawFrame`) and animation (`animate`), the event loop on its own native thread (a stack overflow fixed), the crash
recorder (`SWASWA_CRASH_DUMPS`), exceptions that say what to do (`WebViewException` and its kinds), OS refusals
(`PermissionException`, `SystemException`, `App.elevated()`), memory (`app.memory()`, `memoryBudget`,
`MemoryPressure`, `setMemoryLevel`), the memory limit (`memoryLimit`: refusals with `ResourceLimitException`;
`App.enforceMemoryLimit`: the system holds the whole app to it), `EngineProcessFailed` (a renderer's crash said, not
a blank page), child processes tied to the app (`app.process`, a Job Object: they end with it, even when it crashes),
background workers (`app.worker`: progress, cancellation, the taskbar's bar), drag and drop (`app.cursor()`,
`app.windowAt`, `windowUnderCursor`, `WindowMove` started/ended, `view.dragFiles` to Explorer or another window;
a page's own drag and drop carries its data between the app's windows). The real-mouse tests run with
`-Dswaswa.mouse=true` (they move the person's pointer). Docking and MDI: `adopt` (a window's live view into
another), `detach`/`tearOff` (out into a window following the mouse), `setFloating`/`toFront` (MDI children moved,
sized and raised inside their window), `Docking` (target area, guides or tab strip; Ctrl keeps it floating; the
highlight and a hint drawn above the host; all configurable), `WindowMove` with MOVING, the pointer and the keys,
`windowAt(x, y, except)`. What a person sees: a grab cursor on drag regions, and the window's state on its page's
<html> (`data-swaswa-active`, `-floating`, `-dragging`, `-dock-target`, `-holding`) for its own styles; the
window dragged over lit as a whole (its frame's colour, `setBorderColor`, and a rim inside it); a dock that worked
settles (the area pulses) and can tick (`Docking.sound`). A docked panel keeps its own size (at most 35% of the host,
at least 160 px) and the host grows by it (`grow`), shrinking back when it leaves; the host's page is told its room
(`--swaswa-dock-left`/`-right`/`-top`/`-bottom`). The page's cursors show: Tao set the arrow over the webview on every
WM_SETCURSOR, and each window's subclass now leaves it to the webview (checked on the screen: `CursorTest`). With the
real mouse: an MDI child stays under the pointer where it was grabbed (five drags), a drag docks (the host grows) or,
with Ctrl, stays floating, a tab tears off and docks back, files drag out, a page's data drags between windows.
