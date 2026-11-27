package io.joshuasalcedo.swaswa.dom;

/** A call of the page's (a function, the clipboard) failed in the page: its message is the page's. */
public final class PageCallException extends RuntimeException {

    public PageCallException(String message) {
        super(message);
    }
}
