//! Payloads (PROTOCOL.md §5) and replies (§4). JSON payloads reject unknown keys, so a typo
//! is a `400`, not a setting quietly ignored.

use serde::{Deserialize, Serialize};
use serde_json::Value;

use crate::frame::Message;

/// Payload of `app.open`. `url` wins over `html`; with neither the webview opens blank.
#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct OpenRequest {
    pub url: Option<String>,
    pub html: Option<String>,
    #[serde(default = "default_title")]
    pub title: String,
    #[serde(default = "default_width")]
    pub width: f64,
    #[serde(default = "default_height")]
    pub height: f64,
    #[serde(default = "default_true")]
    pub resizable: bool,
    #[serde(default)]
    pub devtools: bool,
    #[serde(default)]
    pub headless: bool,
    #[serde(default)]
    pub initialization_script: Option<String>,
    #[serde(default)]
    pub data_directory: Option<std::path::PathBuf>,
    /// Native handle (HWND) of the window that owns this one. An owned window stays above its
    /// owner, hides with it and has no taskbar entry, so a host can dock it over its own UI.
    #[serde(default)]
    pub owner_window: Option<isize>,
    /// The view whose window owns this one: it stays above that window, is minimized with it, has no taskbar
    /// entry of its own, and closes before it (Windows: an owned window; macOS: a child window; Linux: transient).
    #[serde(default)]
    pub owner: Option<String>,
    /// `false` removes the title bar and borders.
    #[serde(default = "default_true")]
    pub decorations: bool,
    /// What the inspector reports from the start; only the console unless given.
    #[serde(default)]
    pub capture: Option<CaptureBody>,
    /// The origins whose pages may use the bridge (`invoke`, `listen`), e.g. `https://app.example`.
    /// Unless given: the origin of `url`, or `null` (an opaque origin) for `html`.
    #[serde(default)]
    pub origins: Option<Vec<String>>,
    /// More origins whose pages open in the window when a link goes there (a sign-in page the server sends it
    /// to): any other site opens in the system's browser.
    #[serde(default)]
    pub navigation: Option<Vec<String>>,
    /// The app's own files, by the name Java gave them (a class path directory): `GET <base>app/<path>`
    /// is asked of Java as `view.<view>.asset`. None serves no files.
    #[serde(default)]
    pub assets: Option<String>,
    /// The window's icon, a PNG in base64: in its title bar, the taskbar and the window switcher.
    /// None leaves the program's own.
    #[serde(default)]
    pub icon: Option<String>,
    /// The smallest inner size the user can resize it to, logical pixels.
    #[serde(default)]
    pub min_width: Option<f64>,
    #[serde(default)]
    pub min_height: Option<f64>,
    #[serde(default)]
    pub maximized: bool,
    #[serde(default)]
    pub fullscreen: bool,
    #[serde(default)]
    pub always_on_top: bool,
    /// `true`: the close button sends `view.<view>.close-requested` and leaves the window open;
    /// Java closes it (`view.<view>.close`) when it wants to.
    #[serde(default)]
    pub confirm_close: bool,
    /// `true`: files dragged onto the window are reported (`view.<view>.file-drop`) and the page
    /// does not open them.
    #[serde(default)]
    pub file_drop: bool,
    /// Light or dark for the window's frame and the page's `prefers-color-scheme`; the system's unless given.
    #[serde(default)]
    pub theme: ThemeChoice,
    /// Embeds the webview in this view's window instead of opening a window: at `x`, `y` (logical pixels from
    /// the window's top left), `width` by `height`. Window options (title, frame, size limits...) do not apply.
    #[serde(default)]
    pub parent: Option<String>,
    /// A window opens at `x`, `y` (logical pixels on the desktop), not where the system puts it.
    #[serde(default)]
    pub positioned: bool,
    /// Drawn for Java, not for the person (a JavaFX node shows its page from the DevTools screencast): all but
    /// invisible, and the mouse goes through it. Windows only; elsewhere a normal window.
    #[serde(default)]
    pub offscreen: bool,
    #[serde(default)]
    pub x: f64,
    #[serde(default)]
    pub y: f64,
    /// The largest inner size the user can resize it to, logical pixels.
    #[serde(default)]
    pub max_width: Option<f64>,
    #[serde(default)]
    pub max_height: Option<f64>,
    /// Whether the frame's minimize, maximize and close buttons work.
    #[serde(default = "default_true")]
    pub minimizable: bool,
    #[serde(default = "default_true")]
    pub maximizable: bool,
    #[serde(default = "default_true")]
    pub closable: bool,
    /// No taskbar entry of its own (Windows, Linux).
    #[serde(default)]
    pub skip_taskbar: bool,
    /// On every virtual desktop (macOS, Linux).
    #[serde(default)]
    pub all_workspaces: bool,
    /// Left out of screenshots and screen sharing (Windows, macOS).
    #[serde(default)]
    pub content_protection: bool,
    /// Whether it takes the keyboard's focus when clicked.
    #[serde(default = "default_true")]
    pub focusable: bool,
    /// See-through where the page is (a page with a transparent background): non-rectangular windows.
    #[serde(default)]
    pub transparent: bool,
    /// What shows before the page draws, and where it is transparent: red, green, blue, alpha (0 to 255).
    #[serde(default)]
    pub background_color: Option<[u8; 4]>,
    /// How the title bar looks: `default`; `transparent` (macOS: the bar is see-through, the title hidden, the page
    /// under the traffic lights, which the app's own title bar sits beside). Elsewhere `transparent` is `default`.
    #[serde(default)]
    pub title_bar: TitleBar,
    /// macOS, with `title_bar: transparent`: where the traffic lights are, logical pixels from the top left.
    #[serde(default)]
    pub traffic_lights: Option<[f64; 2]>,
    /// A window without the system's frame keeps the frame's shadow (Windows; default true).
    #[serde(default = "default_true")]
    pub shadow: bool,
    /// Windows 11: the system's material behind the window (`mica`, `acrylic`, `tabbed`), seen where the page is
    /// transparent. None, or an older Windows: none.
    #[serde(default)]
    pub backdrop: Option<Backdrop>,
    /// The page's user agent, in place of the browser's.
    #[serde(default)]
    pub user_agent: Option<String>,
    /// What a link that opens a new window does: `browser` (the system's, the default), `deny`, or `java` (refused,
    /// and reported as `view.<view>.new-window` for Java to decide).
    #[serde(default)]
    pub new_windows: NewWindows,
    /// Downloads: `true` lets the page download (each reported as `view.<view>.download`); `false` refuses them.
    #[serde(default = "default_true")]
    pub downloads: bool,
    /// Keys the window answers itself (`Mod+S`, `Mod+Shift+P`, `F5`): pressed in its page, they do nothing there and
    /// are reported as `view.<view>.shortcut`. `Mod` is Ctrl, or Cmd on macOS.
    #[serde(default)]
    pub shortcuts: Vec<String>,
    /// Escape takes the window out of fullscreen, unless the page used the key itself (default true; a kiosk turns
    /// it off).
    #[serde(default = "default_true")]
    pub escape_exits_fullscreen: bool,
}

