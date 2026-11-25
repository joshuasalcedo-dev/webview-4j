package io.joshuasalcedo.webview4j.automation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * How an element is found, the way a person would point at it: by its role and name ({@code button "Sign in"}), by
 * the label of a field, by its text, by a test id, or (last resort) by CSS. Names match exactly when one does, else
 * by what they contain, ignoring case; {@code nth} picks one of several.
 */
public record Locator(Kind kind, String role, String name, String css, boolean exact, int nth) {

    /** What a locator looks at. */
    public enum Kind { ROLE, LABEL, TEXT, TEST_ID, CSS }

    public Locator {
        Objects.requireNonNull(kind, "kind");
        if (nth < 0) throw new IllegalArgumentException("nth is 0 or more");
    }

    public static Locator role(String role, String name) {
        return new Locator(Kind.ROLE, Objects.requireNonNull(role, "role"), name, null, false, 0);
    }

    public static Locator label(String label) {
        return new Locator(Kind.LABEL, null, Objects.requireNonNull(label, "label"), null, false, 0);
    }

    public static Locator text(String text) {
        return new Locator(Kind.TEXT, null, Objects.requireNonNull(text, "text"), null, false, 0);
    }

    public static Locator testId(String id) {
        return new Locator(Kind.TEST_ID, null, Objects.requireNonNull(id, "id"), null, true, 0);
    }

    public static Locator css(String selector) {
        return new Locator(Kind.CSS, null, null, Objects.requireNonNull(selector, "selector"), true, 0);
    }

    /** Only an exact name. */
    public Locator exactly() {
        return new Locator(kind, role, name, css, true, nth);
    }

    /** The n-th of several (from 0). */
    public Locator nth(int n) {
        return new Locator(kind, role, name, css, exact, n);
    }

    /** As people read it: {@code button "Sign in"}, {@code field "Name"}, {@code text "Saved"}. */
    @Override
    public String toString() {
        String what = switch (kind) {
            case ROLE -> role + (name == null ? "" : " \"" + name + "\"");
            case LABEL -> "field \"" + name + "\"";
            case TEXT -> "text \"" + name + "\"";
            case TEST_ID -> "test id \"" + name + "\"";
            case CSS -> "css " + css;
        };
        return nth > 0 ? what + " (#" + (nth + 1) + ")" : what;
    }

    Map<String, Object> write() {
        Map<String, Object> m = new LinkedHashMap<>();
        switch (kind) {
            case ROLE -> {
                m.put("role", role);
                if (name != null) m.put("name", name);
            }
            case LABEL -> m.put("field", name);
            case TEXT -> m.put("text", name);
            case TEST_ID -> m.put("testId", name);
            case CSS -> m.put("css", css);
        }
        if (exact && kind != Kind.TEST_ID && kind != Kind.CSS) m.put("exact", true);
        if (nth > 0) m.put("nth", nth);
        return m;
    }

    static Locator read(Map<?, ?> m) {
        Locator l;
        if (m.get("role") != null) l = role(String.valueOf(m.get("role")), m.get("name") == null ? null : String.valueOf(m.get("name")));
        else if (m.get("field") != null) l = label(String.valueOf(m.get("field")));
        else if (m.get("text") != null) l = text(String.valueOf(m.get("text")));
        else if (m.get("testId") != null) l = testId(String.valueOf(m.get("testId")));
        else if (m.get("css") != null) l = css(String.valueOf(m.get("css")));
        else {
            // the short forms: {"button": "Sign in"}, {"link": "Docs"}
            var e = m.entrySet().stream().filter(x -> !"exact".equals(x.getKey()) && !"nth".equals(x.getKey())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("a target needs role, field, text, testId or css: " + m));
            l = role(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
        }
        if (Boolean.TRUE.equals(m.get("exact"))) l = l.exactly();
        if (m.get("nth") instanceof Number n) l = l.nth(n.intValue());
        return l;
    }

    /** For the page's helper. */
    Map<String, Object> js() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", switch (kind) {
            case ROLE -> "role";
            case LABEL -> "label";
            case TEXT -> "text";
            case TEST_ID -> "testid";
            case CSS -> "css";
        });
        if (role != null) m.put("role", role);
        if (name != null) m.put("name", name);
        if (css != null) m.put("css", css);
        m.put("exact", exact);
        m.put("nth", nth);
        return m;
    }
}
