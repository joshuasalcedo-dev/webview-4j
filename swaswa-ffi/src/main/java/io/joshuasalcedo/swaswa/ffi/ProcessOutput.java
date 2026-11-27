package io.joshuasalcedo.swaswa.ffi;

/**
 * A line a child process wrote ({@link App#process(String, ProcessBuilder)}), without its line break. A stream's lines
 * come in their order; lines of its output and of its errors each in theirs (not interleaved as written).
 *
 * @param process the name the app gave it
 */
public record ProcessOutput(String process, long pid, Stream stream, String line) implements WebViewEvent {

    public enum Stream { OUTPUT, ERROR }
}
