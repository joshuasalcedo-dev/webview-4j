package io.joshuasalcedo.webview4j;

/**
 * Thrown by a route to answer with a status other than 500, e.g.
 * {@code throw new HttpException(404, "no user 42")}. The page gets the status and the message.
 * An {@link IllegalArgumentException} answers 400.
 */
public class HttpException extends RuntimeException {

    private final int status;

    public HttpException(int status, String message) {
        super(message);
        if (status < 400 || status > 599) throw new IllegalArgumentException("not an error status: " + status);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
