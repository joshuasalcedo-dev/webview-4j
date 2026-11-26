//! Pictures and videos of the app's own windows (never the screen, never another program's windows): a PNG of a
//! window as it looks, title bar and all; a recording of it, as an animated GIF (encoded here) or as MP4 or WebM
//! (frames piped to the system's ffmpeg). On Windows a window is drawn with `PrintWindow(PW_RENDERFULLCONTENT)`, which
//! also catches WebView2's composited page; other systems answer that they cannot yet. MP4 on Windows is written by
//! the system's own encoder (Media Foundation), elsewhere and for WebM by ffmpeg when it is on the PATH.
//!
//! ```text
//! view.<id>.screenshot   {scale?}                                 -> image/png
//! view.<id>.record       {action: "start", path, format, fps, scale, maxSeconds}   -> {}
//!                        {action: "stop"}                         -> {path, frames, seconds, width, height}
//! ```

use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::io::Write;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex, OnceLock};
use std::thread::JoinHandle;
use std::time::{Duration, Instant};

/// A picture: width, height, and RGBA bytes, rows top to bottom.
pub struct Frame {
    pub width: u32,
    pub height: u32,
    pub rgba: Vec<u8>,
}

impl Frame {
    /// Smaller by `scale` (0.1 to 1): each new pixel the average of the ones it covers.
    pub fn scaled(self, scale: f64) -> Frame {
        if !(scale > 0.0 && scale < 1.0) {
            return self;
        }
        let w = ((self.width as f64 * scale).round() as u32).max(1);
        let h = ((self.height as f64 * scale).round() as u32).max(1);
        let mut out = vec![0u8; (w * h * 4) as usize];
        for y in 0..h {
            let y0 = (y as f64 / scale) as u32;
            let y1 = (((y + 1) as f64 / scale) as u32).clamp(y0 + 1, self.height);
            for x in 0..w {
                let x0 = (x as f64 / scale) as u32;
                let x1 = (((x + 1) as f64 / scale) as u32).clamp(x0 + 1, self.width);
                let mut sum = [0u32; 4];
                let mut n = 0u32;
                for sy in y0..y1.min(self.height) {
                    for sx in x0..x1.min(self.width) {
                        let i = ((sy * self.width + sx) * 4) as usize;
                        for c in 0..4 {
                            sum[c] += self.rgba[i + c] as u32;
                        }
                        n += 1;
                    }
                }
                let o = ((y * w + x) * 4) as usize;
                for c in 0..4 {
                    out[o + c] = (sum[c] / n.max(1)) as u8;
                }
            }
        }
        Frame { width: w, height: h, rgba: out }
    }

    /// The frame as `width` by `height`: cut, or filled with black, from the top left (a window resized mid-recording).
    pub fn fit(self, width: u32, height: u32) -> Frame {
        if self.width == width && self.height == height {
            return self;
        }
        let mut out = vec![0u8; (width * height * 4) as usize];
        for y in 0..height.min(self.height) {
            let n = (width.min(self.width) * 4) as usize;
            let from = (y * self.width * 4) as usize;
            let to = (y * width * 4) as usize;
            out[to..to + n].copy_from_slice(&self.rgba[from..from + n]);
        }
        Frame { width, height, rgba: out }
    }

    pub fn png(&self) -> Result<Vec<u8>, String> {
        let mut bytes = Vec::new();
        {
            let mut encoder = png::Encoder::new(&mut bytes, self.width, self.height);
            encoder.set_color(png::ColorType::Rgba);
            encoder.set_depth(png::BitDepth::Eight);
            let mut writer = encoder.write_header().map_err(|e| e.to_string())?;
            writer.write_image_data(&self.rgba).map_err(|e| e.to_string())?;
        }
        Ok(bytes)
    }
}

