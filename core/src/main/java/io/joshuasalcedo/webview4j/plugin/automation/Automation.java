package io.joshuasalcedo.webview4j.plugin.automation;

import io.joshuasalcedo.webview4j.Json;
import io.joshuasalcedo.webview4j.Recording;
import io.joshuasalcedo.webview4j.View;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Drives one of the app's windows as a person would, for tests, demos and recordings: things are found by their role
 * and name ({@link #button}, {@link #field}, {@link #link}, {@link #text}...), every action waits until its target is
 * there, and what cannot be found says what the page had instead. What a person does can be recorded
 * ({@link #record()}), saved as a {@link Script} and replayed.
 * <pre>{@code
 * Automation ui = Automation.of(view);
 * ui.field("Email").type("ana@example.com");
 * ui.field("Password").type(secret);
 * ui.button("Sign in").click();
 * ui.waitForText("Welcome, Ana");
 * ui.screenshot(Path.of("docs/signed-in.png"));
 * }</pre>
 * It runs JavaScript in the window (its own, never another program's); any page works: the Java UI's, a React app's.
 */
public final class Automation {

    private static final String HELPER = helper();

    private final View view;
    private Duration timeout = Duration.ofSeconds(5);
    private Duration poll = Duration.ofMillis(50);

    private Automation(View view) {
        this.view = Objects.requireNonNull(view, "view");
    }

    public static Automation of(View view) {
        return new Automation(view);
    }

    /** How long a step waits for its target (5 seconds unless set). */
    public Automation timeout(Duration timeout) {
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        return this;
    }

    public Duration timeout() {
        return timeout;
    }

    public View view() {
        return view;
    }

    // ---- finding ----

    public Element find(Locator locator) {
        return new Element(this, locator);
    }

    /** Something with this role ({@code button}, {@code link}, {@code checkbox}, {@code tab}, {@code row}...) and name. */
    public Element role(String role, String name) {
        return find(Locator.role(role, name));
    }

    public Element button(String name) {
        return role("button", name);
    }

    public Element link(String name) {
        return role("link", name);
    }

    public Element checkbox(String name) {
        return role("checkbox", name);
    }

    public Element tab(String name) {
        return role("tab", name);
    }

    public Element menuItem(String name) {
        return role("menuitem", name);
    }

    public Element heading(String name) {
        return role("heading", name);
    }

    /** A field (text box, select, check box...) by its label. */
    public Element field(String label) {
        return find(Locator.label(label));
    }

    /** The element whose own text this is. */
    public Element text(String text) {
        return find(Locator.text(text));
    }

    /** {@code data-testid="..."}. */
    public Element testId(String id) {
        return find(Locator.testId(id));
    }

    public Element css(String selector) {
        return find(Locator.css(selector));
    }

    // ---- the page ----

    /** Presses a key on what has the focus: {@code Enter}, {@code Escape}, {@code Control+S}, {@code Shift+Tab}. */
    public Automation press(String key) {
        call("__w4jAuto.press(" + Json.write(key) + ")");
        return this;
    }

    /** Waits until the page shows {@code text} (anywhere), or fails after the timeout. */
    public Automation waitForText(String text) {
        long until = System.nanoTime() + timeout.toNanos();
        while (true) {
            Object shown = view.eval("document.body ? document.body.innerText : ''").join();
            if (String.valueOf(shown).contains(text)) return this;
            if (System.nanoTime() > until) throw new AutomationException("the page never showed \"" + text + "\" in " + timeout.toSeconds() + "s", seen());
            sleep();
        }
    }

    /** Waits until the page no longer shows {@code text}. */
    public Automation waitForTextGone(String text) {
        long until = System.nanoTime() + timeout.toNanos();
        while (true) {
            Object shown = view.eval("document.body ? document.body.innerText : ''").join();
            if (!String.valueOf(shown).contains(text)) return this;
            if (System.nanoTime() > until) throw new AutomationException("the page still shows \"" + text + "\" after " + timeout.toSeconds() + "s", List.of());
            sleep();
        }
    }

    /** All the page's text, as a person reads it. */
    public String pageText() {
        return String.valueOf(view.eval("document.body ? document.body.innerText : ''").join());
    }

    /** What the page has that can be pointed at: its buttons, fields, links, tabs... */
    @SuppressWarnings("unchecked")
    public List<String> seen() {
        Object r = call("__w4jAuto.inventory()");
        return r instanceof List<?> l ? (List<String>) l : List.of();
    }

    public Path screenshot(Path file) {
        return view.screenshot(file).join();
    }

    public Recording record(Path file, Recording.Options options) {
        return view.record(file, options).join();
    }

    /** Starts recording what the person does in the window, as steps: {@link Recorder#stop()} gives the script. */
    public Recorder record() {
        return Recorder.start(this);
    }

    /** Waits, as a person pauses (a recording shows the screen a moment). */
    public Automation pause(Duration time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return this;
    }

    // ---- for Element and Recorder ----

    /** Runs the helper's {@code act}, retrying until its target is there or the timeout passes. */
    @SuppressWarnings("unchecked")
    Map<String, Object> act(Locator locator, String action, Object arg) {
        String script = "__w4jAuto.act(" + Json.write(locator.js()) + ", " + Json.write(action) + ", " + Json.write(arg) + ")";
        long until = System.nanoTime() + timeout.toNanos();
        Map<String, Object> last = Map.of();
        while (true) {
            Object r = call(script);
            if (r instanceof Map<?, ?> m) {
                last = (Map<String, Object>) m;
                if (Boolean.TRUE.equals(last.get("ok"))) return last;
            }
            if (System.nanoTime() > until) {
                List<String> seen = last.get("seen") instanceof List<?> l ? (List<String>) l : List.of();
                int found = last.get("found") instanceof Number n ? n.intValue() : 0;
                String why = found > 0 && locator.nth() >= found ? " (" + found + " found, #" + (locator.nth() + 1) + " asked)" : "";
                throw new AutomationException("no " + locator + " in " + timeout.toSeconds() + "s" + why, seen);
            }
            sleep();
        }
    }

    int count(Locator locator) {
        Object r = call("__w4jAuto.count(" + Json.write(locator.js()) + ")");
        return r instanceof Number n ? n.intValue() : 0;
    }

    /** Evaluates {@code expression} with the helper in the page. */
    Object call(String expression) {
        return view.eval("(() => { " + HELPER + "\nreturn " + expression + "; })()").join();
    }

    private void sleep() {
        try {
            Thread.sleep(poll);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AutomationException("stopped", List.of());
        }
    }

    private static String helper() {
        try (InputStream in = Automation.class.getResourceAsStream("automation.js")) {
            if (in == null) throw new IllegalStateException("automation.js is not in webview-4j's jar");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<String> list(Object o) {
        List<String> out = new ArrayList<>();
        if (o instanceof List<?> l) for (Object x : l) out.add(String.valueOf(x));
        return out;
    }
}
