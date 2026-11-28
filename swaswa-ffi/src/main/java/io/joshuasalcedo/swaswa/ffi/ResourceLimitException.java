package io.joshuasalcedo.swaswa.ffi;

/**
 * Refused: the app is over its memory limit ({@link App#memoryLimit(long)}), and this would take more (another window,
 * another view). {@link #memory()} is what it used when it was refused; the hint says what uses the most.
 */
public class ResourceLimitException extends WebViewException {

    private final transient Memory memory;
    private final long limit;

    public ResourceLimitException(String reason, String hint, Memory memory, long limit) {
        super(reason, hint, null, null);
        this.memory = memory;
        this.limit = limit;
    }

    /** What the app used when it was refused. */
    public Memory memory() {
        return memory;
    }

    /** The limit, in bytes. */
    public long limit() {
        return limit;
    }
}
