package io.joshuasalcedo.webview4j;

/** An action the native side refused or could not perform, or an app that is not running. */
public class WebViewException extends RuntimeException {

    public WebViewException(String message) {
        super(message);
    }

    public WebViewException(String message, Throwable cause) {
        super(message, cause);
    }
}
