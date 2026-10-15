package io.joshuasalcedo.webview4j;

/**
 * A log line. From the {@code NATIVE} side (the library, Tao, Wry; {@code target} is the Rust
 * module, {@code view} null), or from a {@code PAGE}: its {@code console.*} calls and uncaught
 * errors ({@link Capture#CONSOLE}, on by default; {@code target} null).
 */
public record LogRecord(Source source, String view, Level level, String target, String message)
        implements WebViewEvent {

    public enum Source { NATIVE, PAGE }

    public enum Level {
        ERROR, WARN, INFO, DEBUG, TRACE;

        /** The matching {@link System.Logger} level, for forwarding. */
        public System.Logger.Level toSystemLevel() {
            return switch (this) {
                case ERROR -> System.Logger.Level.ERROR;
                case WARN -> System.Logger.Level.WARNING;
                case INFO -> System.Logger.Level.INFO;
                case DEBUG -> System.Logger.Level.DEBUG;
                case TRACE -> System.Logger.Level.TRACE;
            };
        }
    }
}