/// How a window's title bar looks.
#[derive(Debug, Clone, Copy, Default, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "kebab-case")]
pub enum TitleBar {
    #[default]
    Default,
    Transparent,
}

/// Windows 11's materials behind a window.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "kebab-case")]
pub enum Backdrop {
    None,
    Mica,
    Acrylic,
    Tabbed,
}

/// What a link that opens a new window does.
#[derive(Debug, Clone, Copy, Default, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "kebab-case")]
pub enum NewWindows {
    #[default]
    Browser,
    Deny,
    Java,
}

/// Payload of `view.<view>.window`: an open window's settings to change; keys left out stay as they are. A max size
/// of 0 by 0 takes the limit off.
#[derive(Debug, Clone, Default, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct WindowBody {
    /// The system's frame and title bar, on or off.
    pub decorations: Option<bool>,
    /// The frame's border colour (Windows 11): [r, g, b], or [] for the system's own again.
    pub border_color: Option<Vec<u8>>,
    pub max_width: Option<f64>,
    pub max_height: Option<f64>,
    pub minimizable: Option<bool>,
    pub maximizable: Option<bool>,
    pub closable: Option<bool>,
    pub skip_taskbar: Option<bool>,
    pub all_workspaces: Option<bool>,
    pub content_protection: Option<bool>,
    pub focusable: Option<bool>,
    pub shadow: Option<bool>,
    pub background_color: Option<[u8; 4]>,
    pub backdrop: Option<Backdrop>,
    /// The taskbar's badge: a count (macOS's dock label); 0 takes it off.
    pub badge: Option<i64>,
    /// `critical` or `informational`: the taskbar flashes (the dock bounces) until the app has the focus.
    pub attention: Option<String>,
}

