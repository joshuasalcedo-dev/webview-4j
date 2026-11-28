package io.joshuasalcedo.swaswa.ffi;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * A recording of one of the app's windows, under way ({@link View#record}): {@link #stop()} finishes its file. Only
 * the app's own windows are recorded, never the screen or another program.
 * <pre>{@code
 * Recording rec = view.record(Path.of("docs/tour.gif"), Recording.Options.gif().fps(12).scale(0.6)).join();
 * ... // drive the window
 * Recording.Result done = rec.stop().join();   // docs/tour.gif, its frames, seconds, size
 * }</pre>
 */
public final class Recording {

    /** What a recording is written as. */
    public enum Format {
        /** An animated GIF, made by webview-4j itself: for a README, a bug report. */
        GIF,
        /** H.264 in MP4: the system's own encoder on Windows (Media Foundation), else ffmpeg on the PATH. */
        MP4,
        /** VP9 in WebM, through ffmpeg on the PATH. */
        WEBM
    }

    /** How it is recorded: the format, frames per second (10 unless set), a smaller size, a time limit. */
    public record Options(Format format, int fps, double scale, int maxSeconds) {

        public static Options gif() {
            return new Options(Format.GIF, 10, 1.0, 600);
        }

        public static Options mp4() {
            return new Options(Format.MP4, 30, 1.0, 600);
        }

        public static Options webm() {
            return new Options(Format.WEBM, 30, 1.0, 600);
        }

        public Options fps(int fps) { return new Options(format, fps, scale, maxSeconds); }
        /** 0.1 to 1: smaller pictures (a GIF for a README: 0.5 to 0.7). */
        public Options scale(double scale) { return new Options(format, fps, scale, maxSeconds); }
        /** It stops by itself after this long. */
        public Options maxSeconds(int seconds) { return new Options(format, fps, scale, seconds); }

        Map<String, Object> payload(Path file) {
            return Map.of("action", "start", "path", file.toAbsolutePath().toString(), "format", format.name().toLowerCase(Locale.ROOT),
                    "fps", fps, "scale", scale, "maxSeconds", maxSeconds);
        }
    }

    /** A finished recording: its file, how many frames, how long, its size. */
    public record Result(Path file, long frames, double seconds, int width, int height) {}

    private final View view;
    private final Path file;

    Recording(View view, Path file) {
        this.view = view;
        this.file = file;
    }

    public Path file() {
        return file;
    }

    /** Stops and finishes the file. */
    @SuppressWarnings("unchecked")
    public CompletableFuture<Result> stop() {
        return view.app().request(view.message("record").withJson(Map.of("action", "stop"))).thenApply(reply -> {
            Map<String, Object> m = (Map<String, Object>) reply.json();
            return new Result(Path.of(String.valueOf(m.get("path"))), ((Number) m.get("frames")).longValue(),
                    ((Number) m.get("seconds")).doubleValue(), ((Number) m.get("width")).intValue(), ((Number) m.get("height")).intValue());
        });
    }
}
