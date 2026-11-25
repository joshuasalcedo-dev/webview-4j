//! MP4 (H.264) written by Windows' own encoder, Media Foundation's sink writer: no ffmpeg, nothing shipped, the codec
//! licensed with the system, hardware encoding where the machine has it. Used by capture.rs for recordings.

use windows::Win32::Media::MediaFoundation::*;
use windows::Win32::System::Com::{COINIT_MULTITHREADED, CoInitializeEx, CoUninitialize};
use windows::core::{HSTRING, Result};

/// An MP4 being written, frame by frame, on the thread that made it.
pub struct Mp4 {
    writer: IMFSinkWriter,
    stream: u32,
    width: u32,
    height: u32,
    duration: i64,
    frames: i64,
}

/// The sides H.264 wants: even.
pub fn even(n: u32) -> u32 {
    (n & !1).max(2)
}

impl Mp4 {
    /// Starts `path`: `width` by `height` (even), `fps` frames a second.
    pub fn create(path: &str, width: u32, height: u32, fps: u32) -> Result<Mp4> {
        unsafe {
            CoInitializeEx(None, COINIT_MULTITHREADED).ok()?;
            MFStartup(MF_VERSION, MFSTARTUP_FULL)?;
            let mut attributes: Option<IMFAttributes> = None;
            MFCreateAttributes(&mut attributes, 1)?;
            let attributes = attributes.expect("attributes");
            attributes.SetUINT32(&MF_READWRITE_ENABLE_HARDWARE_TRANSFORMS, 1)?;
            let writer = MFCreateSinkWriterFromURL(&HSTRING::from(path), None, &attributes)?;

            let size = ((width as u64) << 32) | height as u64;
            let rate = ((fps as u64) << 32) | 1;
            let square = (1u64 << 32) | 1;

            let out = MFCreateMediaType()?;
            out.SetGUID(&MF_MT_MAJOR_TYPE, &MFMediaType_Video)?;
            out.SetGUID(&MF_MT_SUBTYPE, &MFVideoFormat_H264)?;
            // About 0.15 bits a pixel a frame: sharp text, small files.
            out.SetUINT32(&MF_MT_AVG_BITRATE, ((width as u64 * height as u64 * fps as u64) / 6).clamp(500_000, 40_000_000) as u32)?;
            out.SetUINT32(&MF_MT_INTERLACE_MODE, MFVideoInterlace_Progressive.0 as u32)?;
            out.SetUINT64(&MF_MT_FRAME_SIZE, size)?;
            out.SetUINT64(&MF_MT_FRAME_RATE, rate)?;
            out.SetUINT64(&MF_MT_PIXEL_ASPECT_RATIO, square)?;
            let stream = writer.AddStream(&out)?;

            let input = MFCreateMediaType()?;
            input.SetGUID(&MF_MT_MAJOR_TYPE, &MFMediaType_Video)?;
            input.SetGUID(&MF_MT_SUBTYPE, &MFVideoFormat_RGB32)?;
            input.SetUINT32(&MF_MT_INTERLACE_MODE, MFVideoInterlace_Progressive.0 as u32)?;
            input.SetUINT64(&MF_MT_FRAME_SIZE, size)?;
            input.SetUINT64(&MF_MT_FRAME_RATE, rate)?;
            input.SetUINT64(&MF_MT_PIXEL_ASPECT_RATIO, square)?;
            // A positive stride: rows top to bottom, as the frames are.
            input.SetUINT32(&MF_MT_DEFAULT_STRIDE, width * 4)?;
            writer.SetInputMediaType(stream, &input, None)?;
            writer.BeginWriting()?;
            Ok(Mp4 { writer, stream, width, height, duration: 10_000_000 / fps.max(1) as i64, frames: 0 })
        }
    }

    /// One frame: RGBA, rows top to bottom, at least the video's size (cut to it).
    pub fn write(&mut self, rgba: &[u8], frame_width: u32) -> Result<()> {
        unsafe {
            let len = self.width * self.height * 4;
            let buffer = MFCreateMemoryBuffer(len)?;
            let mut data: *mut u8 = std::ptr::null_mut();
            buffer.Lock(&mut data, None, None)?;
            let out = std::slice::from_raw_parts_mut(data, len as usize);
            for y in 0..self.height as usize {
                let from = &rgba[y * frame_width as usize * 4..][..self.width as usize * 4];
                let to = &mut out[y * self.width as usize * 4..][..self.width as usize * 4];
                for (s, d) in from.chunks_exact(4).zip(to.chunks_exact_mut(4)) {
                    d[0] = s[2];
                    d[1] = s[1];
                    d[2] = s[0];
                    d[3] = 255;
                }
            }
            buffer.Unlock()?;
            buffer.SetCurrentLength(len)?;
            let sample = MFCreateSample()?;
            sample.AddBuffer(&buffer)?;
            sample.SetSampleTime(self.frames * self.duration)?;
            sample.SetSampleDuration(self.duration)?;
            self.writer.WriteSample(self.stream, &sample)?;
            self.frames += 1;
            Ok(())
        }
    }

    /// Finishes the file.
    pub fn finish(self) -> Result<()> {
        unsafe {
            let done = self.writer.Finalize();
            drop(self.writer);
            let _ = MFShutdown();
            CoUninitialize();
            done
        }
    }
}