/// Payload of `view.<view>.progress`: the taskbar button's progress.
#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct ProgressBody {
    /// `none`, `normal`, `indeterminate`, `paused` or `error`.
    pub state: String,
    /// 0 to 100.
    #[serde(default)]
    pub value: Option<u64>,
}

/// Payload of `view.<view>.place`: where an embedded view sits in its window, logical pixels.
#[derive(Debug, Clone, Copy, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct PlaceBody {
    pub x: f64,
    pub y: f64,
    pub width: f64,
    pub height: f64,
}

/// `system` follows the system, and changes when it does; `light` and `dark` hold.
#[derive(Debug, Clone, Copy, Default, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "kebab-case")]
pub enum ThemeChoice {
    #[default]
    System,
    Light,
    Dark,
}

fn default_title() -> String {
    "WebView".to_string()
}

fn default_width() -> f64 {
    1024.0
}

fn default_height() -> f64 {
    768.0
}

fn default_true() -> bool {
    true
}

/// Payload of `view.<view>.capture`: each kind of report on or off; kinds left out keep their setting.
#[derive(Debug, Clone, Copy, Default, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct CaptureBody {
    pub api: Option<bool>,
    pub clicks: Option<bool>,
    pub dom_events: Option<bool>,
    pub websocket: Option<bool>,
    pub console: Option<bool>,
    /// Every request, start to end, with headers and bodies (Capture.NETWORK).
    pub network: Option<bool>,
}

/// One open webview, as `app.list` reports it.
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct WebViewInfo {
    pub view: String,
    pub title: String,
    pub url: String,
    pub data_directory: Option<std::path::PathBuf>,
}

/// Cookie values are credentials. Deliberately omit Debug to avoid accidental logging.
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct CookieRecord {
    pub name: String,
    pub value: String,
    pub domain: String,
    pub path: String,
    pub http_only: bool,
    pub secure: bool,
}

/// `view.<view>.cookies.set` and `.delete`: a cookie as Java gives it. To delete, its name, domain and path say
/// which one. Cookie values are credentials: no Debug.
#[derive(Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct CookieBody {
    pub name: String,
    #[serde(default)]
    pub value: String,
    #[serde(default)]
    pub domain: Option<String>,
    #[serde(default)]
    pub path: Option<String>,
    #[serde(default)]
    pub secure: bool,
    #[serde(default)]
    pub http_only: bool,
    /// "Strict", "Lax" or "None"; the browser's default when absent.
    #[serde(default)]
    pub same_site: Option<String>,
    /// Seconds it lives; a session cookie when absent.
    #[serde(default)]
    pub max_age: Option<i64>,
}

