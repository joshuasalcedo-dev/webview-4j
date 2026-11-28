package io.joshuasalcedo.swaswa.html;

import io.joshuasalcedo.swaswa.internal.Json;
import io.joshuasalcedo.swaswa.ffi.App;
import io.joshuasalcedo.swaswa.ffi.Closed;
import io.joshuasalcedo.swaswa.ffi.PageLoad;
import io.joshuasalcedo.swaswa.ffi.Theme;
import io.joshuasalcedo.swaswa.ffi.View;
import io.joshuasalcedo.swaswa.css.Style;
import io.joshuasalcedo.swaswa.css.StyleSheet;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * A view's page, live from Java: elements added to its {@link #body()} are in the page, what changes in them changes
 * there, and their events come to their listeners. Changes go in batches (a few milliseconds' worth at once);
 * {@link #flush()} sends what is waiting now. Style sheets and the colour scheme (light, dark, the system's) too.
 * If the page loads again, all of it is put back.
 * <pre>{@code
 * Document page = Document.of(view);
 * page.addStyleSheet(new StyleSheet().rule(".total", s -> s.set(CssProperty.FONT_WEIGHT, "600")));
 * Span total = Html.span("0").cssClass("total");
 * Button add = Html.button("Add");
 * add.addActionListener(e -> total.setText(String.valueOf(++count)));
 * page.body().add(Html.div(add, total));
 * }</pre>
 * The page needs the bridge (an app page; a website only for the origins its view allows).
 */
public final class Document {

    private static final String RUNTIME = runtime();
    /** The documents of each app, by view; the app's command for their events registered with the first. */
    private static final Map<App, Map<String, Document>> DOCUMENTS = new ConcurrentHashMap<>();

    private final View view;
    /** What plays the page's body: its {@link Body}, or a component of the app's own (an app is one). */
    private final HtmlElement<?> body;
    private String title;
    private String lang;
    private final Map<Integer, Node> nodes = new ConcurrentHashMap<>();
    private final Map<StyleSheet, Integer> sheets = new LinkedHashMap<>();
    /** How many times the page was drawn anew (mount): each batch says which drawing it changes. */
    private int epoch;
    /** The kinds' scripts in the page, by id: put in once, and again when the page loads anew. */
    private final Map<String, String> scripts = new LinkedHashMap<>();
    private final Map<StyleSheet, Runnable> sheetListeners = new ConcurrentHashMap<>();
    private int next = 1;
    /** The page's events, delivered in its order and one at a time (as Swing's event thread does). */
    private final java.util.TreeMap<Long, Map<String, Object>> arrived = new java.util.TreeMap<>();
    private long expected = 1;
    private final java.util.concurrent.ExecutorService delivery = java.util.concurrent.Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("swaswa-page-events").factory());
    private int nextSheet = 1;
    private Theme theme = Theme.SYSTEM;
    private List<List<Object>> waiting = new ArrayList<>();
    private boolean scheduled;
    private CompletableFuture<Void> sent = CompletableFuture.completedFuture(null);
    /** Done once the page has loaded and has all of it: a change sent before then is put back by the load's mount. */
    private volatile CompletableFuture<Void> loaded = new CompletableFuture<>();

    private Document(View view, HtmlElement<?> root) {
        this.view = view;
        this.body = root == null ? new Body(this) : root;
        this.body.document = this;
        this.body.key = 0;
        nodes.put(0, body);
    }

    /** The view's page, live: one per view. */
    public static Document of(View view) {
        return of(view, null);
    }

    /**
     * The view's page, live, with {@code root} as its body: what {@code root} holds is the page's, and it stays
     * {@code root}'s (an app is the root of its page: {@code SwaApplication}).
     *
     * @throws IllegalStateException if the view's page has another root already
     */
    public static Document of(View view, HtmlElement<?> root) {
        Objects.requireNonNull(view, "view");
        App app = view.app();
        Map<String, Document> documents = DOCUMENTS.computeIfAbsent(app, a -> {
            a.handle("swaswa.html", call -> {
                Map<String, Document> those = DOCUMENTS.get(a);
                Document d = those == null ? null : those.get(call.view().id());
                if (d != null) d.event(io.joshuasalcedo.swaswa.internal.Access.calls().fields(call));
                return null;
            });
            a.subscribe(Closed.class, closed -> {
                Map<String, Document> those = DOCUMENTS.get(a);
                if (those != null) those.remove(closed.view());
            });
            return new ConcurrentHashMap<>();
        });
        Document document = documents.computeIfAbsent(view.id(), id -> {
            Document d = new Document(view, root);
            view.subscribe(PageLoad.class, load -> {
                if (load.state() == PageLoad.State.STARTED) d.loading();
                else d.mount();
            });
            d.mount();
            return d;
        });
        if (root != null && document.body != root) throw new IllegalStateException("the view's page has another root already: " + document.body);
        return document;
    }

    public View view() {
        return view;
    }

    /** The page's {@code <body>}: add elements to it. With a root of the app's own ({@link #of(View, HtmlElement)}), that root. */
    public HtmlElement<?> body() {
        return body;
    }

    /** The page's {@code <title>}: its window's title too. */
    public synchronized Document setTitle(String title) {
        this.title = title;
        op("title", title == null ? "" : title);
        return this;
    }

    /** The page's language ({@code <html lang>}). */
    public synchronized Document setLang(String lang) {
        this.lang = lang;
        op("lang", lang);
        return this;
    }

    // ---- CSS ----

    /** Shows a style sheet in the page; what changes in it then, changes there. */
    public synchronized Document addStyleSheet(StyleSheet sheet) {
        Objects.requireNonNull(sheet, "sheet");
        if (sheets.containsKey(sheet)) return this;
        for (StyleSheet first : sheet.requires()) addStyleSheet(first);   // what it needs, before it
        int k = nextSheet++;
        sheets.put(sheet, k);
        Runnable changed = () -> op("sheet", k, sheet.toCss());
        sheetListeners.put(sheet, changed);
        io.joshuasalcedo.swaswa.internal.Access.looks().listen(sheet, changed);
        op("sheet", k, sheet.toCss());
        return this;
    }

    public synchronized Document removeStyleSheet(StyleSheet sheet) {
        Integer k = sheets.remove(sheet);
        if (k != null) {
            io.joshuasalcedo.swaswa.internal.Access.looks().unlisten(sheet, sheetListeners.remove(sheet));
            op("sheet", k, null);
        }
        return this;
    }

    /**
     * Light, dark, or the system's (as it changes): the window's theme ({@link View#setTheme}) and the page's
     * {@code color-scheme}, so {@link Style#lightDark}, {@link StyleSheet#dark} and the page's own controls follow.
     */
    public synchronized CompletableFuture<Void> setTheme(Theme theme) {
        this.theme = Objects.requireNonNull(theme, "theme");
        op("scheme", scheme(theme));
        return view.setTheme(theme).thenCompose(ok -> flush());
    }

    public synchronized Theme getTheme() {
        return theme;
    }

    private static String scheme(Theme theme) {
        return switch (theme) {
            case LIGHT -> "light";
            case DARK -> "dark";
            case SYSTEM -> "light dark";
        };
    }

    // ---- typed classes' own CSS ----

    /** The rules of the typed classes the page's elements use, once each, after their bases. */
    private StyleSheet utilities;
    private final java.util.Set<String> used = new java.util.HashSet<>();
    /** Their rules in their order ({@link io.joshuasalcedo.swaswa.css.CssClass#order()}, then first use). */
    private final java.util.TreeMap<long[], String> rules = new java.util.TreeMap<>(
            (a, b) -> a[0] != b[0] ? Long.compare(a[0], b[0]) : Long.compare(a[1], b[1]));
    private long firstUse;

    /** A typed class one of the page's elements has: its base (once), and its rule (once), in the page, in its order. */
    synchronized void uses(io.joshuasalcedo.swaswa.css.CssClass c) {
        if (used.contains(c.className())) return;
        String css = c.css();
        if (css == null) return;
        used.add(c.className());
        StyleSheet base = c.base();
        if (base != null && !sheets.containsKey(base)) addStyleSheet(base);
        if (utilities == null) {
            utilities = new StyleSheet();
            addStyleSheet(utilities);
        }
        long order = c.order();
        rules.put(new long[] {order, firstUse++}, css);
        if (rules.lastKey()[0] == order && rules.lastKey()[1] == firstUse - 1) {
            utilities.css(css);   // last in order: added at the end
        } else {
            // in its place: the sheet written anew (one send: a sheet's latest text only)
            utilities.clear();
            utilities.css(String.join("\n", rules.values()));
        }
    }

    // ---- to the page ----

    /**
     * Sends the changes waiting; the future completes once the page has them (and, while it loads, once it has loaded
     * and has all of it: at most 10 seconds).
     */
    public synchronized CompletableFuture<Void> flush() {
        scheduled = false;
        if (!waiting.isEmpty()) {
            // with the page's epoch it was made in: a batch that reaches the page after the page was drawn anew is dropped
            String script = "window.__swaswaDom && __swaswaDom.apply(" + Json.write(waiting) + ", " + epoch + ")";
            waiting = new ArrayList<>();
            send(script);
        }
        CompletableFuture<Void> page = loaded;
        return sent.thenCompose(ok -> page.copy().completeOnTimeout(null, 10, TimeUnit.SECONDS));
    }

    /** A page is loading: changes wait for it (its load puts all of it back). */
    private synchronized void loading() {
        if (loaded.isDone()) loaded = new CompletableFuture<>();
    }

    synchronized void op(Object... op) {
        if ("sheet".equals(op[0])) {
            // a sheet's whole text: only its latest is needed (a sheet growing class by class, one send)
            waiting.removeIf(w -> "sheet".equals(w.get(0)) && java.util.Objects.equals(w.get(1), op[1]));
        }
        waiting.add(Arrays.asList(op.clone()));
        if (!scheduled) {
            scheduled = true;
            CompletableFuture.delayedExecutor(2, TimeUnit.MILLISECONDS).execute(this::flush);
        }
    }

    private CompletableFuture<Object> send(String script) {
        CompletableFuture<Object> value = sent.thenCompose(ok -> view.eval(script));
        sent = value.handle((v, failed) -> {
            if (failed != null) System.getLogger("swaswa.html").log(System.Logger.Level.DEBUG, "the page did not take a change", failed);
            return null;
        });
        return value;
    }

    /** All of it put in the page again: its runtime, the scheme, the sheets, the body's elements (a page loaded anew). */
    private synchronized void mount() {
        waiting = new ArrayList<>();
        scheduled = false;
        nodes.clear();
        nodes.put(0, body);
        next = 1;
        List<List<Object>> ops = new ArrayList<>();
        epoch++;   // what was made before this, sent or not, is the page's past
        ops.add(Arrays.asList("reset", epoch));
        ops.add(Arrays.asList("seq", 0));
        synchronized (arrived) {
            arrived.clear();
            expected = 1;
        }
        ops.add(Arrays.asList("scheme", scheme(theme)));
        if (title != null) ops.add(Arrays.asList("title", title));
        if (lang != null) ops.add(Arrays.asList("lang", lang));
        sheets.forEach((sheet, k) -> ops.add(Arrays.asList("sheet", k, sheet.toCss())));
        scripts.forEach((kind, script) -> ops.add(Arrays.asList("script", kind, script)));
        Map<String, Object> self = body.tree(this);   // numbers its children; the body itself is the page's
        body.key = 0;
        nodes.put(0, body);
        @SuppressWarnings("unchecked")
        Map<String, String> attrs = (Map<String, String>) self.get("attrs");
        attrs.forEach((k, v) -> ops.add(Arrays.asList("attr", 0, k, v)));
        @SuppressWarnings("unchecked")
        List<String> events = (List<String>) self.getOrDefault("events", List.of());
        for (String e : events) ops.add(Arrays.asList("listen", 0, e));
        for (Object child : (List<?>) self.get("children")) ops.add(Arrays.asList("append", 0, child));
        CompletableFuture<Void> page = loaded;
        // the page has it all once it has loaded: then changes are sure to reach it
        send(RUNTIME + "\n;__swaswaDom.apply(" + Json.write(ops) + ") && document.readyState").thenAccept(state -> {
            if ("complete".equals(state)) page.complete(null);
        });
    }

    /** A kind's script, in the page once ({@link HtmlElement#kindScripts()}). */
    synchronized void usesScript(PageScript script) {
        if (scripts.containsKey(script.id())) return;
        if (script.directory() != null) view.app().serveShared(script.directory(), script.loader());   // its files, before it loads them
        scripts.put(script.id(), script.source());
        op("script", script.id(), script.source());
    }

    // ---- numbering ----

    synchronized int number(Node node) {
        int k = next++;
        nodes.put(k, node);
        return k;
    }

    void forget(int key) {
        if (key != 0) nodes.remove(key);
    }

    // ---- from the page ----

    /**
     * An event from the page, as it arrived (each page call on a thread of its own: not always in order): delivered in
     * the page's order. One missing for long (the page loaded anew while it was on its way) is not waited for.
     */
    private void event(Map<String, Object> data) {
        if (!(data.get("seq") instanceof Number n)) {
            delivery.execute(() -> deliver(data));
            return;
        }
        synchronized (arrived) {
            long seq = n.longValue();
            if (seq < expected) return;   // from before the page was mounted anew
            arrived.put(seq, data);
            drain();
            if (!arrived.isEmpty()) {
                long waitingFor = expected;
                CompletableFuture.delayedExecutor(250, TimeUnit.MILLISECONDS).execute(() -> {
                    synchronized (arrived) {
                        if (expected == waitingFor && !arrived.isEmpty()) {
                            expected = arrived.firstKey();
                            drain();
                        }
                    }
                });
            }
        }
    }

    private void drain() {
        while (!arrived.isEmpty() && arrived.firstKey() == expected) {
            Map<String, Object> next = arrived.pollFirstEntry().getValue();
            expected++;
            delivery.execute(() -> deliver(next));
        }
    }

    private void deliver(Map<String, Object> data) {
        Object k = data.get("key");
        Object type = data.get("type");
        if (!(k instanceof Number n) || !(type instanceof String t)) return;
        Node dragged = data.get("dragged") instanceof Number d ? nodes.get(d.intValue()) : null;
        if (nodes.get(n.intValue()) instanceof HtmlElement<?> element) element.fire(t, data, dragged);
    }

    private static String runtime() {
        try (InputStream in = Document.class.getResourceAsStream("dom.js")) {
            if (in == null) throw new IllegalStateException("dom.js is not in the jar");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
