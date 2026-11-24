package io.joshuasalcedo.webview4j.ui;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Where a page is: its path ({@code /users/42?page=2}), its slugs ({@code param("id")} for {@code /users/{id}}), its
 * query ({@code query("page")}), and the way to other pages ({@link #navigate}, {@link #back}). Given to every
 * {@link PageView} and {@link LayoutView} as it renders.
 */
public final class PageContext {

    private final Router router;
    private final String path;
    private final String pattern;
    private final Map<String, String> params;
    private final Map<String, String> query;

    PageContext(Router router, String path, String pattern, Map<String, String> params, Map<String, String> query) {
        this.router = router;
        this.path = path;
        this.pattern = pattern;
        this.params = Map.copyOf(params);
        this.query = Map.copyOf(query);
    }

    /** The path shown, without its query: {@code /users/42}. */
    public String path() {
        return path;
    }

    /** The page's pattern: {@code /users/{id}}; null when no page matches (not found). */
    public String pattern() {
        return pattern;
    }

    /**
     * A slug of the path: {@code param("id")} is {@code "42"} at {@code /users/42} for {@code /users/{id}}.
     *
     * @throws IllegalArgumentException if the page's pattern has no such slug
     */
    public String param(String name) {
        String value = params.get(Objects.requireNonNull(name, "name"));
        if (value == null) throw new IllegalArgumentException("no {" + name + "} in " + pattern);
        return value;
    }

    public Map<String, String> params() {
        return params;
    }

    /** A query parameter: {@code query("page")} is {@code "2"} at {@code /users?page=2}; empty when absent. */
    public Optional<String> query(String name) {
        return Optional.ofNullable(query.get(name));
    }

    /** A whole-number query parameter, or {@code otherwise} when absent or not a number: {@code queryInt("page", 1)}. */
    public int queryInt(String name, int otherwise) {
        try {
            return query(name).map(Integer::parseInt).orElse(otherwise);
        } catch (NumberFormatException e) {
            return otherwise;
        }
    }

    public Map<String, String> queries() {
        return query;
    }

    /** Shows another page: {@code navigate("/users/42")}, {@code navigate("/users?page=3")}. Back returns here. */
    public void navigate(String path) {
        router.navigate(path);
    }

    /** This page with one query parameter changed: {@code withQuery("page", 3)} is {@code /users?page=3}. */
    public String withQuery(String name, Object value) {
        return router.withQuery(path, query, name, value);
    }

    /** The page before, as a browser's back. */
    public void back() {
        router.back();
    }

    public void forward() {
        router.forward();
    }

    public boolean canGoBack() {
        return router.canGoBack();
    }

    public boolean canGoForward() {
        return router.canGoForward();
    }

    /** Whether {@code path} is this page or one under it: to mark the side bar's current place. */
    public boolean isAt(String path) {
        if (path.equals("/")) return true;   // every page is under /
        return this.path.equals(path) || this.path.startsWith(path.endsWith("/") ? path : path + "/");
    }

    /** The pages that asked to be in the navigation ({@link Router#page(String, String, PageView)}), in order. */
    public List<Router.Entry> navigation() {
        return router.navigation();
    }

    /**
     * Data that takes a while (a call to a server), loaded once off the screen's thread (Next.js' {@code loading}):
     * empty while it loads, then its value; the page shows again when it arrives. Keyed by {@code key}: a new key
     * (another id, another page number) loads again. A failure shows on the page as its error.
     */
    public <T> Optional<T> load(String key, Supplier<T> loader) {
        return router.load(key, loader);
    }

    /** Forgets what {@link #load} loaded under {@code key}, so it loads again (after a change). */
    public void reload(String key) {
        router.forget(key);
    }
}
