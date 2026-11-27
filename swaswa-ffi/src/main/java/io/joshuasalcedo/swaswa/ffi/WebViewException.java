package io.joshuasalcedo.swaswa.ffi;

/**
 * What swaswa could not do, and what to do about it: the root of its exceptions, so one {@code catch} takes them all.
 * {@link #reason()} says what happened; {@link #hint()}, when there is one, what the app's developer can do (both are
 * in {@link #getMessage()}, so a stack trace shows them). Catch a subclass to handle one situation:
 * <ul>
 *   <li>{@link NotRunningException}: the app did not start, has quit, or is used after {@code close()};</li>
 *   <li>{@link AlreadyRunningException}: a second {@code App} in the process;</li>
 *   <li>{@link ViewClosedException}: the window or view was closed;</li>
 *   <li>{@link NotSupportedException}: not on this system, or not for this kind of view;</li>
 *   <li>{@link InvalidRequestException}: what was asked is not right (a value, a name, a payload);</li>
 *   <li>{@link ConflictException}: the view is busy with something that must end first;</li>
 *   <li>{@link PageException}: the page could not do it (no such canvas, a picture it cannot read);</li>
 *   <li>{@link NativeLibraryException}: the native library is missing, or not this jar's version;</li>
 *   <li>{@link PermissionException}: the operating system refused (and whether administrator rights would help);</li>
 *   <li>{@link SystemException}: the operating system could not do it (a path not there, a full disk).</li>
 * </ul>
 * A method that returns a future fails it with one of these: {@code join()} throws it wrapped in a
 * {@link java.util.concurrent.CompletionException} ({@code getCause()} is it), {@code get()} in an
 * {@link java.util.concurrent.ExecutionException}.
 */
public class WebViewException extends RuntimeException {

    private final String reason;
    private final String hint;
    private final String action;

    public WebViewException(String message) {
        this(message, null, null, null);
    }

    public WebViewException(String message, Throwable cause) {
        this(message, null, null, cause);
    }

    /**
     * @param reason what happened
     * @param hint   what to do about it; null if there is nothing to say
     * @param action the action that failed (a protocol subject such as {@code view.wv-1.record}); null if none
     */
    public WebViewException(String reason, String hint, String action, Throwable cause) {
        super(message(reason, hint, action), cause);
        this.reason = reason;
        this.hint = hint;
        this.action = action;
    }

    private static String message(String reason, String hint, String action) {
        String what = action == null ? reason : action + ": " + reason;
        return hint == null || hint.isBlank() ? what : what + System.lineSeparator() + "  What to do: " + hint;
    }

    /** What happened. */
    public String reason() {
        return reason;
    }

    /** What the app's developer can do about it; null if there is nothing to say. */
    public String hint() {
        return hint;
    }

    /** The action that failed, such as {@code view.wv-1.record}; null if the failure was not of one action. */
    public String action() {
        return action;
    }

    private static Integer osError(Message reply) {
        String code = reply.header("Os-Error");
        try {
            return code == null ? null : Integer.valueOf(code.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * The exception for a native error reply: its {@code Error-Kind} decides the class, its {@code Hint} and text
     * what it says.
     */
    static WebViewException fromReply(String action, Message reply) {
        String kind = reply.header("Error-Kind");
        String hint = reply.header("Hint");
        String reason = reply.text();
        return switch (kind == null ? "" : kind) {
            case "view-closed" -> new ViewClosedException(reason, hint, action);
            case "unsupported" -> new NotSupportedException(reason, hint, action);
            case "invalid" -> new InvalidRequestException(reason, hint, action);
            case "conflict" -> new ConflictException(reason, hint, action);
            case "page", "not-found" -> new PageException(reason, hint, action);
            case "permission" -> new PermissionException(reason, hint, action, "required".equals(reply.header("Elevation")),
                    osError(reply), null);
            case "system" -> new SystemException(reason, hint, action, osError(reply), null);
            default -> new WebViewException(reason, hint != null ? hint
                    : "This is swaswa's own failure: report it with the log (run with -Dswaswa.log=debug).", action, null);
        };
    }
}
