package io.joshuasalcedo.webview4j.ui;

import static io.joshuasalcedo.webview4j.ui.Ui.*;

import io.joshuasalcedo.webview4j.ui.components.*;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.WindowOptions;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * An app of several pages, the way Next.js' App Router does it: pages at paths ({@code /users},
 * {@code /users/{id}} with a slug, {@code /docs/{*rest}} for the rest of the path), layouts that wrap every page under
 * their path (nested: the one at {@code /} outermost), query parameters ({@code ?page=2}), back and forward, data
 * loaded off the screen's thread, and each page's errors kept to the page.
 * <pre>{@code
 * Router router = new Router().title("Shop")
 *         .page(new Router.Entry("/", "Home", "Shop", 0), page -> title("Welcome"))
 *         .page(new Router.Entry("/orders", "Orders", "Shop", 1), page -> ordersList(page))
 *         .page("/orders/{id}", page -> order(page.param("id")))
 *         .layout("/orders", (page, content) -> column(text("Orders").variant(TextVariant.TITLE), content).padding(24));
 * router.open(app, o -> o.title("Shop").size(1100, 720));
 * }</pre>
 * Without a layout at {@code /}, the app gets one: its title bar, a side bar of the pages in the navigation, back
 * and forward, and a status bar.
 */
public final class Router implements Screen {

    private static final System.Logger LOG = System.getLogger(Router.class.getName());

    /** A page as the navigation lists it: its path (no slugs), its name, its group in the side bar, its place. */
    public record Entry(String path, String title, String section, int order) {
        public Entry {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(title, "title");
            if (path.contains("{")) throw new IllegalArgumentException("a page in the navigation has no slugs: " + path);
        }
    }

    private record PageRoute(String pattern, String[] segments, PageView view, Entry entry) {}

    private record LayoutRoute(String prefix, LayoutView view) {}

    /** What {@link PageContext#load} found: the value, or why it failed. */
    private record Loaded(Object value, RuntimeException failure) {}

    private final List<PageRoute> pages = new ArrayList<>();
    private final List<LayoutRoute> layouts = new ArrayList<>();
    private String title = "App";
    private String path = "/";
    private Map<String, String> query = Map.of();
    private final Deque<String> backStack = new ArrayDeque<>();
    private final Deque<String> forwardStack = new ArrayDeque<>();
    private final Map<String, Loaded> loaded = new HashMap<>();
    private final Set<String> loading = new HashSet<>();
    private volatile UiWindow window;

    /** The app's name: the default layout's title bar. */
    public Router title(String title) {
        this.title = Objects.requireNonNull(title, "title");
        return this;
    }

    public String title() {
        return title;
    }

    /** A page at {@code pattern} ({@code /users/{id}}), not in the navigation. */
    public synchronized Router page(String pattern, PageView view) {
        return add(pattern, view, null);
    }

    /** A page in the navigation, at its {@code entry}'s path. */
    public synchronized Router page(Entry entry, PageView view) {
        return add(entry.path(), view, entry);
    }

    /** A screen as a page: a {@link Screen} needs no {@link PageContext}. */
    public Router page(Entry entry, Screen screen) {
        return page(entry, page -> screen.render());
    }

    private Router add(String pattern, PageView view, Entry entry) {
        String normal = normal(Objects.requireNonNull(pattern, "pattern"));
        Objects.requireNonNull(view, "view");
        if (pages.stream().anyMatch(p -> p.pattern().equals(normal))) throw new IllegalStateException("two pages at " + normal);
        pages.add(new PageRoute(normal, segments(normal), view, entry));
        // The most specific first: more fixed segments, then more segments; a catch-all last.
        pages.sort(Comparator.comparingInt((PageRoute p) -> p.pattern().contains("{*") ? 1 : 0)
                .thenComparingInt(p -> -(int) java.util.Arrays.stream(p.segments()).filter(s -> !s.startsWith("{")).count())
                .thenComparingInt(p -> -p.segments().length));
        return this;
    }

    /** A layout for every page at {@code prefix} or under it. */
    public synchronized Router layout(String prefix, LayoutView view) {
        String normal = normal(Objects.requireNonNull(prefix, "prefix"));
        layouts.removeIf(l -> l.prefix().equals(normal));
        layouts.add(new LayoutRoute(normal, Objects.requireNonNull(view, "view")));
        layouts.sort(Comparator.comparingInt(l -> segments(l.prefix()).length));
        return this;
    }