impl CookieBody {
    /// The cookie wry sets or deletes; 400 for a name or value a cookie cannot hold.
    pub fn cookie(self) -> Result<wry::cookie::Cookie<'static>, Outcome> {
        let token = |s: &str| !s.is_empty() && s.chars().all(|c| c.is_ascii_graphic() && !"()<>@,;:\\\"/[]?={}".contains(c));
        if !token(&self.name) {
            return Err(Outcome::error(400, "a cookie's name is a token: letters, digits and !#$%&'*+-.^_`|~"));
        }
        if self.value.chars().any(|c| c.is_control() || c == ';' || c == ',' || c == '"' || c == '\\' || c == ' ') {
            return Err(Outcome::error(400, "a cookie's value cannot hold spaces, ; , \" \\ or control characters"));
        }
        let mut cookie = wry::cookie::Cookie::new(self.name, self.value);
        if let Some(domain) = self.domain.filter(|d| !d.is_empty()) {
            cookie.set_domain(domain);
        }
        cookie.set_path(self.path.filter(|p| !p.is_empty()).unwrap_or_else(|| "/".into()));
        cookie.set_secure(self.secure);
        cookie.set_http_only(self.http_only);
        match self.same_site.as_deref() {
            None => {}
            Some("Strict") => cookie.set_same_site(wry::cookie::SameSite::Strict),
            Some("Lax") => cookie.set_same_site(wry::cookie::SameSite::Lax),
            Some("None") => cookie.set_same_site(wry::cookie::SameSite::None),
            Some(other) => return Err(Outcome::error(400, format!("sameSite is Strict, Lax or None: {other}"))),
        }
        if let Some(seconds) = self.max_age {
            cookie.set_max_age(wry::cookie::time::Duration::seconds(seconds));
        }
        Ok(cookie)
    }
}

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
pub struct SizeBody {
    pub width: f64,
    pub height: f64,
}

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
pub struct PositionBody {
    pub x: f64,
    pub y: f64,
}

/// Position and size in one step, in physical screen pixels.
#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
pub struct BoundsBody {
    pub x: i32,
    pub y: i32,
    pub width: u32,
    pub height: u32,
}

/// Payload of `view.<view>.state`: what to change; keys left out stay as they are.
#[derive(Debug, Clone, Copy, Default, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct StateBody {
    pub minimized: Option<bool>,
    pub maximized: Option<bool>,
    pub fullscreen: Option<bool>,
    pub always_on_top: Option<bool>,
    pub resizable: Option<bool>,
}

/// The reply to `view.<view>.geometry`: where the window is and how it is shown. Position and
/// size in logical pixels (the outer position, the inner size).
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Geometry {
    pub x: f64,
    pub y: f64,
    pub width: f64,
    pub height: f64,
    pub scale_factor: f64,
    pub minimized: bool,
    pub maximized: bool,
    pub fullscreen: bool,
    pub focused: bool,
    pub visible: bool,
}

/// One monitor, in the asking window's logical pixels (reply of `view.<id>.monitors`).
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Monitor {
    pub name: String,
    pub x: f64,
    pub y: f64,
    pub width: f64,
    pub height: f64,
    pub scale_factor: f64,
    pub primary: bool,
    pub current: bool,
}

/// Payload of `app.dialog.file`.
#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct FileDialogBody {
    pub kind: FileDialogKind,
    pub title: Option<String>,
    pub directory: Option<std::path::PathBuf>,
    pub file_name: Option<String>,
    #[serde(default)]
    pub filters: Vec<FileFilter>,
    /// The view whose window the dialog belongs to (modal to it); none for a free one.
    pub view: Option<String>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "kebab-case")]
pub enum FileDialogKind {
    Open,
    OpenMany,
    Save,
    Folder,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct FileFilter {
    pub name: String,
    pub extensions: Vec<String>,
}

/// Payload of `app.dialog.message`.
#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct MessageDialogBody {
    #[serde(default)]
    pub title: String,
    pub text: String,
    #[serde(default)]
    pub level: MessageLevel,
    #[serde(default)]
    pub buttons: MessageButtons,
    pub view: Option<String>,
}

