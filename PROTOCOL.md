# webview-4j protocol, version 1

How Java, the native side (Rust) and, from step 3, the page talk to each other. It is modelled on
[NATS](https://docs.nats.io/reference/reference-protocols/nats-protocol): a message is a
**subject** to route on, **headers** to describe it, and a **payload** of bytes that nobody in the
middle reads. Each side implements this document on its own; the fixtures in
[`protocol/fixtures`](protocol/fixtures) are the conformance tests every implementation runs.

> **Internal.** Apps never see any of this: Java apps use `App` and `View`, pages use
> `@webview4j/api`. This document is for the implementations (`crates/webview` in Rust, the
> package-private `Message`/`Native`/`App` internals in Java), and for anyone writing another one.
>
> Status: implemented between Java and the native side (FFM), and between the page and the native
> side over the `webview4j` custom protocol (§2.4). Inspector reports still come over the webview's
> IPC: they only report, they cannot call anything.

## 1. Messages

A message has:

| Part | What it is |
|---|---|
| subject | where it goes, e.g. `view.wv-1.eval` (§2) |
| headers | name/value pairs, e.g. `Reply-To: _inbox.42` (§3) |
| payload | bytes, possibly empty, described by `Content-Type`; passed on unread by anyone routing it |

### 1.1 Encoding

One encoding, the same over FFM today and over any later transport (a socket, the page):

```
MSG <subject> <header-length> <payload-length>\r\n
<header block>
<payload>
```

- The first line is ASCII: `MSG`, the subject, then two decimal lengths in bytes, single spaces
  between them, ending in `\r\n`.
- The **header block** is zero or more `Name: value\r\n` lines followed by one empty `\r\n` line.
  `header-length` counts the whole block including that final `\r\n`, so it is at least 2.
- The **payload** is exactly `payload-length` bytes, then the frame ends: no trailing `\r\n`.
  Over FFM a frame is one buffer of exactly this size; a stream transport reads frames back to back.
- Lengths have no sign, no leading zeros (except `0` itself) and fit in a signed 64-bit integer.

An empty message to a subject:

```
MSG app.list 2 0\r\n
\r\n
```

### 1.2 Decoding order

So that every implementation names the same error for the same bad frame, check in this order and
stop at the first failure:

1. The first line, up to the first `\r\n` (at most 300 bytes), is printable ASCII and exactly
   `MSG <subject> <header-length> <payload-length>`: `bad-line`.
2. The subject is at most 256 bytes (`too-large`) and valid (§2): `bad-subject`.
3. Both lengths are plain decimals, and `header-length` is at least 2: `bad-length`.
4. The lengths are within the limits (§1.3): `too-large`. Decided from the line alone, before
   reading or buffering the rest.
5. What follows the line is exactly `header-length + payload-length` bytes: fewer is `truncated`,
   more is `bad-length`.
6. The header block is well formed (§3) and ends in an empty line: `bad-header`.

### 1.3 Limits

| Limit | Value | Over it |
|---|---|---|
| subject | 256 bytes | the frame is refused |
| header block | 64 KiB | the frame is refused |
| payload | 64 MiB | the frame is refused, or answered `413` (§4) |

A refused frame is never half-processed: the receiver drops it and, if it can tell who sent it
(§4), answers with `400` or `413`.

## 2. Subjects

- Tokens separated by `.`. A token is one or more of `A-Z a-z 0-9 - _`. No empty tokens, no
  whitespace, no other characters. Case-sensitive.
- Some subjects end in a **tail** of several tokens that is one value, e.g. the command in
  `view.wv-1.invoke.users.find` is `users.find`. Tails are marked `<…tail>` below.
- `_inbox.<n>` subjects are reply addresses a sender makes up (§4); they carry only replies.

### 2.1 From Java to the native side

| Subject | Payload | Reply |
|---|---|---|
| `app.open` | `application/json`: window options (§5.1) | `200`, `{"view":"wv-1"}` |
| `app.list` | none | `200`, a list of views (§5.2) |
| `app.quit` | none | `200`, then every window closes and the loop ends |
| `view.<view>.navigate` | `text/plain`: the URL | `200` |
| `view.<view>.load-html` | `text/html`: the page | `200` |
| `view.<view>.eval` | `text/javascript`: a script | `200`, `application/json`: its value |
| `view.<view>.cookies` | `text/plain`: the URL | `200`, a list of cookies (§5.3) |
| `view.<view>.title` | `text/plain`: the title | `200` |
| `view.<view>.size` | `{"width":800,"height":600}` logical pixels | `200` |
| `view.<view>.position` | `{"x":10,"y":10}` logical pixels | `200` |
| `view.<view>.bounds` | `{"x":0,"y":0,"width":800,"height":600}` physical pixels | `200` |
| `view.<view>.show` / `hide` / `focus` / `close` | none | `200` |
| `view.<view>.capture` | `{"api":true,"clicks":false,...}`, keys left out keep their setting (§5.4) | `200` |
| `view.<view>.websocket.<socket>.send` | `text/plain`: one text frame | `200`, or `404` if the socket is unknown or not open |
| `view.<view>.emit.<…topic>` | `application/json`: the data | `200` once handed to the page |
| `view.<view>.call.<call>` | the answer to a page's call (§2.3) | none |

Every message from Java except `view.<view>.call.<call>` carries `Reply-To`; the native side
answers each exactly once (§4).

### 2.2 From the native side to Java

| Subject | Payload | Notes |
|---|---|---|
| `app.ready` | none | first message; header `Protocol: 1` |
| `app.log` | `{"level":"warn","target":"wry::…","message":"…"}` | native log records; may arrive from any thread |
| `view.<view>.closed` | none | the window closed, by the user or `close` |
| `view.<view>.log` | `{"level":"warn","message":"…"}` | the page's console and uncaught errors (capture `console`) |
| `view.<view>.api-call` | §5.5 | capture `api` |
| `view.<view>.click` | §5.5 | capture `clicks` |
| `view.<view>.dom-event` | §5.5 | capture `domEvents` |
| `view.<view>.websocket` | `{"socket":3,"url":"…","direction":"open|in|out|close","frame":"…"}` | capture `websocket` |
| `view.<view>.invoke.<…command>` | the page's arguments, untouched | always on; header `Reply-To: view.<view>.call.<call>` |
| `view.<view>.request` | the request body, untouched | a page's request to a controller route (§2.4); headers `Method`, `Path` (e.g. `/users/42`, still percent-encoded), `Query` (raw, may be empty), `Content-Type`, `Reply-To`; answered like a call, `204` for no content |
| `view.<view>.asset` | none | a page's request for one of the app's files (§2.4); headers `Root` (the window's `assets`, §5.1), `Path` (checked, e.g. `assets/main.js`), `Reply-To`; answered `200` with the file and its `Content-Type`, or `404` |
| `_inbox.…` | a reply (§4) | |

The `<view>` in a subject is set by the native side from the window the message came from. A page
can never choose it.

### 2.3 Calls from the page

A page's `invoke("users.find", {id: 42})` arrives as `view.wv-1.invoke.users.find` with
`Reply-To: view.wv-1.call.7`. Java answers by sending a message to that subject, with a `Status`
header: `200` and the value, or an error status and a `text/plain` message. The native side
delivers it to the page, which settles its promise. The page's arguments and Java's answer pass
through the native side unread.

### 2.4 The page's side

UI actions take the fast channel, data takes the custom protocol. A call is a UI action when its
arguments are at most 64 KiB; the page decides, Java does not see the difference.

**UI actions: the webview's IPC.** The page posts one frame, `MSG invoke.<…command>` with a `Call`
header (the page's own number for the call). The native side checks the page's origin, sends Java
`view.<view>.invoke.<…command>`, and settles the call with Java's answer by running
`window.__webview4j.settle(<call>, <status>, <value>, <fetch>)` in the page. Everything in that
script is written by the native side as JSON (the status as a number, the value parsed and written
out again), never pasted in as received; an answer that is not JSON settles as `500`. An answer over
64 KiB is settled with `<fetch>` = `calls/<call>` instead, and the page fetches it (below). A
settle script only runs while the page showing is one the window allows.

**Data: the `webview4j` custom protocol**, `https://webview4j.localhost/` on Windows and Android,
`webview4j://localhost/` elsewhere. Nothing outside the webview can reach it, and nothing it
carries is run as a script.

| Request | Becomes |
|---|---|
| `POST invoke/<…command>`, body: the arguments | `view.<view>.invoke.<…command>` to Java, body unread; Java's answer (§2.3) is the response, `Status` and bytes unchanged |
| `POST calls/<call>` | the large answer to an IPC call (above), once |
| `GET`, `POST`, `PUT`, `PATCH`, `DELETE` `api/<path>[?query]` | `view.<view>.request` to Java (§2.2), the body unread; Java's answer is the response. The routes are a `@WebViewController`'s, and the TypeScript client the annotation processor generates calls them |
| `POST events` | held until Java emits a large push; answered with the waiting `view.<view>.emit.<…topic>` messages as frames (§1.1), each with a `Seq` header, `Content-Type: application/x-webview4j-frames` |
| `GET app/<path>` | `view.<view>.asset` to Java (§2.2), for a window opened with `assets`; Java's answer is the response. An empty path or one ending in `/` is `index.html` |

**The app's files** answer before the origin check: a page loading itself and the files it links
to sends no `Origin`. They are the app's code, never its data, and only a window opened with
`assets` serves any (`404` otherwise). A path is refused natively with `400`, before Java sees it,
unless every segment is non-empty, does not start with `.`, and has only letters, digits and
`._-~@+`: so no `..`, no hidden files, no backslashes or drives, and nothing percent-encoded that
could smuggle one in. Java checks the same rule again and serves only directories it opened windows
with. The page's origin (`https://webview4j.localhost` on Windows) is the window's default allowed
origin, so the app's page uses the bridge and the controllers. Browsers send no `Origin` on a
same-origin `GET`, so without one a request's origin is taken from headers pages cannot set:
`Sec-Fetch-Site: same-origin` means the protocol's own origin, and otherwise the origin of the
`Referer`. With neither, the request has no origin, and no window allows it.

- Both channels answer only origins the window allows: by default the origin of the URL it
  opened, or `null` for HTML it was given; `WindowOptions.origins(...)` sets the list. Over IPC a
  refused call settles as `403`; over the custom protocol the browser hides the `403` from the page
  as a failed fetch. Java never sees a refused call.
- Arguments are sent as `text/plain` (a CORS "simple" request, so no preflight per call) and are
  JSON by the bridge's rule; the native side labels them `application/json` for Java.
- **Pushes** (`view.<view>.emit.<…topic>`) take the same split. One with at most 64 KiB of data is
  run in the page as `window.__webview4j.deliver(<seq>, <topic>, <data>)`, written by the native
  side as JSON (data that is not JSON is refused with `400`). The small pushes of one round of the
  event loop go out together as one script, and before any call answer that follows them. A larger
  push goes to the held `events` request.
- Every push that goes out is numbered (`<seq>`, or the `Seq` header) in one sequence per window,
  and the page hands them to listeners in that order, whichever channel each took. A number that
  never arrives is skipped after 100 ms.
- Large pushes made before the page has polled once are dropped, as with no listeners. While the
  page polls, up to 16 MiB of them wait for it; beyond that a push fails with `503`.
- When the window closes, the held poll is answered `410` and open calls `503`.

## 3. Headers

- `Name: value` lines. Names are one or more of `A-Z a-z 0-9 -`, compared without regard to case.
  Values are UTF-8 without `\r` or `\n`; spaces around the value are trimmed.
- A name may appear more than once; receivers that expect one value take the first.

| Header | Meaning |
|---|---|
| `Reply-To` | the subject to answer on (§4) |
| `Status` | on replies and call answers: an HTTP status code (§4) |
| `Content-Type` | what the payload is; `application/json` when left out |
| `Protocol` | on `app.ready`: the protocol version, `1` |

Unknown headers are ignored and passed on.

## 4. Replies and errors

A reply is a message sent to the `Reply-To` subject of the message it answers, with a `Status`
header. Its payload is the result (`200`), or a `text/plain` message saying what went wrong.
Senders pick their own `_inbox.<n>` subjects and match replies by them.

| Status | When |
|---|---|
| `200` | done |
| `400` | the payload or subject is malformed, e.g. `size` without `height` |
| `404` | no such view, socket or subject |
| `413` | the payload is over a limit |
| `500` | it failed on the native side, e.g. the webview refused a URL |
| `503` | the event loop is stopping |

Every message that has `Reply-To` gets exactly one reply, unless the loop stops first; then the
sender fails what is still waiting.

## 5. Payloads

JSON payloads are objects with `camelCase` keys. Receivers reject unknown keys in what they are
sent (`400`), so a typo fails loudly instead of being half-understood.

### 5.1 Window options (`app.open`)

| Key | Type | Default |
|---|---|---|
| `url` | string | |
| `html` | string, used when there is no `url` | |
| `title` | string | `"WebView"` |
| `width`, `height` | number, logical pixels | `1024`, `768` |
| `resizable`, `decorations` | boolean | `true` |
| `devtools`, `headless` | boolean | `false` |
| `initializationScript` | string | |
| `dataDirectory` | absolute path | |
| `ownerWindow` | integer, native handle (Windows) | |
| `capture` | §5.4 | console only |
| `origins` | array of origins | the origin of `url`, or `null` |
| `assets` | string, a class path directory Java serves the app's files from | none |
| `icon` | string, a PNG in base64 (1 MB at most): the window's icon in its title bar, the taskbar and the window switcher; one that is not a PNG is ignored with a warning | the program's icon |

### 5.2 Views (`app.list`)

`[{"view":"wv-1","title":"…","url":"…","dataDirectory":"…" or null}]`

### 5.3 Cookies (`view.<view>.cookies`)

`[{"name":"…","value":"…","domain":"…","path":"…","httpOnly":true,"secure":true}]`. Values are
credentials: implementations must not log them.

### 5.4 Capture switches

`{"api":bool,"clicks":bool,"domEvents":bool,"websocket":bool,"console":bool}`. Only `console` is
on unless set.

### 5.5 Page reports

As the inspector scripts write them, minus any `viewId` or `sessionKey` the page claims:

- `api-call`: `method`, `url`, `status`, `durationMs`, `requestBody`, `responseBody`,
  `requestHeaders`, `responseHeaders`, `cookies`
- `click`: `selector`, `targetSelector`, `tag`, `text`, `x`, `y`, `button`, and what describes the
  element (`id`, `classes`, `role`, `ariaLabel`, `href`, `title`, `dataAttrs`)
- `dom-event`: `kind` (`click`, `hover`, `input`, `keyDown`, …), `element` (a snapshot), `x`, `y`,
  `button`, `key`

## 6. The FFM transport

```c
int32_t wv_run(void (*on_message)(const uint8_t *frame, size_t length));
int32_t wv_send(const uint8_t *frame, size_t length);
const char *wv_version(void);
```

- `wv_run` runs the event loop on the calling thread and calls `on_message` with each frame from
  the native side; the buffer is valid only during the call. Returns when the loop ends, with its
  exit code; `-1` if a loop already runs.
- `wv_send` hands one frame to the loop from any thread and returns at once: `0` taken, `1` no loop
  is running, `2` the frame is malformed or over a limit. Everything else, including errors about
  what the frame asks for, comes back as a reply.
- `wv_quit`, `wv_call`, `wv_submit`, `wv_actions` and `wv_free` go away: `app.quit` and replies
  replace them.

## 7. Conformance

[`protocol/fixtures`](protocol/fixtures) holds the cases every implementation must pass:

- `frames.json`: frames and what they decode to, or the error they must raise, and frames
  implementations must encode exactly.
- `subjects.json`: valid and invalid subjects.
- `headers.json`: valid and invalid header blocks.

Error kinds are names, not messages: `bad-line`, `bad-subject`, `bad-header`, `bad-length`,
`truncated`, `too-large`.
