//! The app's own sounds: `app.play` (PROTOCOL.md §2.1, §5.6). A sound plays on a thread of its
//! own, so a long one never holds the event loop up.

use std::io::Cursor;

use crate::payload::Outcome;

/// The longest sound `app.play` takes: a notification sound, not music.
pub const MAX_SOUND_BYTES: usize = 4 * 1024 * 1024;

/// `app.play`: an MP3, WAV, Ogg Vorbis or FLAC, played once on the default output device.
pub fn play(audio: &[u8]) -> Outcome {
    if audio.is_empty() || audio.len() > MAX_SOUND_BYTES {
        return Outcome::error(400, format!("a sound is 1 byte to {MAX_SOUND_BYTES} bytes"));
    }
    // Decoded here, so a file that is not a sound is refused rather than failing silently later.
    if let Err(error) = rodio::Decoder::new(Cursor::new(audio.to_vec())) {
        return Outcome::error(415, format!("not a sound this can play: {error}"));
    }
    let audio = audio.to_vec();
    std::thread::Builder::new()
        .name("webview4j-sound".into())
        .spawn(move || {
            let played = (|| -> Result<(), String> {
                let (_stream, output) = rodio::OutputStream::try_default().map_err(|e| e.to_string())?;
                let sink = rodio::Sink::try_new(&output).map_err(|e| e.to_string())?;
                sink.append(rodio::Decoder::new(Cursor::new(audio)).map_err(|e| e.to_string())?);
                sink.sleep_until_end();
                Ok(())
            })();
            if let Err(reason) = played {
                log::warn!("a sound did not play: {reason}");
            }
        })
        .map(|_| Outcome::ok())
        .unwrap_or_else(|error| Outcome::error(500, format!("no thread to play on: {error}")))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn what_is_not_a_sound_is_refused_before_it_plays() {
        assert_eq!(play(b"").status, 400);
        assert_eq!(play(b"hello, not audio").status, 415);
        assert_eq!(play(&vec![0u8; MAX_SOUND_BYTES + 1]).status, 400);
    }
}