    /** The page to show first: {@code /} unless set. */
    public synchronized Router start(String path) {
        setPath(path);
        return this;
    }

    /** Opens the app in a window. */
    public CompletableFuture<UiWindow> open(App app, UnaryOperator<WindowOptions> options) {
        return Ui.open(app, this, o -> options.apply(o.title(title))).thenApply(w -> {
            window = w;
            return w;
        });
    }

    /** The path shown now, with its query. */
    public synchronized String location() {
        return path + queryString(query);
    }

    // ---- navigating ----

    synchronized void navigate(String target) {
        String next = target.startsWith("/") || target.startsWith("?") ? target : "/" + target;
        if (next.startsWith("?")) next = path + next;
        if (next.equals(location())) return;
        backStack.push(location());
        forwardStack.clear();
        setPath(next);
    }

    synchronized void back() {
        if (backStack.isEmpty()) return;
        forwardStack.push(location());
        setPath(backStack.pop());
    }

    synchronized void forward() {
        if (forwardStack.isEmpty()) return;
        backStack.push(location());
        setPath(forwardStack.pop());
    }

    synchronized boolean canGoBack() {
        return !backStack.isEmpty();
    }

    synchronized boolean canGoForward() {
        return !forwardStack.isEmpty();
    }

    private void setPath(String target) {
        int q = target.indexOf('?');
        path = normal(q < 0 ? target : target.substring(0, q));
        query = q < 0 ? Map.of() : parseQuery(target.substring(q + 1));
    }

    String withQuery(String at, Map<String, String> current, String name, Object value) {
        Map<String, String> next = new LinkedHashMap<>(current);
        if (value == null) next.remove(name);
        else next.put(name, String.valueOf(value));
        return at + queryString(next);
    }