/// The window as it looks now. `hwnd` is the window's handle (Windows).
#[cfg(windows)]
pub fn grab(hwnd: isize) -> Result<Frame, String> {
    use std::mem::{size_of, zeroed};
    use std::ptr::null_mut;
    use windows_sys::Win32::Foundation::RECT;
    use windows_sys::Win32::Graphics::Dwm::{DWMWA_EXTENDED_FRAME_BOUNDS, DwmGetWindowAttribute};
    use windows_sys::Win32::Graphics::Gdi::{
        BI_RGB, BITMAPINFO, BITMAPINFOHEADER, CreateCompatibleDC, CreateDIBSection, DIB_RGB_COLORS, DeleteDC, DeleteObject,
        GetDC, ReleaseDC, SelectObject,
    };
    use windows_sys::Win32::Storage::Xps::PrintWindow;
    use windows_sys::Win32::UI::WindowsAndMessaging::GetWindowRect;

    unsafe {
        let window = hwnd as windows_sys::Win32::Foundation::HWND;
        let mut outer: RECT = zeroed();
        if GetWindowRect(window, &mut outer) == 0 {
            return Err("the window's size could not be read".into());
        }
        // What shows of the window, without the invisible resize borders around it.
        let mut shown: RECT = zeroed();
        let visible = DwmGetWindowAttribute(window, DWMWA_EXTENDED_FRAME_BOUNDS as u32, &mut shown as *mut RECT as *mut _, size_of::<RECT>() as u32) == 0;
        if !visible {
            shown = outer;
        }
        let width = (outer.right - outer.left).max(1);
        let height = (outer.bottom - outer.top).max(1);
        let screen = GetDC(null_mut());
        let dc = CreateCompatibleDC(screen);
        let mut info: BITMAPINFO = zeroed();
        info.bmiHeader.biSize = size_of::<BITMAPINFOHEADER>() as u32;
        info.bmiHeader.biWidth = width;
        info.bmiHeader.biHeight = -height; // top-down rows
        info.bmiHeader.biPlanes = 1;
        info.bmiHeader.biBitCount = 32;
        info.bmiHeader.biCompression = BI_RGB;
        let mut bits: *mut core::ffi::c_void = null_mut();
        let bitmap = CreateDIBSection(dc, &info, DIB_RGB_COLORS, &mut bits, null_mut(), 0);
        if bitmap.is_null() || bits.is_null() {
            DeleteDC(dc);
            ReleaseDC(null_mut(), screen);
            return Err("no memory for the picture".into());
        }
        let old = SelectObject(dc, bitmap);
        // PW_RENDERFULLCONTENT (2): DirectComposition content too, as WebView2's page is.
        let drawn = PrintWindow(window, dc, 2) != 0;
        let all = std::slice::from_raw_parts(bits as *const u8, (width * height * 4) as usize);
        let (x0, y0) = ((shown.left - outer.left).max(0) as u32, (shown.top - outer.top).max(0) as u32);
        let w = ((shown.right - shown.left).max(1) as u32).min(width as u32 - x0);
        let h = ((shown.bottom - shown.top).max(1) as u32).min(height as u32 - y0);
        let mut rgba = Vec::with_capacity((w * h * 4) as usize);
        for y in y0..y0 + h {
            let row = &all[((y * width as u32 + x0) * 4) as usize..((y * width as u32 + x0 + w) * 4) as usize];
            for px in row.chunks_exact(4) {
                rgba.extend_from_slice(&[px[2], px[1], px[0], 255]);
            }
        }
        SelectObject(dc, old);
        DeleteObject(bitmap);
        DeleteDC(dc);
        ReleaseDC(null_mut(), screen);
        if !drawn {
            return Err("the window could not be drawn into a picture".into());
        }
        Ok(Frame { width: w, height: h, rgba })
    }
}

#[cfg(not(windows))]
pub fn grab(_handle: isize) -> Result<Frame, String> {
    Err("capturing a window is not on this system yet (Windows has it)".into())
}

/// `view.<id>.screenshot`'s payload.
#[derive(Debug, Default, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ShotBody {
    #[serde(default)]
    pub scale: Option<f64>,
}

/// `view.<id>.record`'s payload.
#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RecordBody {
    pub action: String,
    #[serde(default)]
    pub path: Option<String>,
    #[serde(default)]
    pub format: Option<String>,
    #[serde(default)]
    pub fps: Option<u32>,
    #[serde(default)]
    pub scale: Option<f64>,
    #[serde(default)]
    pub max_seconds: Option<u64>,
}

/// What a finished recording is.
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Recorded {
    pub path: String,
    pub frames: u64,
    pub seconds: f64,
    pub width: u32,
    pub height: u32,
}

struct Running {
    stop: Arc<AtomicBool>,
    thread: JoinHandle<Result<Recorded, String>>,
}

fn running() -> &'static Mutex<HashMap<String, Running>> {
    static RUNNING: OnceLock<Mutex<HashMap<String, Running>>> = OnceLock::new();
    RUNNING.get_or_init(|| Mutex::new(HashMap::new()))
}

