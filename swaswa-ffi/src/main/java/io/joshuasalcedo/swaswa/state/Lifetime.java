package io.joshuasalcedo.swaswa.state;

import java.time.Duration;
import java.util.Objects;

/**
 * How long a value lives and when it is fetched again: for a {@link ServerState} (all of it), a {@link Store} and a
 * {@link Saved} (memory and disk). Made once, reused. The nearest one wins: a value's own, then the first of its
 * session's key patterns that matches ({@link Session#lifetime(String, Lifetime)}), then the session's default.
 * <pre>{@code
 * static final Lifetime LIVE = Lifetime.of().fresh(Duration.ofSeconds(10)).keepInMemory(Duration.ofMinutes(1))
 *         .persist(Duration.ofDays(7)).retry(3, Duration.ofSeconds(1));
 *
 * session.lifetime("reference.>", Lifetime.of().fresh(Duration.ofHours(12)).persist(Duration.ofDays(30)));
 * }</pre>
 * Unless set, as TanStack Query's: fresh 0 (checked each time something starts showing it), kept in memory 1 minute
 * after nothing shows it, not on disk, checked when the network is back, 3 tries.
 */
public final class Lifetime {

    private final Duration fresh;
    private final Duration keepInMemory;
    private final Duration persist;
    private final boolean sensitive;
    private final boolean refetchOnReconnect;
    private final Duration refetchEvery;
    private final int retries;
    private final Duration retryDelay;
    private final int version;

    private Lifetime(Duration fresh, Duration keepInMemory, Duration persist, boolean sensitive, boolean refetchOnReconnect,
                     Duration refetchEvery, int retries, Duration retryDelay, int version) {
        this.fresh = Objects.requireNonNull(fresh, "fresh");
        this.keepInMemory = Objects.requireNonNull(keepInMemory, "keepInMemory");
        this.persist = persist;
        this.sensitive = sensitive;
        this.refetchOnReconnect = refetchOnReconnect;
        this.refetchEvery = Objects.requireNonNull(refetchEvery, "refetchEvery");
        if (retries < 0) throw new IllegalArgumentException("retries must be 0 or more");
        this.retries = retries;
        this.retryDelay = Objects.requireNonNull(retryDelay, "retryDelay");
        this.version = version;
    }

    /** TanStack Query's defaults. */
    public static final Lifetime DEFAULT = new Lifetime(Duration.ZERO, Duration.ofMinutes(1), null, false, true, Duration.ZERO, 3,
            Duration.ofSeconds(1), 0);

    /** A lifetime from the defaults ({@link #DEFAULT}), to change. */
    public static Lifetime of() {
        return DEFAULT;
    }

    // ---- what it says ----

    /** Used as is for this long; after, checked before it is used again (TanStack's staleTime). */
    public Duration freshFor() { return fresh; }
    /** In memory this long after nothing shows it (gcTime); then only on disk, if persisted. */
    public Duration inMemoryFor() { return keepInMemory; }
    /** Kept on disk this long; null: not on disk. */
    public Duration persistedFor() { return persist; }
    /** Never written to disk (tokens, personal data), whatever {@code persist} says. */
    public boolean isSensitive() { return sensitive; }
    public boolean refetchesOnReconnect() { return refetchOnReconnect; }
    /** Fetched again every this long while something shows it (polling); zero: never. */
    public Duration refetchInterval() { return refetchEvery; }
    /** Tries after the first failure. */
    public int retries() { return retries; }
    /** The wait before the first retry, doubled each time. */
    public Duration retryDelay() { return retryDelay; }
    /** A copy on disk of another version is dropped (the data's shape changed). */
    public int version() { return version; }

    /** Written to disk: persisted and not sensitive. */
    public boolean onDisk() {
        return persist != null && !sensitive && !persist.isZero() && !persist.isNegative();
    }

    // ---- changed ----

    public Lifetime fresh(Duration d) { return new Lifetime(d, keepInMemory, persist, sensitive, refetchOnReconnect, refetchEvery, retries, retryDelay, version); }
    public Lifetime keepInMemory(Duration d) { return new Lifetime(fresh, d, persist, sensitive, refetchOnReconnect, refetchEvery, retries, retryDelay, version); }
    /** Kept on disk this long. */
    public Lifetime persist(Duration d) { return new Lifetime(fresh, keepInMemory, d, sensitive, refetchOnReconnect, refetchEvery, retries, retryDelay, version); }
    /** Kept on disk for 30 days. */
    public Lifetime persist() { return persist(Duration.ofDays(30)); }
    /** Not on disk. */
    public Lifetime memoryOnly() { return persist(null); }
    /** Never written to disk. */
    public Lifetime sensitive() { return new Lifetime(fresh, keepInMemory, persist, true, refetchOnReconnect, refetchEvery, retries, retryDelay, version); }
    public Lifetime refetchOnReconnect(boolean on) { return new Lifetime(fresh, keepInMemory, persist, sensitive, on, refetchEvery, retries, retryDelay, version); }
    public Lifetime refetchEvery(Duration d) { return new Lifetime(fresh, keepInMemory, persist, sensitive, refetchOnReconnect, d, retries, retryDelay, version); }
    public Lifetime retry(int times, Duration firstDelay) { return new Lifetime(fresh, keepInMemory, persist, sensitive, refetchOnReconnect, refetchEvery, times, firstDelay, version); }
    public Lifetime noRetry() { return retry(0, retryDelay); }
    public Lifetime version(int v) { return new Lifetime(fresh, keepInMemory, persist, sensitive, refetchOnReconnect, refetchEvery, retries, retryDelay, v); }

    @Override
    public boolean equals(Object o) {
        return o instanceof Lifetime l && fresh.equals(l.fresh) && keepInMemory.equals(l.keepInMemory) && Objects.equals(persist, l.persist)
                && sensitive == l.sensitive && refetchOnReconnect == l.refetchOnReconnect && refetchEvery.equals(l.refetchEvery)
                && retries == l.retries && retryDelay.equals(l.retryDelay) && version == l.version;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fresh, keepInMemory, persist, sensitive, refetchOnReconnect, refetchEvery, retries, retryDelay, version);
    }

    @Override
    public String toString() {
        return "Lifetime[fresh " + fresh + ", in memory " + keepInMemory + ", on disk " + (onDisk() ? persist : "no")
                + (refetchEvery.isZero() ? "" : ", every " + refetchEvery) + ", retries " + retries + ", version " + version + "]";
    }
}
