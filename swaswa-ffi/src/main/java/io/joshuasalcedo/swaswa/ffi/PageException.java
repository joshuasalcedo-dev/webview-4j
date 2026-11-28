package io.joshuasalcedo.swaswa.ffi;

/** The page could not do it: it has no such element, or could not read what it was given. */
public class PageException extends WebViewException {

    public PageException(String reason, String hint) {
        super(reason, hint, null, null);
    }

    public PageException(String reason, String hint, Throwable cause) {
        super(reason, hint, null, cause);
    }

    PageException(String reason, String hint, String action) {
        super(reason, hint, action, null);
    }
}
