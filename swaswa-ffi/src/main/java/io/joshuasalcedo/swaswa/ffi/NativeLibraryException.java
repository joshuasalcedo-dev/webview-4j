package io.joshuasalcedo.swaswa.ffi;

/** swaswa's native library is missing, could not load, or is not this jar's version. */
public class NativeLibraryException extends WebViewException {

    public NativeLibraryException(String reason, String hint) {
        super(reason, hint, null, null);
    }

    public NativeLibraryException(String reason, String hint, Throwable cause) {
        super(reason, hint, null, cause);
    }

    NativeLibraryException(String reason, String hint, String action) {
        super(reason, hint, action, null);
    }
}
