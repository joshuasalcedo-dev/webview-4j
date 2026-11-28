package io.joshuasalcedo.swaswa.ffi;

/**
 * The operating system could not do it, for a reason other than permission: a path that is not there, a file where a
 * folder should be, a full disk, a device that went away. {@link #osError()} is the system's own error code.
 */
public class SystemException extends WebViewException {

    private final Integer osError;

    public SystemException(String reason, String hint, Integer osError, Throwable cause) {
        this(reason, hint, null, osError, cause);
    }

    SystemException(String reason, String hint, String action, Integer osError, Throwable cause) {
        super(reason, hint, action, cause);
        this.osError = osError;
    }

    /** The system's own error code; null if it gave none. */
    public Integer osError() {
        return osError;
    }
}
