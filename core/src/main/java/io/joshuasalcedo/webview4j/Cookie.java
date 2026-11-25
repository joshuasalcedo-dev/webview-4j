package io.joshuasalcedo.webview4j;

/** A cookie from a view's browser profile, HttpOnly ones included. The value is a credential. */
public record Cookie(String name, String value, String domain, String path, boolean httpOnly, boolean secure) {

    /** A cookie to set: this name and value, for the page's own site, path "/", script-readable, not Secure-only. */
    public static Cookie of(String name, String value) {
        return new Cookie(name, value, null, "/", false, false);
    }

    /** When the browser sends it with requests from other sites ({@link View#setCookie(Cookie, java.time.Duration, SameSite)}). */
    public enum SameSite {
        STRICT("Strict"), LAX("Lax"), NONE("None");

        final String word;

        SameSite(String word) { this.word = word; }
    }

    /** Without the value, so logging a cookie never leaks it. */
    @Override
    public String toString() {
        return "Cookie[name=" + name + ", value=<redacted>, domain=" + domain + ", path=" + path
                + ", httpOnly=" + httpOnly + ", secure=" + secure + "]";
    }
}