/// Where frames go: a GIF made here, Windows' encoder, or ffmpeg's stdin.
enum Sink {
    Gif(gif::Encoder<std::fs::File>),
    #[cfg(windows)]
    MediaFoundation(crate::mediafoundation::Mp4),
    Ffmpeg(std::process::Child),
}

impl Sink {
    fn open(path: &str, format: &str, fps: u32, width: u32, height: u32) -> Result<Sink, String> {
        match format {
            "gif" => {
                let file = std::fs::File::create(path).map_err(|e| format!("{path} could not be written: {e}"))?;
                let mut encoder = gif::Encoder::new(file, width as u16, height as u16, &[]).map_err(|e| e.to_string())?;
                encoder.set_repeat(gif::Repeat::Infinite).map_err(|e| e.to_string())?;
                Ok(Sink::Gif(encoder))
            }
            #[cfg(windows)]
            "mp4" => match crate::mediafoundation::Mp4::create(path, crate::mediafoundation::even(width), crate::mediafoundation::even(height), fps) {
                Ok(mp4) => Ok(Sink::MediaFoundation(mp4)),
                // Windows without its media features (the N editions): ffmpeg, if there is one.
                Err(_) => Sink::ffmpeg(path, "mp4", fps, width, height),
            },
            #[cfg(windows)]
            "webm" => Sink::ffmpeg(path, format, fps, width, height),
            #[cfg(not(windows))]
            "mp4" | "webm" => Sink::ffmpeg(path, format, fps, width, height),
            other => Err(format!("no recording format '{other}': gif, mp4 or webm")),
        }
    }

    fn ffmpeg(path: &str, format: &str, fps: u32, width: u32, height: u32) -> Result<Sink, String> {
        match format {
            "mp4" | "webm" => {
                let codec: &[&str] = if format == "mp4" {
                    &["-c:v", "libx264", "-preset", "veryfast", "-pix_fmt", "yuv420p", "-movflags", "+faststart"]
                } else {
                    &["-c:v", "libvpx-vp9", "-b:v", "0", "-crf", "34", "-pix_fmt", "yuv420p"]
                };
                // Even sides, as the encoders want them.
                let size = format!("{width}x{height}");
                let child = std::process::Command::new("ffmpeg")
                    .args(["-loglevel", "error", "-y", "-f", "rawvideo", "-pix_fmt", "rgba", "-s", &size, "-r", &fps.to_string(), "-i", "-"])
                    .args(["-vf", "pad=ceil(iw/2)*2:ceil(ih/2)*2"])
                    .args(codec)
                    .arg(path)
                    .stdin(std::process::Stdio::piped())
                    .stdout(std::process::Stdio::null())
                    .stderr(std::process::Stdio::null())
                    .spawn()
                    .map_err(|_| "MP4 and WebM need ffmpeg on the PATH (https://ffmpeg.org); GIF needs nothing".to_string())?;
                Ok(Sink::Ffmpeg(child))
            }
            other => Err(format!("no recording format '{other}': gif, mp4 or webm")),
        }
    }

    fn write(&mut self, frame: &Frame, delay: Duration) -> Result<(), String> {
        match self {
            Sink::Gif(encoder) => {
                let mut rgba = frame.rgba.clone();
                let mut f = gif::Frame::from_rgba_speed(frame.width as u16, frame.height as u16, &mut rgba, 10);
                f.delay = ((delay.as_millis() / 10) as u16).max(2);
                encoder.write_frame(&f).map_err(|e| e.to_string())
            }
            #[cfg(windows)]
            Sink::MediaFoundation(mp4) => mp4.write(&frame.rgba, frame.width).map_err(|e| format!("the video could not be written: {e}")),
            Sink::Ffmpeg(child) => child
                .stdin
                .as_mut()
                .ok_or("ffmpeg's input is closed")?
                .write_all(&frame.rgba)
                .map_err(|e| format!("ffmpeg stopped: {e}")),
        }
    }

    fn finish(self) -> Result<(), String> {
        match self {
            Sink::Gif(encoder) => drop(encoder),
            #[cfg(windows)]
            Sink::MediaFoundation(mp4) => mp4.finish().map_err(|e| format!("the video could not be finished: {e}"))?,
            Sink::Ffmpeg(mut child) => {
                drop(child.stdin.take());
                let status = child.wait().map_err(|e| e.to_string())?;
                if !status.success() {
                    return Err(format!("ffmpeg ended with {status}"));
                }
            }
        }
        Ok(())
    }
}

