//! A window's icon: the PNG Java sends (in base64, in `app.open`'s payload), as the RGBA pixels
//! the window system takes.

use base64::Engine;
use tao::window::Icon;

/// The largest icon accepted, in bytes of PNG: an icon is small.
const MOST: usize = 1024 * 1024;

/// The icon a base64 PNG is.
pub fn from_png_base64(text: &str) -> Result<Icon, String> {
    let png = base64::engine::general_purpose::STANDARD
        .decode(text.trim())
        .map_err(|e| format!("not base64: {e}"))?;
    from_png(&png)
}

/// The icon a PNG is: 8-bit RGB, RGBA, gray or gray with alpha, any palette expanded.
pub fn from_png(png: &[u8]) -> Result<Icon, String> {
    let (rgba, width, height) = rgba(png)?;
    Icon::from_rgba(rgba, width, height).map_err(|e| format!("not an icon: {e}"))
}

fn rgba(png: &[u8]) -> Result<(Vec<u8>, u32, u32), String> {
    if png.len() > MOST {
        return Err(format!("{} bytes: an icon is {MOST} at most", png.len()));
    }
    let mut decoder = png::Decoder::new(std::io::Cursor::new(png));
    decoder.set_transformations(png::Transformations::normalize_to_color8());
    let mut reader = decoder.read_info().map_err(|e| format!("not a PNG: {e}"))?;
    let mut pixels = vec![0; reader.output_buffer_size()];
    let frame = reader.next_frame(&mut pixels).map_err(|e| format!("not a PNG: {e}"))?;
    let pixels = &pixels[..frame.buffer_size()];
    let rgba = match frame.color_type {
        png::ColorType::Rgba => pixels.to_vec(),
        png::ColorType::Rgb => pixels.chunks_exact(3).flat_map(|p| [p[0], p[1], p[2], 255]).collect(),
        png::ColorType::GrayscaleAlpha => pixels.chunks_exact(2).flat_map(|p| [p[0], p[0], p[0], p[1]]).collect(),
        png::ColorType::Grayscale => pixels.iter().flat_map(|&g| [g, g, g, 255]).collect(),
        other => return Err(format!("a PNG of {other:?} pixels")),
    };
    Ok((rgba, frame.width, frame.height))
}

#[cfg(test)]
mod tests {
    use super::*;

    /// A 2x1 PNG: one opaque red pixel, one half-transparent blue one.
    fn two_pixels() -> Vec<u8> {
        let mut png = Vec::new();
        {
            let mut encoder = png::Encoder::new(&mut png, 2, 1);
            encoder.set_color(png::ColorType::Rgba);
            encoder.set_depth(png::BitDepth::Eight);
            let mut writer = encoder.write_header().unwrap();
            writer.write_image_data(&[255, 0, 0, 255, 0, 0, 255, 128]).unwrap();
        }
        png
    }

    #[test]
    fn a_png_is_its_pixels_in_rgba() {
        let (pixels, width, height) = rgba(&two_pixels()).unwrap();
        assert_eq!((width, height), (2, 1));
        assert_eq!(pixels, vec![255, 0, 0, 255, 0, 0, 255, 128]);
        let text = base64::engine::general_purpose::STANDARD.encode(two_pixels());
        assert!(from_png_base64(&text).is_ok());
    }

    #[test]
    fn what_is_not_a_png_is_refused_with_why() {
        assert!(from_png_base64("not base64!").unwrap_err().starts_with("not base64"));
        let text = base64::engine::general_purpose::STANDARD.encode(b"GIF89a");
        assert!(from_png_base64(&text).unwrap_err().starts_with("not a PNG"));
    }
}
