package io.joshuasalcedo.swaswa.ffi;

/** The window or view was closed (by the person, or by {@code close()}), or never opened in this app.
 * Subscribe to {@link Closed} to stop using a view once it closes. */
public class ViewClosedException extends WebViewException {

    public ViewClosedException(String reason, String hint) {
        super(reason, hint, null, null);
    }

    public ViewClosedException(String reason, String hint, Throwable cause) {
        super(reason, hint, null, cause);
    }

    ViewClosedException(String reason, String hint, String action) {
        super(reason, hint, action, null);
    }
}
