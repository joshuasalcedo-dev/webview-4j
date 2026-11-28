package io.joshuasalcedo.swaswa.ffi;

/**
 * A background {@link Worker} ended: done, cancelled, or failed ({@code error} why, {@code hint} what to do, when its
 * exception said).
 */
public record WorkerFinished(String worker, State state, String error, String hint) implements WebViewEvent {

    public enum State { DONE, CANCELLED, FAILED }
}