#[derive(Debug, Clone, Copy, Default, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "kebab-case")]
pub enum MessageLevel {
    #[default]
    Info,
    Warning,
    Error,
}

#[derive(Debug, Clone, Copy, Default, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "kebab-case")]
pub enum MessageButtons {
    #[default]
    Ok,
    OkCancel,
    YesNo,
    YesNoCancel,
}

/// How an action went: a `Status` and a payload (PROTOCOL.md §4).
#[derive(Debug, Clone, PartialEq)]
pub struct Outcome {
    pub status: u16,
    pub content_type: &'static str,
    pub body: Vec<u8>,
    /// An error's kind, for Java to throw the matching exception (`Error-Kind`); from the status unless set.
    pub kind: Option<&'static str>,
    /// What the app's developer can do about an error (`Hint`): one line.
    pub hint: Option<String>,
    /// More said with an error: the system's error code (`Os-Error`), whether administrator rights would help
    /// (`Elevation`).
    pub headers: Vec<(&'static str, String)>,
}

impl Outcome {
    pub fn ok() -> Self {
        Outcome { status: 200, content_type: "application/json", body: Vec::new(), kind: None, hint: None, headers: Vec::new() }
    }

    pub fn json(value: &impl Serialize) -> Self {
        Outcome { status: 200, content_type: "application/json", body: serde_json::to_vec(value).expect("serializable"), kind: None, hint: None, headers: Vec::new() }
    }

    /// An error: `status` with a message saying what went wrong.
    pub fn error(status: u16, message: impl Into<String>) -> Self {
        Outcome { status, content_type: "text/plain", body: message.into().into_bytes(), kind: None, hint: None, headers: Vec::new() }
    }

    /// The error's kind (Java's exception): `view-closed`, `unsupported`, `invalid`, `conflict`, `page`,
    /// `not-found`, `unavailable` or `internal`.
    pub fn kind(mut self, kind: &'static str) -> Self {
        self.kind = Some(kind);
        self
    }

    /// What to do about the error, for whoever reads the exception: one line.
    pub fn hint(mut self, hint: impl Into<String>) -> Self {
        self.hint = Some(hint.into().replace(['\r', '\n'], " "));
        self
    }

    /// One more header with the error.
    pub fn header(mut self, name: &'static str, value: impl Into<String>) -> Self {
        self.headers.push((name, value.into()));
        self
    }

    /// The kind an error of this status is, unless it said.
    fn kind_or_default(&self) -> &'static str {
        self.kind.unwrap_or(match self.status {
            400 | 415 => "invalid",
            403 => "permission",
            404 => "not-found",
            409 => "conflict",
            410 => "view-closed",
            501 => "unsupported",
            502 | 503 => "unavailable",
            _ => "internal",
        })
    }

    /// The reply to send to `reply_to`.
    pub fn to(self, reply_to: &str) -> Message {
        let mut message = Message::new(reply_to).header("Status", self.status.to_string()).header("Content-Type", self.content_type);
        if self.status != 200 {
            message = message.header("Error-Kind", self.kind_or_default());
            if let Some(hint) = &self.hint {
                message = message.header("Hint", hint.clone());
            }
            for (name, value) in &self.headers {
                message = message.header(name, value.clone());
            }
        }
        message.payload(self.body)
    }
}

/// A JSON payload; empty reads as `{}`.
pub fn json<T: serde::de::DeserializeOwned>(payload: &[u8]) -> Result<T, Outcome> {
    let payload = if payload.iter().all(u8::is_ascii_whitespace) { b"{}".as_slice() } else { payload };
    serde_json::from_slice(payload).map_err(|e| Outcome::error(400, format!("invalid payload: {e}")))
}

