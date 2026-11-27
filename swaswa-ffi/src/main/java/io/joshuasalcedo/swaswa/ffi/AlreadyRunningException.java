package io.joshuasalcedo.swaswa.ffi;

/** A second {@code App} was started while one runs: there is one per process, on one event loop. */
public class AlreadyRunningException extends WebViewException {

    public AlreadyRunningException(String reason, String hint) {
        super(reason, hint, null, null);
    }

    public AlreadyRunningException(String reason, String hint, Throwable cause) {
        super(reason, hint, null, cause);
    }

    AlreadyRunningException(String reason, String hint, String action) {
        super(reason, hint, action, null);
    }
}
