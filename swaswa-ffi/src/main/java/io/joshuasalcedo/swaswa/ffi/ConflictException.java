package io.joshuasalcedo.swaswa.ffi;

/** The view is busy with something that must end first, such as a recording under way. */
public class ConflictException extends WebViewException {

    public ConflictException(String reason, String hint) {
        super(reason, hint, null, null);
    }

    public ConflictException(String reason, String hint, Throwable cause) {
        super(reason, hint, null, cause);
    }

    ConflictException(String reason, String hint, String action) {
        super(reason, hint, action, null);
    }
}
