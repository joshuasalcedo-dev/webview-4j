package io.joshuasalcedo.swaswa.ffi;

/** The app is not running: it did not start, it has quit, or it is used after {@code close()}. */
public class NotRunningException extends WebViewException {

    public NotRunningException(String reason, String hint) {
        super(reason, hint, null, null);
    }

    public NotRunningException(String reason, String hint, Throwable cause) {
        super(reason, hint, null, cause);
    }

    NotRunningException(String reason, String hint, String action) {
        super(reason, hint, action, null);
    }
}
