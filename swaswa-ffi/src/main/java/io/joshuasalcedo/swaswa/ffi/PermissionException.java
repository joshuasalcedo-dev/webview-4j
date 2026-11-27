package io.joshuasalcedo.swaswa.ffi;

/**
 * The operating system refused: access to a file or folder, a setting, a device. {@link #needsElevation()} says
 * whether administrator rights (root elsewhere) would change that: the place is the system's own, or the system said
 * the action needs them. Otherwise it is this user's access (a read-only file, one open in another program, another
 * user's). The hint says which to do: choose a place of the user's own, run as administrator, or grant access.
 */
public class PermissionException extends WebViewException {

    private final boolean needsElevation;
    private final Integer osError;

    public PermissionException(String reason, String hint, boolean needsElevation, Integer osError, Throwable cause) {
        this(reason, hint, null, needsElevation, osError, cause);
    }

    PermissionException(String reason, String hint, String action, boolean needsElevation, Integer osError, Throwable cause) {
        super(reason, hint, action, cause);
        this.needsElevation = needsElevation;
        this.osError = osError;
    }

    /** Whether administrator rights would let it happen (see {@link App#elevated()}). */
    public boolean needsElevation() {
        return needsElevation;
    }

    /** The system's own error code (Windows: 5 access denied, 740 elevation required); null if it gave none. */
    public Integer osError() {
        return osError;
    }
}