    synchronized List<Entry> navigation() {
        return pages.stream().map(PageRoute::entry).filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(Entry::order)).toList();
    }

    // ---- loading ----

    synchronized <T> Optional<T> load(String key, Supplier<T> loader) {
        Loaded found = loaded.get(key);
        if (found != null) {
            if (found.failure() != null) throw found.failure();
            @SuppressWarnings("unchecked")
            T value = (T) found.value();
            return Optional.ofNullable(value);
        }
        if (loading.add(key)) {
            Thread.ofVirtual().name("webview4j-load").start(() -> {
                Loaded result;
                try {
                    result = new Loaded(loader.get(), null);
                } catch (RuntimeException e) {
                    result = new Loaded(null, e);
                }
                synchronized (this) {
                    loading.remove(key);
                    loaded.put(key, result);
                }
                UiWindow w = window;
                if (w != null) w.refresh();
            });
        }
        return Optional.empty();
    }

    synchronized void forget(String key) {
        loaded.remove(key);
    }

    // ---- showing ----

    @Override
    public synchronized Component<?> render() {
        PageRoute route = null;
        Map<String, String> params = Map.of();
        String[] at = segments(path);
        for (PageRoute candidate : pages) {
            Map<String, String> found = match(candidate.segments(), at);
            if (found != null) {
                route = candidate;
                params = found;
                break;
            }
        }
        PageContext page = new PageContext(this, path, route == null ? null : route.pattern(), params, query);
        Component<?> content;
        if (route == null) {
            content = column(Ui.title("Not found"), text("There is no page at " + path + ".").tone(Tone.SECONDARY),
                    button("Go to the start").onClick(() -> page.navigate("/"))).padding(24).gap(12);
        } else {
            content = guarded(route.view(), page);
        }
        // Layouts from the innermost out: each wraps what the one inside made.
        boolean rootLayout = false;
        for (int i = layouts.size() - 1; i >= 0; i--) {
            LayoutRoute layout = layouts.get(i);
            if (!page.isAt(layout.prefix())) continue;
            if (layout.prefix().equals("/")) rootLayout = true;
            content = layout.view().render(page, content);
        }
        return rootLayout ? content : defaultLayout(page, content, route == null ? null : route.entry());
    }

    /** A page's errors stay in the page (Next.js' {@code error.tsx}): said for people, with "Try again". */
    private Component<?> guarded(PageView view, PageContext page) {
        try {
            return view.render(page);
        } catch (UiException e) {
            return column(Ui.title(e.title()), text(e.userMessage()).tone(Tone.SECONDARY),
                    button("Try again").onClick(loaded::clear)).padding(24).gap(12);
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.ERROR, "the page at " + page.path() + " failed", e);
            return column(Ui.title("This page could not be shown"),
                    text("Something went wrong while showing it. Try again; if it keeps happening, tell the people who make the app.")
                            .tone(Tone.SECONDARY),
                    text(e.toString()).mono().tone(Tone.TERTIARY),
                    button("Try again").onClick(loaded::clear)).padding(24).gap(12);
        }
    }

    /** The app's frame when it has no layout at {@code /}: title bar, side bar of the navigation, toolbar, status bar. */
    private Component<?> defaultLayout(PageContext page, Component<?> content, Entry entry) {
        Map<String, List<Component<?>>> sections = new LinkedHashMap<>();
        for (Entry e : navigation()) {
            sections.computeIfAbsent(e.section() == null ? "" : e.section(), s -> new ArrayList<>())
                    .add(sidebarItem(e.title()).key(e.path()).selected(e.path().equals("/") ? page.path().equals("/") : page.isAt(e.path()))
                            .onSelect(() -> page.navigate(e.path())));
        }
        String heading = entry != null ? entry.title()
                : navigation().stream().filter(e -> page.isAt(e.path()) && !e.path().equals("/")).map(Entry::title).findFirst().orElse(title);
        Toolbar toolbar = toolbar(heading,
                toolbarButton("Back").disabled(!page.canGoBack()).onClick(page::back),
                toolbarButton("Forward").disabled(!page.canGoForward()).onClick(page::forward));
        Component<?> body = sections.isEmpty() ? content
                : split(sidebar(sections.entrySet().stream()
                        .map(s -> sidebarSection(s.getKey().isEmpty() ? null : s.getKey(), s.getValue().toArray(Component<?>[]::new)))
                        .toArray(Component<?>[]::new)), content);
        return frame(body).titleBar(titleBar(title)).toolbar(toolbar).statusBar(statusBar(statusItem(location())));
    }

    // ---- paths ----

    static String normal(String path) {
        String p = path.isEmpty() ? "/" : path.startsWith("/") ? path : "/" + path;
        while (p.length() > 1 && p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return p;
    }

    static String[] segments(String path) {
        String p = normal(path);
        return p.equals("/") ? new String[0] : p.substring(1).split("/");
    }

    /** The slugs when {@code pattern} matches {@code at}; null when it does not. */
    static Map<String, String> match(String[] pattern, String[] at) {
        Map<String, String> params = new LinkedHashMap<>();
        for (int i = 0; i < pattern.length; i++) {
            String s = pattern[i];
            if (s.startsWith("{*") && s.endsWith("}")) {
                params.put(s.substring(2, s.length() - 1), String.join("/", java.util.Arrays.copyOfRange(at, Math.min(i, at.length), at.length)));
                return params;
            }
            if (i >= at.length) return null;
            if (s.startsWith("{") && s.endsWith("}")) params.put(s.substring(1, s.length() - 1), URLDecoder.decode(at[i], StandardCharsets.UTF_8));
            else if (!s.equals(at[i])) return null;
        }
        return pattern.length == at.length ? params : null;
    }

    private static Map<String, String> parseQuery(String text) {
        Map<String, String> q = new LinkedHashMap<>();
        for (String pair : text.split("&")) {
            if (pair.isEmpty()) continue;
            int eq = pair.indexOf('=');
            String name = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            q.put(name, eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return q;
    }

    private static String queryString(Map<String, String> query) {
        if (query.isEmpty()) return "";
        StringBuilder s = new StringBuilder();
        query.forEach((k, v) -> s.append(s.isEmpty() ? "?" : "&").append(URLEncoder.encode(k, StandardCharsets.UTF_8))
                .append('=').append(URLEncoder.encode(v, StandardCharsets.UTF_8)));
        return s.toString();
    }
}
