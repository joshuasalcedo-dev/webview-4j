package io.joshuasalcedo.swaswa.dom;

import io.joshuasalcedo.swaswa.component.Property;
import io.joshuasalcedo.swaswa.ffi.App;
import io.joshuasalcedo.swaswa.ffi.Closed;
import io.joshuasalcedo.swaswa.ffi.PageLoad;
import io.joshuasalcedo.swaswa.ffi.View;
import io.joshuasalcedo.swaswa.internal.Access;
import io.joshuasalcedo.swaswa.internal.Json;
import io.joshuasalcedo.swaswa.internal.Typed;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A view's page, from Java, typed: what its scripts give back as a type of the app's ({@link #eval(String, Class)}),
 * its own functions behind a Java interface ({@link #bind}), the browser's APIs (the clipboard, the window's size and
 * scroll, the location, the selection, an element's box), and values Java follows as {@link Property}s
 * ({@link #media}, {@link #online}, {@link #visible}). The page needs the bridge (an app page; a website only for the
 * origins its view allows).
 * <pre>{@code
 * Dom page = Dom.of(view);
 * record Total(double sum, int count) {}
 * Total t = page.eval("({sum: 12.5, count: 3})", Total.class).join();
 * Property<Boolean> dark = page.media("(prefers-color-scheme: dark)");
 * page.clipboard().writeText("copied from Java").join();
 * }</pre>
 */
public final class Dom {

    private static final String RUNTIME = runtime();
    private static final Map<App, Map<String, Dom>> PAGES = new ConcurrentHashMap<>();

    private final View view;
    private final AtomicLong next = new AtomicLong();
    /** Calls on their way: their answer completes them. */
    private final Map<Long, CompletableFuture<Object>> calls = new ConcurrentHashMap<>();
    /** Values followed: their kind, its argument, their property. */
    private record Watch(String kind, String arg, Property<Boolean> property) {}
    private final Map<Long, Watch> watches = new ConcurrentHashMap<>();
    private final Map<String, Property<Boolean>> media = new ConcurrentHashMap<>();
    private volatile CompletableFuture<Object> installed;

    private Dom(View view) {
        this.view = view;
        this.installed = install();
    }

    /** The view's page: one per view. */
    public static Dom of(View view) {
        Objects.requireNonNull(view, "view");
        App app = view.app();
        Map<String, Dom> pages = PAGES.computeIfAbsent(app, a -> {
            a.handle("swaswa.dom", call -> {
                Map<String, Dom> those = PAGES.get(a);
                Dom d = those == null ? null : those.get(call.view().id());
                if (d != null) d.told(Access.calls().fields(call));
                return null;
            });
            a.subscribe(Closed.class, closed -> {
                Map<String, Dom> those = PAGES.get(a);
                if (those != null) those.remove(closed.view());
            });
            return new ConcurrentHashMap<>();
        });
        return pages.computeIfAbsent(view.id(), id -> {
            Dom d = new Dom(view);
            view.subscribe(PageLoad.class, load -> {
                if (load.state() == PageLoad.State.FINISHED) d.installed = d.install();
            });
            return d;
        });
    }

    public View view() {
        return view;
    }

    // ---- scripts, typed ----

    /**
     * Runs {@code script} in the page; its value comes back as {@code type}: a record of the app's (by its components'
     * names), a text, a number, true or false, an enum, a list or map of those. A promise is not waited for: a page's
     * function that is async goes through {@link #bind}.
     */
    public <T> CompletableFuture<T> eval(String script, Class<T> type) {
        Objects.requireNonNull(type, "type");
        return view.eval(script).thenApply(json -> type.cast(Typed.as(json, boxed(type), "eval")));
    }

    /** {@link #eval(String, Class)} for a list: {@code evalList("[...document.links].map(a => a.href)", String.class)}. */
    @SuppressWarnings("unchecked")
    public <E> CompletableFuture<List<E>> evalList(String script, Class<E> element) {
        return view.eval(script).thenApply(json -> (List<E>) Typed.as(json, listOf(element), "eval"));
    }

    /**
     * The page's own functions as a Java interface: each method calls the function of its name on {@code window}
     * ({@link PageFunction} for another name, a path too: {@code "app.cart.total"}), its arguments as JSON (records
     * included), its answer, a promise's too, as the method's type. A method returning {@code CompletableFuture<T>} does
     * not wait; any other waits for the answer ({@code void}: for it to be done).
     * <pre>{@code
     * interface Cart {
     *     double total(List<Item> items);
     *     @PageFunction("app.cart.clear") CompletableFuture<Void> clear();
     * }
     * Cart cart = Dom.of(view).bind(Cart.class);
     * }</pre>
     */
    public <A> A bind(Class<A> api) {
        Objects.requireNonNull(api, "api");
        if (!api.isInterface()) throw new IllegalArgumentException(api.getName() + " is not an interface");
        Object proxy = Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[] {api}, (self, method, args) -> {
            if (method.getDeclaringClass() == Object.class) return objectMethod(self, method, args, api);
            if (method.isDefault()) return java.lang.reflect.InvocationHandler.invokeDefault(self, method, args);
            PageFunction named = method.getAnnotation(PageFunction.class);
            String name = named != null ? named.value() : method.getName();
            CompletableFuture<Object> answer = call(name, args == null ? List.of() : Arrays.asList(args));
            Type returns = method.getGenericReturnType();
            if (method.getReturnType() == CompletableFuture.class) {
                Type inner = returns instanceof ParameterizedType p ? p.getActualTypeArguments()[0] : Object.class;
                return answer.thenApply(json -> inner == Void.class ? null : Typed.as(json, inner, api.getSimpleName() + "." + method.getName()));
            }
            Object json = join(answer);
            if (method.getReturnType() == void.class) return null;
            return Typed.as(json, method.getReturnType().isPrimitive() ? boxed(method.getReturnType()) : returns,
                    api.getSimpleName() + "." + method.getName());
        });
        return api.cast(proxy);
    }

    private static Object objectMethod(Object self, Method method, Object[] args, Class<?> api) {
        return switch (method.getName()) {
            case "equals" -> self == args[0];
            case "hashCode" -> System.identityHashCode(self);
            default -> "the page's " + api.getSimpleName();
        };
    }

    /** A function of the page's, by name (a path: {@code "app.cart.total"}), with its arguments: its answer, as JSON values. */
    public CompletableFuture<Object> call(String function, List<?> args) {
        Objects.requireNonNull(function, "function");
        if (!function.matches("[A-Za-z_$][\\w$]*(\\.[A-Za-z_$][\\w$]*)*")) throw new IllegalArgumentException("not a function's name: " + function);
        return ask(id -> "__swaswaPage.call(" + id + ", " + Json.write(function) + ", " + Json.write(args) + ")");
    }

    /** A call through the page's runtime: its answer comes back by the bridge (a promise's when it settles). */
    private CompletableFuture<Object> ask(java.util.function.LongFunction<String> script) {
        long id = next.incrementAndGet();
        CompletableFuture<Object> answer = new CompletableFuture<>();
        calls.put(id, answer);
        installed.thenCompose(ok -> view.eval(script.apply(id))).whenComplete((v, failed) -> {
            if (failed != null) {
                calls.remove(id);
                answer.completeExceptionally(failed);
            }
        });
        return answer.orTimeout(30, java.util.concurrent.TimeUnit.SECONDS).whenComplete((v, e) -> calls.remove(id));
    }

    // ---- the browser's own ----

    /** The system's clipboard (native: for any page, a secure one or not, with no permission to ask). */
    public Clipboard clipboard() {
        App app = view.app();
        return new Clipboard() {
            @Override
            public CompletableFuture<Void> writeText(String text) {
                return app.writeClipboard(text);
            }

            @Override
            public CompletableFuture<String> readText() {
                return app.readClipboard().thenApply(text -> text.orElse(""));
            }
        };
    }

    /** The page's inner size: its viewport, in CSS pixels. */
    public CompletableFuture<Size> size() {
        return eval("({width: innerWidth, height: innerHeight})", Size.class);
    }

    /** CSS pixels to the screen's: the zoom and the monitor's scale together. */
    public CompletableFuture<Double> pixelRatio() {
        return eval("devicePixelRatio", Double.class);
    }

    /** How far the page is scrolled. */
    public CompletableFuture<Point> scroll() {
        return eval("({x: scrollX, y: scrollY})", Point.class);
    }

    public CompletableFuture<Void> scrollTo(double x, double y) {
        return view.eval("scrollTo(" + x + ", " + y + ")").thenApply(v -> null);
    }

    /** Where the page is: its address. */
    public CompletableFuture<String> url() {
        return eval("location.href", String.class);
    }

    /** The page's title. */
    public CompletableFuture<String> title() {
        return eval("document.title", String.class);
    }

    public CompletableFuture<Void> setTitle(String title) {
        return view.eval("document.title = " + Json.write(Objects.requireNonNull(title, "title"))).thenApply(v -> null);
    }

    /** The text the person has selected ("" if none). */
    public CompletableFuture<String> selection() {
        return eval("String(getSelection() ?? '')", String.class);
    }

    /**
     * The box of the first element {@code selector} matches, in the page's viewport (CSS pixels); empty if none does.
     */
    public CompletableFuture<Optional<Rect>> bounds(String selector) {
        return eval("(() => { const e = document.querySelector(" + Json.write(Objects.requireNonNull(selector, "selector")) + ");"
                + " if (!e) return null; const r = e.getBoundingClientRect(); return {x: r.x, y: r.y, width: r.width, height: r.height}; })()",
                Rect.class).thenApply(Optional::ofNullable);
    }

    // ---- values followed ----

    /** Whether the media query holds, now and as it changes: {@code media("(prefers-color-scheme: dark)")}, {@code media("(width >= 48rem)")}. */
    public Property<Boolean> media(String query) {
        Objects.requireNonNull(query, "query");
        return media.computeIfAbsent(query, q -> watch("media", q));
    }

    /** Whether the page's network is up, as it changes. */
    public Property<Boolean> online() {
        return media.computeIfAbsent("\0online", q -> watch("online", null));
    }

    /** Whether the page is seen (its window not minimized or hidden), as it changes. */
    public Property<Boolean> visible() {
        return media.computeIfAbsent("\0visible", q -> watch("visible", null));
    }

    private Property<Boolean> watch(String kind, String arg) {
        long id = next.incrementAndGet();
        Property<Boolean> property = new Property<>();
        watches.put(id, new Watch(kind, arg, property));
        Object now = join(installed.thenCompose(ok -> view.eval(watchScript(id, kind, arg))));
        property.set(Boolean.TRUE.equals(now));
        return property;
    }

    private static String watchScript(long id, String kind, String arg) {
        return "__swaswaPage.watch(" + id + ", " + Json.write(kind) + ", " + Json.write(arg) + ")";
    }

    // ---- the page's side ----

    /** Its runtime in the page (a page loaded anew: again), and the values followed, followed again. */
    private CompletableFuture<Object> install() {
        CompletableFuture<Object> done = view.eval(RUNTIME);
        watches.forEach((id, w) -> done.thenCompose(ok -> view.eval(watchScript(id, w.kind(), w.arg())))
                .thenAccept(now -> w.property().set(Boolean.TRUE.equals(now))));
        return done;
    }

    /** What the page told: a call's answer, or a followed value's change. */
    private void told(Map<String, Object> data) {
        if (data.get("call") instanceof Number n) {
            CompletableFuture<Object> answer = calls.remove(n.longValue());
            if (answer == null) return;
            if (data.get("error") instanceof String error) answer.completeExceptionally(new PageCallException(error));
            else answer.complete(data.get("value"));
        } else if (data.get("watch") instanceof Number n) {
            Watch w = watches.get(n.longValue());
            if (w != null) w.property().set(Boolean.TRUE.equals(data.get("value")));
        }
    }

    // ---- small things ----

    private static Object join(CompletableFuture<?> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException r) throw r;
            throw e;
        }
    }

    private static Class<?> boxed(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == boolean.class) return Boolean.class;
        return type;
    }

    private static Type listOf(Class<?> element) {
        return new ParameterizedType() {
            @Override public Type[] getActualTypeArguments() { return new Type[] {element}; }
            @Override public Type getRawType() { return List.class; }
            @Override public Type getOwnerType() { return null; }
        };
    }

    private static String runtime() {
        try (InputStream in = Dom.class.getResourceAsStream("page.js")) {
            if (in == null) throw new IllegalStateException("page.js is not in the jar");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
