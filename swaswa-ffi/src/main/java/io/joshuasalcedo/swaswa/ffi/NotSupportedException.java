package io.joshuasalcedo.swaswa.ffi;

/** Not on this system yet, or not for this kind of view (an embedded view has no window of its own). */
public class NotSupportedException extends WebViewException {

    public NotSupportedException(String reason, String hint) {
        super(reason, hint, null, null);
    }

    public NotSupportedException(String reason, String hint, Throwable cause) {
        super(reason, hint, null, cause);
    }

    NotSupportedException(String reason, String hint, String action) {
        super(reason, hint, action, null);
    }
}
