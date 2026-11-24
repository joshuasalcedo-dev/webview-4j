package io.joshuasalcedo.webview4j.ui.state;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * How long a value lives and when it is fetched again: for {@link ServerState} (all of it), {@link Store} and
 * {@link Saved} (memory and disk). Named and reused; the nearest one wins: a value's own, then the first key pattern
 * that matches its key ({@link #forKeys}), then the app's {@link #defaults}.
 * <pre>{@code
 * static final Property LIVE = Property.of().fresh(Duration.ofSeconds(10)).keepInMemory(Duration.ofMinutes(1))
 *         .persist(Duration.ofDays(7)).refetchOnFocus(true).retry(3, Duration.ofSeconds(1));
 *
 * Property.forKeys("reference.>", Property.of().fresh(Duration.ofHours(12)).persist(Duration.ofDays(30)));
 * }</pre>
 * Unless set (as TanStack Query's): fresh 0 (checked each time a screen shows it, a cheap 304 when the server has
 * version tags), kept in memory 1 minute after no screen shows it, not on disk, checked on focus and when the network
 * is back, 3 tries.
 *
 */
public final class Property {

    private final Duration fresh;
    private final Duration keepInMemory;
    private final Duration persist;
    private final boolean sensitive;
    private final boolean refetchOnFocus;
    private final boolean refetchOnReconnect;
    private final Duration refetchEvery;
    private final int retries;
    private final Duration retryDelay;
    private final int version;

    private Property(Duration fresh, Duration keepInMemory, Duration persist, boolean sensitive, boolean refetchOnFocus,
                  boolean refetchOnReconnect, Duration refetchEvery, int retries, Duration retryDelay, int version) {
        this.fresh = Objects.requireNonNull(fresh, "fresh");
        this.keepInMemory = Objects.requireNonNull(keepInMemory, "keepInMemory");
        this.persist = persist;
        this.sensitive = sensitive;
        this.refetchOnFocus = refetchOnFocus;
        this.refetchOnReconnect = refetchOnReconnect;
        this.refetchEvery = Objects.requireNonNull(refetchEvery, "refetchEvery");
        if (retries < 0) throw new IllegalArgumentException("retries must be 0 or more");
        this.retries = retries;
        this.retryDelay = Objects.requireNonNull(retryDelay, "retryDelay");
        this.version = version;
    }

    // ---- what they say ----

    /** Used as is for this long; after, checked before it is used again (TanStack's staleTime). */
    public Duration freshFor() { return fresh; }
    /** In memory this long after no screen shows it (gcTime); then only on disk, if persisted. */
    public Duration inMemoryFor() { return keepInMemory; }
    /** Kept on disk this long; null: not on disk. */
    public Duration persistedFor() { return persist; }
    /** Never written to disk (tokens, personal data), whatever {@code persist} says. */
    public boolean isSensitive() { return sensitive; }
    public boolean refetchesOnFocus() { return refetchOnFocus; }
    public boolean refetchesOnReconnect() { return refetchOnReconnect; }
    /** Fetched again every this long while a screen shows it (polling); zero: never. */
    public Duration refetchInterval() { return refetchEvery; }
    /** Tries after the first failure. */
    public int retries() { return retries; }
    /** The wait before the first retry, doubled each time. */
    public Duration retryDelay() { return retryDelay; }
    /** A copy on disk of another version is dropped (the data's shape changed). */
    public int version() { return version; }

    /** TanStack Query's defaults. */
    public static final Property DEFAULT = new Property(Duration.ZERO, Duration.ofMinutes(1), null, false, true, true,
            Duration.ZERO, 3, Duration.ofSeconds(1), 0);

    private static volatile Property defaults = DEFAULT;
    private static final List<Map.Entry<String, Property>> PATTERNS = new CopyOnWriteArrayList<>();

    /** Property starting from the app's defaults. */
    public static Property of() {
        return defaults;
    }

    public Property fresh(Duration d) { return new Property(d, keepInMemory, persist, sensitive, refetchOnFocus, refetchOnReconnect, refetchEvery, retries, retryDelay, version); }
    public Property keepInMemory(Duration d) { return new Property(fresh, d, persist, sensitive, refetchOnFocus, refetchOnReconnect, refetchEvery, retries, retryDelay, version); }
    /** Kept on disk this long. */
    public Property persist(Duration d) { return new Property(fresh, keepInMemory, d, sensitive, refetchOnFocus, refetchOnReconnect, refetchEvery, retries, retryDelay, version); }
    /** Kept on disk for 30 days. */
    public Property persist() { return persist(Duration.ofDays(30)); }
    /** Not on disk. */
    public Property memoryOnly() { return persist(null); }
    /** Never written to disk. */
    public Property sensitive() { return new Property(fresh, keepInMemory, persist, true, refetchOnFocus, refetchOnReconnect, refetchEvery, retries, retryDelay, version); }
    public Property refetchOnFocus(boolean on) { return new Property(fresh, keepInMemory, persist, sensitive, on, refetchOnReconnect, refetchEvery, retries, retryDelay, version); }
    public Property refetchOnReconnect(boolean on) { return new Property(fresh, keepInMemory, persist, sensitive, refetchOnFocus, on, refetchEvery, retries, retryDelay, version); }
    public Property refetchEvery(Duration d) { return new Property(fresh, keepInMemory, persist, sensitive, refetchOnFocus, refetchOnReconnect, d, retries, retryDelay, version); }
    public Property retry(int times, Duration firstDelay) { return new Property(fresh, keepInMemory, persist, sensitive, refetchOnFocus, refetchOnReconnect, refetchEvery, times, firstDelay, version); }
    public Property noRetry() { return retry(0, retryDelay); }
    public Property version(int v) { return new Property(fresh, keepInMemory, persist, sensitive, refetchOnFocus, refetchOnReconnect, refetchEvery, retries, retryDelay, v); }

    /** Written to disk: persisted and not sensitive. */
    public boolean onDisk() {
        return persist != null && !sensitive && !persist.isZero() && !persist.isNegative();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Property r && fresh.equals(r.fresh) && keepInMemory.equals(r.keepInMemory) && Objects.equals(persist, r.persist)
                && sensitive == r.sensitive && refetchOnFocus == r.refetchOnFocus && refetchOnReconnect == r.refetchOnReconnect
                && refetchEvery.equals(r.refetchEvery) && retries == r.retries && retryDelay.equals(r.retryDelay) && version == r.version;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fresh, keepInMemory, persist, sensitive, refetchOnFocus, refetchOnReconnect, refetchEvery, retries, retryDelay, version);
    }

    @Override
    public String toString() {
        return "Property[fresh " + fresh + ", in memory " + keepInMemory + ", on disk " + (onDisk() ? persist : "no")
                + (refetchEvery.isZero() ? "" : ", every " + refetchEvery) + ", retries " + retries + ", version " + version + "]";
    }

    // ---- where rules come from ----

    /** The app's rules for every value without rules of its own. */
    public static void defaults(Property rules) {
        defaults = Objects.requireNonNull(rules, "rules");
    }

    /**
     * Property for every key the pattern matches, as subjects match: {@code "orders"}, {@code "orders.*"} (one more
     * part), {@code "reference.>"} (one or more). The first pattern that matches wins.
     */
    public static void forKeys(String pattern, Property rules) {
        PATTERNS.add(Map.entry(Objects.requireNonNull(pattern, "pattern"), Objects.requireNonNull(rules, "rules")));
    }

    /** The rules for a key: its own, then the first pattern that matches, then the defaults. */
    static Property resolve(String key, Property own) {
        if (own != null) return own;
        for (Map.Entry<String, Property> p : PATTERNS) if (Keys.matches(p.getKey(), key)) return p.getValue();
        return defaults;
    }

    /** Back to TanStack's defaults and no patterns: for tests. */
    static void reset() {
        defaults = DEFAULT;
        PATTERNS.clear();
    }
}