/// Any JSON value; empty reads as null.
pub fn value(payload: &[u8]) -> Result<Value, Outcome> {
    if payload.iter().all(u8::is_ascii_whitespace) {
        return Ok(Value::Null);
    }
    serde_json::from_slice(payload).map_err(|e| Outcome::error(400, format!("the payload is not JSON: {e}")))
}

/// A text payload.
pub fn text(payload: &[u8]) -> Result<&str, Outcome> {
    std::str::from_utf8(payload).map_err(|_| Outcome::error(400, "the payload must be UTF-8 text"))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn replies_carry_status_and_content_type() {
        let reply = Outcome::error(404, "no view 'wv-9'").to("_inbox.3");
        assert_eq!(reply.subject, "_inbox.3");
        assert_eq!(reply.get("status"), Some("404"));
        assert_eq!(reply.get("content-type"), Some("text/plain"));
        assert_eq!(reply.payload, b"no view 'wv-9'");
    }

    #[test]
    fn payloads_refuse_unknown_keys_and_bad_json() {
        assert_eq!(json::<SizeBody>(br#"{"width":1,"height":2,"depth":3}"#).err().unwrap().status, 400);
        assert_eq!(value(b"{oops").err().unwrap().status, 400);
        assert_eq!(value(b" ").unwrap(), Value::Null);
        assert!(json::<CaptureBody>(b"").is_ok());
    }

    #[test]
    fn dialog_payloads() {
        let file = json::<FileDialogBody>(
            br#"{"kind":"open-many","title":"Pick","filters":[{"name":"Images","extensions":["png","jpg"]}],"view":"wv-1"}"#,
        )
        .unwrap();
        assert_eq!(file.kind, FileDialogKind::OpenMany);
        assert_eq!(file.filters[0].extensions, ["png", "jpg"]);
        assert_eq!(json::<FileDialogBody>(br#"{"kind":"browse"}"#).err().unwrap().status, 400);

        let message = json::<MessageDialogBody>(br#"{"text":"Sure?","buttons":"yes-no","level":"warning"}"#).unwrap();
        assert_eq!((message.buttons, message.level), (MessageButtons::YesNo, MessageLevel::Warning));
        assert_eq!(json::<MessageDialogBody>(br#"{"title":"no text"}"#).err().unwrap().status, 400);
    }

    #[test]
    fn state_and_window_options() {
        let state = json::<StateBody>(br#"{"maximized":true,"alwaysOnTop":false}"#).unwrap();
        assert_eq!((state.maximized, state.always_on_top, state.fullscreen), (Some(true), Some(false), None));
        let open = json::<OpenRequest>(br#"{"minWidth":400,"minHeight":300,"confirmClose":true,"fileDrop":true}"#).unwrap();
        assert_eq!((open.min_width, open.min_height), (Some(400.0), Some(300.0)));
        assert!(open.confirm_close && open.file_drop && !open.maximized);
        assert_eq!(open.theme, ThemeChoice::System);
        assert_eq!(json::<OpenRequest>(br#"{"theme":"dark"}"#).unwrap().theme, ThemeChoice::Dark);
        assert_eq!(json::<OpenRequest>(br#"{"theme":"sepia"}"#).err().unwrap().status, 400);
        let embed = json::<OpenRequest>(br#"{"url":"https://example.com","parent":"wv-1","x":10,"y":20,"width":300,"height":200}"#).unwrap();
        assert_eq!((embed.parent.as_deref(), embed.x, embed.y, embed.width), (Some("wv-1"), 10.0, 20.0, 300.0));
        assert!(json::<PlaceBody>(br#"{"x":1,"y":2,"width":3}"#).is_err(), "a place needs all four");
        let owned = json::<OpenRequest>(br#"{"title":"Tool","owner":"wv-1"}"#).unwrap();
        assert_eq!((owned.owner.as_deref(), owned.owner_window), (Some("wv-1"), None));
        assert_eq!(json::<OpenRequest>(br#"{}"#).unwrap().owner, None, "not owned unless asked");
    }
}
