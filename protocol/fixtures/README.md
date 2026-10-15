# Protocol conformance fixtures

Every implementation of [PROTOCOL.md](../../PROTOCOL.md) (Rust, Java, and the page's TypeScript)
runs these files in its tests. They are the contract between the implementations.

| File | Cases |
|---|---|
| `frames.json` | `decode`: frames and the subject, headers and payload they decode to. `encode`: messages and the exact frame they encode to. `errors`: frames and the error kind they must be refused with (§1.2 fixes the order of checks). |
| `subjects.json` | subjects to accept and to refuse (§2) |
| `headers.json` | header blocks to accept, with their pairs, and to refuse (§3) |

Frames are JSON strings of UTF-8 text (`"frame"`), or base64 (`"frameBase64"`) when the payload is
binary. When adding a case by hand, remember that the lengths on a frame's first line count
**bytes**, not characters, and that an error case should be wrong in one way only: a bad header
block with a wrong length is refused for its length (step 5) before its headers are read (step 6).
