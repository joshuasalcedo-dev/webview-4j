package io.joshuasalcedo.webview4j;

/** A subscriber's registration. Closing it stops the events; ones already queued are dropped. */
public interface Subscription extends AutoCloseable {

    boolean isActive();

    @Override
    void close();
}
