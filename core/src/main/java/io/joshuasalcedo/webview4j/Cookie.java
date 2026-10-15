package io.joshuasalcedo.webview4j;

/** A cookie from a view's browser profile, HttpOnly ones included. The value is a credential. */
public record Cookie(String name, String value, String domain, String path, boolean httpOnly, boolean secure) {

    /** Without the value, so logging a cookie never leaks it. */
    @Override
    public String toString() {
        return "Cookie[name=" + name + ", value=<redacted>, domain=" + domain + ", path=" + path
                + ", httpOnly=" + httpOnly + ", secure=" + secure + "]";
    }
}