/// Starts recording the window `hwnd` of view `view` into `body.path`.
pub fn start(view: &str, hwnd: isize, body: RecordBody) -> Result<(), String> {
    let path = body.path.ok_or("a recording needs a path")?;
    let format = body.format.unwrap_or_else(|| "gif".into()).to_lowercase();
    let fps = body.fps.unwrap_or(10).clamp(1, 60);
    let scale = body.scale.unwrap_or(1.0);
    let max = Duration::from_secs(body.max_seconds.unwrap_or(600).clamp(1, 3600));
    let mut all = running().lock().map_err(|_| "recordings are stuck")?;
    if all.contains_key(view) {
        return Err(format!("'{view}' is already being recorded"));
    }
    // The first frame now: it says the size, and a window that cannot be drawn fails here, not later.
    let first = grab(hwnd)?.scaled(scale);
    let (width, height) = (first.width, first.height);
    let stop = Arc::new(AtomicBool::new(false));
    let flag = stop.clone();
    let (opened, is_open) = std::sync::mpsc::channel::<Result<(), String>>();
    let thread = std::thread::Builder::new()
        .name(format!("webview4j-record-{view}"))
        .spawn(move || -> Result<Recorded, String> {
            let mut sink = match Sink::open(&path, &format, fps, width, height) {
                Ok(sink) => {
                    let _ = opened.send(Ok(()));
                    sink
                }
                Err(e) => {
                    let _ = opened.send(Err(e.clone()));
                    return Err(e);
                }
            };
            let began = Instant::now();
            let tick = Duration::from_secs_f64(1.0 / fps as f64);
            let mut frames = 0u64;
            let mut last = Instant::now();
            let mut pending = Some(first);
            while !flag.load(Ordering::Relaxed) && began.elapsed() < max {
                let frame = match pending.take() {
                    Some(f) => f,
                    None => match grab(hwnd) {
                        Ok(f) => f.scaled(scale).fit(width, height),
                        Err(_) => break, // the window closed
                    },
                };
                // How long it shows: the real time since the last one (a slow frame is not played fast).
                let now = Instant::now();
                let shown = if frames == 0 { tick } else { now - last };
                last = now;
                sink.write(&frame, if matches!(sink, Sink::Gif(_)) { shown.max(tick) } else { tick })?;
                frames += 1;
                let next = began + tick * frames as u32;
                if let Some(wait) = next.checked_duration_since(Instant::now()) {
                    std::thread::sleep(wait);
                }
            }
            let seconds = began.elapsed().as_secs_f64();
            sink.finish()?;
            Ok(Recorded { path, frames, seconds, width, height })
        })
        .map_err(|e| e.to_string())?;
    // The file opened (or why not), before saying it records.
    match is_open.recv() {
        Ok(Ok(())) => {}
        Ok(Err(e)) => {
            let _ = thread.join();
            return Err(e);
        }
        Err(_) => return Err("the recording could not start".into()),
    }
    all.insert(view.to_string(), Running { stop, thread });
    Ok(())
}

/// Stops the view's recording; `done` gets the finished file, from a thread of its own. Never on the event loop's
/// thread: a frame being drawn (PrintWindow) waits for that thread, so waiting there for the recording would hang both.
pub fn stop_later(view: &str, done: impl FnOnce(Result<Recorded, String>) + Send + 'static) {
    let run = match running().lock() {
        Ok(mut all) => all.remove(view),
        Err(_) => return done(Err("recordings are stuck".into())),
    };
    let Some(run) = run else {
        return done(Err(format!("'{view}' is not being recorded")));
    };
    run.stop.store(true, Ordering::Relaxed);
    let _ = std::thread::Builder::new().name("webview4j-record-finish".into()).spawn(move || {
        done(run.thread.join().map_err(|_| "the recording stopped badly".to_string()).and_then(|r| r))
    });
}

/// A view closed: its recording is finished as it is (in the background).
pub fn closed(view: &str) {
    stop_later(view, |_| {});
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn frames_scale_fit_and_encode() {
        let f = Frame { width: 4, height: 2, rgba: vec![255; 32] };
        let s = f.scaled(0.5);
        assert_eq!((s.width, s.height), (2, 1));
        let fitted = Frame { width: 2, height: 2, rgba: vec![9; 16] }.fit(3, 1);
        assert_eq!(fitted.rgba.len(), 12);
        assert_eq!(&fitted.rgba[..8], &[9; 8]);
        assert_eq!(&fitted.rgba[8..], &[0; 4]);
        let png = s.png().unwrap();
        assert_eq!(&png[1..4], b"PNG");
    }
}
