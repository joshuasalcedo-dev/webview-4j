package io.joshuasalcedo.webview4j.ui;

import java.util.Objects;

/**
 * Something that went wrong, said for the person using the app: thrown from a handler ({@code onClick},
 * {@code onChange}...), its {@link #userMessage()} shows in a dialog over the screen, which stays as it was. Its
 * {@link #getMessage()} stays technical, for logs; an expected problem like this one is not logged as a failure.
 * <pre>{@code
 * button("Save").onClick(() -> {
 *     if (name.isBlank()) throw new UiException("Name is required", "Type the name people will see.");
 *     ...
 * })
 * }</pre>
 *
 * <p>Any other exception from a handler shows as "Something went wrong", with its technical message under the
 * dialog's details, and is logged with its stack trace.
 */
public class UiException extends RuntimeException {

    /** How serious it is: the dialog's look. */
    public enum Level { INFO, WARNING, ERROR }

    private final String title;
    private final String userMessage;
    private final Level level;

    /** {@code title}: short, what went wrong ("Couldn't save"). {@code userMessage}: what happened and what to do. */
    public UiException(String title, String userMessage) {
        this(title, userMessage, Level.ERROR, null);
    }

    public UiException(String title, String userMessage, Level level) {
        this(title, userMessage, level, null);
    }

    /** With the technical {@code cause}: its message goes under the dialog's details, and to the log. */
    public UiException(String title, String userMessage, Throwable cause) {
        this(title, userMessage, Level.ERROR, cause);
    }

    public UiException(String title, String userMessage, Level level, Throwable cause) {
        super(title + ": " + userMessage, cause);
        this.title = Objects.requireNonNull(title, "title");
        this.userMessage = Objects.requireNonNull(userMessage, "userMessage");
        this.level = Objects.requireNonNull(level, "level");
    }

    public String title() {
        return title;
    }

    /** What the person reads: plain words, what happened and what to do about it. */
    public String userMessage() {
        return userMessage;
    }

    public Level level() {
        return level;
    }
}
