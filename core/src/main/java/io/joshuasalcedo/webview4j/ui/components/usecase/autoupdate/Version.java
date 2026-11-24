package io.joshuasalcedo.webview4j.ui.components.usecase.autoupdate;

import java.util.Arrays;
import java.util.Objects;

/**
 * A version as releases number them: {@code 1.4.2}, {@code 2.0.0-rc.1}. Compared part by part as numbers, and a
 * pre-release before its release ({@code 2.0.0-rc.1 < 2.0.0}).
 */
public record Version(String text) implements Comparable<Version> {

    public Version {
        Objects.requireNonNull(text, "text");
        text = text.trim().replaceFirst("^[vV]", "");
        if (!text.matches("[0-9]+(\\.[0-9]+)*(-[0-9A-Za-z.]+)?")) throw new IllegalArgumentException("not a version: " + text);
    }

    public static Version of(String text) {
        return new Version(text);
    }

    /** Whether {@code text} reads as a version (an "Unreleased" heading does not). */
    public static boolean is(String text) {
        try {
            of(text);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private int[] numbers() {
        return Arrays.stream(text.split("-", 2)[0].split("\\.")).mapToInt(Integer::parseInt).toArray();
    }

    private String pre() {
        String[] parts = text.split("-", 2);
        return parts.length > 1 ? parts[1] : null;
    }

    @Override
    public int compareTo(Version other) {
        int[] a = numbers();
        int[] b = other.numbers();
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int d = Integer.compare(i < a.length ? a[i] : 0, i < b.length ? b[i] : 0);
            if (d != 0) return d;
        }
        String x = pre();
        String y = other.pre();
        if (x == null || y == null) return x == null ? (y == null ? 0 : 1) : -1;
        return x.compareTo(y);
    }

    public boolean newerThan(Version other) {
        return compareTo(other) > 0;
    }

    @Override
    public String toString() {
        return text;
    }
}
