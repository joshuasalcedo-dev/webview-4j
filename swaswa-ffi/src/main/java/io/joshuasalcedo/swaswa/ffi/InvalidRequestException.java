package io.joshuasalcedo.swaswa.ffi;

/** What was asked is not right: a value out of range, a name, a payload the native side cannot read. */
public class InvalidRequestException extends WebViewException {

    public InvalidRequestException(String reason, String hint) {
        super(reason, hint, null, null);
    }

    public InvalidRequestException(String reason, String hint, Throwable cause) {
        super(reason, hint, null, cause);
    }

    InvalidRequestException(String reason, String hint, String action) {
        super(reason, hint, action, null);
    }
}
