package io.joshuasalcedo.webview4j.plugin.automation;

import java.util.Map;

/**
 * Something in the window, found the way a person points at it ({@link Locator}). Found again for each action, so it
 * stays right while the page changes; each action waits until it is there and visible.
 */
public final class Element {

    private final Automation automation;
    private final Locator locator;

    Element(Automation automation, Locator locator) {
        this.automation = automation;
        this.locator = locator;
    }

    public Locator locator() {
        return locator;
    }

    /** The n-th of several (from 0). */
    public Element nth(int n) {
        return new Element(automation, locator.nth(n));
    }

    /** Only an exact name. */
    public Element exactly() {
        return new Element(automation, locator.exactly());
    }

    public Element click() {
        automation.act(locator, "click", null);
        return this;
    }

    public Element doubleClick() {
        automation.act(locator, "dblclick", null);
        return this;
    }

    /** The right-click (its context menu). */
    public Element rightClick() {
        automation.act(locator, "contextmenu", null);
        return this;
    }

    public Element hover() {
        automation.act(locator, "hover", null);
        return this;
    }

    public Element focus() {
        automation.act(locator, "focus", null);
        return this;
    }

    /** Puts {@code text} in the field (instead of what was there), as typing would. */
    public Element type(String text) {
        automation.act(locator, "type", text);
        return this;
    }

    public Element clear() {
        return type("");
    }

    /** Presses a key on it: {@code Enter}, {@code Control+A}. */
    public Element press(String key) {
        automation.act(locator, "press", key);
        return this;
    }

    public Element check() {
        automation.act(locator, "check", true);
        return this;
    }

    public Element uncheck() {
        automation.act(locator, "check", false);
        return this;
    }

    /** Chooses {@code option}: in a select, or by opening it and clicking the option. */
    public Element select(String option) {
        Map<String, Object> r = automation.act(locator, "select", option);
        if (Boolean.TRUE.equals(r.get("open"))) automation.role("option", option).click();
        return this;
    }

    /** Its text, as a person reads it. */
    public String text() {
        return String.valueOf(automation.act(locator, "read", null).get("text"));
    }

    /** A field's value. */
    public String value() {
        Object v = automation.act(locator, "read", null).get("value");
        return v == null ? null : String.valueOf(v);
    }

    public boolean checked() {
        return Boolean.TRUE.equals(automation.act(locator, "read", null).get("checked"));
    }

    /** Whether it is there and visible now (no waiting). */
    public boolean exists() {
        return automation.count(locator.nth(0)) > locator.nth();
    }

    /** How many there are now. */
    public int count() {
        return automation.count(locator.nth(0));
    }

    /** Waits until it is there and visible. */
    public Element waitFor() {
        automation.act(locator, "read", null);
        return this;
    }

    /** Waits until its text has {@code text}, or fails saying what it had. */
    public Element expectText(String text) {
        long until = System.nanoTime() + automation.timeout().toNanos();
        String had = "";
        while (true) {
            had = text();
            if (had.contains(text)) return this;
            if (System.nanoTime() > until) throw new AutomationException(locator + " never had \"" + text + "\": it has \"" + had + "\"", java.util.List.of());
            automation.pause(java.time.Duration.ofMillis(50));
        }
    }

    @Override
    public String toString() {
        return locator.toString();
    }
}
