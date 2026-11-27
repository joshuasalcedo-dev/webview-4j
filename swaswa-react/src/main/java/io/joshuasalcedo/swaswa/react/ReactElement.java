package io.joshuasalcedo.swaswa.react;

import io.joshuasalcedo.swaswa.component.Property;
import io.joshuasalcedo.swaswa.css.CssProperty;
import io.joshuasalcedo.swaswa.html.HtmlElement;
import io.joshuasalcedo.swaswa.html.HtmlLiteral;
import io.joshuasalcedo.swaswa.html.PageScript;
import io.joshuasalcedo.swaswa.internal.Typed;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import io.joshuasalcedo.swaswa.internal.Json;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A real React component in the page, as a Java component: what its typed classes extend (a library's, generated from
 * its TypeScript props). React draws it inside this element; its children (what {@link #add} adds) are its
 * {@code children}, where it puts them. A prop set here renders it anew; an event it sends comes back to its
 * listeners; a value the person changes (an input's {@code value}, a switch's {@code checked}) is kept here at once
 * ({@link #bind}), so the page and Java agree without a round trip.
 * <pre>{@code
 * public final class Switch extends ReactElement<Switch> {
 *     public Switch() { super("Switch", false); bind("checked", "onCheckedChange"); }
 *     public Switch setChecked(boolean on) { return prop("checked", on); }
 *     public Property<Boolean> checkedProperty() { return propProperty("checked", Boolean.class); }
 *     @Override protected List<PageScript> libraries() { return List.of(MyLibrary.bundle()); }
 * }
 * }</pre>
 *
 * @param <Self> the component's own class
 */
public abstract class ReactElement<Self extends ReactElement<Self>> extends HtmlElement<Self> {

    private final String component;
    private final boolean takesChildren;
    private final Map<String, Object> props = Collections.synchronizedMap(new LinkedHashMap<>());
    /** The props as Java gave them (records, not their JSON): an item's menu is made from those. */
    private final Map<String, Object> raw = new LinkedHashMap<>();
    private final Map<String, List<ReactListener>> listeners = new ConcurrentHashMap<>();
    /** A prop the page changes itself, and the event it changes it with: {@code value} by {@code onChange}. */
    private final Map<String, String> binds = new LinkedHashMap<>();
    private final Map<String, Property<Object>> propProperties = new ConcurrentHashMap<>();

    /**
     * @param component     its name, as its library registered it ({@code "Button"})
     * @param takesChildren whether it shows children (its {@code children} prop): what is added to it goes there
     */
    protected ReactElement(String component, boolean takesChildren) {
        super("div", false);
        this.component = Objects.requireNonNull(component, "component");
        this.takesChildren = takesChildren;
        // React's elements lay out as if they were its parent's (the page's rule: display: contents); its own style is
        // the component's (React's style prop), as its classes are its className
        setAttribute("data-swaswa-react", component);
        io.joshuasalcedo.swaswa.internal.Access.looks().listen(getStyle(), this::kindChanged);
    }

    /** Its name, as its library registered it. */
    public final String getComponent() {
        return component;
    }

    // ---- props: for the typed setters ----

    /** Sets prop {@code name} (JSON: text, numbers, booleans, records, lists; a literal's word); null removes it. */
    @SuppressWarnings("unchecked")
    protected final Self prop(String name, Object value) {
        Object v = json(value);
        Object old;
        synchronized (props) {
            old = v == null ? props.remove(name) : props.put(name, v);
            if (value == null) raw.remove(name);
            else raw.put(name, value);
        }
        if (!Objects.equals(old, v)) {
            Property<Object> p = propProperties.get(name);
            if (p != null) p.set(v);
            kindChanged();
        }
        return (Self) this;
    }

    /**
     * A value as the component reads it, as JSON: a literal's word, a record's fields (those that are null left out, as
     * TypeScript's optional ones are), lists and maps of these.
     */
    private static Object json(Object value) {
        return switch (value) {
            case null -> null;
            case HtmlLiteral literal -> literal.literal();
            // a day: the page's Date at its midnight; a moment: the page's Date of it
            case java.time.LocalDate day -> Map.of("$swaswa", "date", "day", day.toString());
            case java.time.LocalDateTime local -> Map.of("$swaswa", "date", "local", local.toString());
            case java.time.Instant at -> Map.of("$swaswa", "date", "at", at.toString());
            case java.time.OffsetDateTime at -> Map.of("$swaswa", "date", "at", at.toInstant().toString());
            case java.time.ZonedDateTime at -> Map.of("$swaswa", "date", "at", at.toInstant().toString());
            // bytes: served by the app when the page needs them, fetched there into an ArrayBuffer
            case byte[] data -> new Served(data, null);
            case java.nio.file.Path file -> new Served(null, file);
            // a style: React's style object (camelCase; custom properties as they are)
            case io.joshuasalcedo.swaswa.css.Style style -> styleObject(style);
            case Record record -> {
                Map<String, Object> fields = new LinkedHashMap<>();
                for (java.lang.reflect.RecordComponent part : record.getClass().getRecordComponents()) {
                    try {
                        java.lang.reflect.Method read = part.getAccessor();
                        read.setAccessible(true);
                        Object v = json(read.invoke(record));
                        if (v != null) fields.put(part.getName(), v);
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalArgumentException("cannot read " + record.getClass().getName() + "." + part.getName(), e);
                    }
                }
                yield fields;
            }
            case Map<?, ?> map -> {
                Map<String, Object> out = new LinkedHashMap<>();
                map.forEach((k, v) -> out.put(String.valueOf(k), json(v)));
                yield out;
            }
            case Iterable<?> items -> {
                List<Object> out = new ArrayList<>();
                for (Object item : items) out.add(json(item));
                yield out;
            }
            default -> value;
        };
    }

    /** Bytes for the page, served by the app once this is in a page ({@link io.joshuasalcedo.swaswa.ffi.App#serveBytes}). */
    private record Served(byte[] data, java.nio.file.Path file) {}

    private static Map<String, Object> styleObject(io.joshuasalcedo.swaswa.css.Style style) {
        Map<String, Object> out = new LinkedHashMap<>();
        style.declarations().forEach((property, value) -> {
            if (property.startsWith("--")) {
                out.put(property, value);
                return;
            }
            StringBuilder camel = new StringBuilder();
            boolean up = false;
            for (char c : property.toCharArray()) {
                if (c == '-') up = true;
                else {
                    camel.append(up ? Character.toUpperCase(c) : c);
                    up = false;
                }
            }
            out.put(camel.toString(), value);
        });
        return out;
    }

    /** What the app serves for this one's bytes, by what they are: served once, let go when no prop holds them. */
    private final Map<Object, String> servedUrls = new java.util.IdentityHashMap<>();

    /** {@code value} for the page: its bytes served (as {@code {$swaswa: "bytes", url}}), the rest as it is. */
    private Object forPage(Object value, io.joshuasalcedo.swaswa.ffi.App app, java.util.Set<String> used) {
        return switch (value) {
            case Served served -> {
                Object key = served.data() != null ? served.data() : served.file();
                String url;
                synchronized (servedUrls) {
                    url = servedUrls.get(key);
                    if (url == null && app != null) {
                        byte[] data = served.data();
                        String type = null;
                        if (data == null) {
                            try {
                                data = java.nio.file.Files.readAllBytes(served.file());
                                type = java.nio.file.Files.probeContentType(served.file());
                            } catch (java.io.IOException e) {
                                throw new java.io.UncheckedIOException(e);
                            }
                        }
                        url = app.serveBytes(data, type);
                        servedUrls.put(key, url);
                    }
                }
                if (url == null) yield null;
                used.add(url);
                yield Map.of("$swaswa", "bytes", "url", url);
            }
            case Map<?, ?> map -> {
                Map<String, Object> out = new LinkedHashMap<>();
                map.forEach((k, v) -> out.put(String.valueOf(k), forPage(v, app, used)));
                yield out;
            }
            case List<?> items -> {
                List<Object> out = new ArrayList<>();
                for (Object item : items) out.add(forPage(item, app, used));
                yield out;
            }
            default -> value;
        };
    }

    /** Prop {@code name} as {@code type}; null when not set. */
    protected final <T> T getProp(String name, Class<T> type) {
        Object v = props.get(name);
        return v == null ? null : type.cast(Typed.as(v, type, name));
    }

    /**
     * Prop {@code name}, live: what it is, set here or by the person ({@link #bind}); setting it sets the prop. Bind a
     * state to it ({@code sw.checkedProperty().bindBidirectional(DARK.in(session))}).
     */
    @SuppressWarnings("unchecked")
    protected final <T> Property<T> propProperty(String name, Class<T> type) {
        Property<Object> p = propProperties.computeIfAbsent(name, n -> {
            Property<Object> made = new Property<>(props.get(n));
            made.addListener((old, now) -> prop(n, now));
            return made;
        });
        return (Property<T>) (Property<?>) p;
    }

    /** {@code prop} is one the page changes itself, with {@code event} ({@code bind("value", "onChange")}). */
    protected final void bind(String prop, String event) {
        synchronized (binds) {
            binds.put(Objects.requireNonNull(prop, "prop"), Objects.requireNonNull(event, "event"));
        }
    }

    // ---- components in props: an icon, a label, actions ----

    /** The holders of the components given as props, by prop: children of this one, which the page moves into place. */
    private final Map<String, io.joshuasalcedo.swaswa.html.Div> slots = new LinkedHashMap<>();

    /**
     * Prop {@code name} is {@code node}: a component (or several, in one) the page draws where the component draws that
     * prop ({@code icon}, {@code label}, {@code actions}). Null takes it away.
     */
    @SuppressWarnings("unchecked")
    protected final Self slot(String name, io.joshuasalcedo.swaswa.html.Node node) {
        io.joshuasalcedo.swaswa.html.Div old;
        synchronized (slots) {
            old = slots.remove(name);
        }
        if (old != null) remove(old);
        synchronized (props) {
            props.remove(name);   // a component instead of its text
        }
        if (node != null) {
            io.joshuasalcedo.swaswa.html.Div holder = new io.joshuasalcedo.swaswa.html.Div(node);
            holder.setAttribute("data-swaswa-slot", name);
            holder.getStyle().set(CssProperty.DISPLAY, "contents");
            synchronized (slots) {
                slots.put(name, holder);
            }
            add(holder);   // first in the page, then where the prop is drawn
        }
        kindChanged();
        return (Self) this;
    }

    // ---- enabled: the component's own disabled ----

    /** Enabled or not: the component itself ({@code disabled}), as its wrapper. */
    @Override
    public Self setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        return prop("disabled", enabled ? null : Boolean.TRUE);
    }

    // ---- classes: the component's own className ----

    /**
     * Classes on the component itself (its {@code className}: a Tailwind class styles the button React draws, not a
     * wrapper). Typed classes ({@code cssClass(Tw.Mt.MT_4)}) bring their CSS to the page as for any element.
     */
    @Override
    public Self cssClass(String... names) {
        super.cssClass(names);
        return className();
    }

    @Override
    public Self removeCssClass(String name) {
        super.removeCssClass(name);
        return className();
    }

    private Self className() {
        java.util.Set<String> classes = getCssClasses();
        return prop("className", classes.isEmpty() ? null : String.join(" ", classes));
    }

    // ---- events ----

    /** {@code listener} is told when it sends {@code event} ({@code onClick}, {@code onOpenChange}). */
    @SuppressWarnings("unchecked")
    public final Self onEvent(String event, ReactListener listener) {
        boolean first = !listeners.containsKey(event);
        listeners.computeIfAbsent(event, e -> new CopyOnWriteArrayList<>()).add(Objects.requireNonNull(listener, "listener"));
        if (first) kindChanged();   // the page passes a function for it now
        return (Self) this;
    }

    @SuppressWarnings("unchecked")
    public final Self removeEventListener(String event, ReactListener listener) {
        List<ReactListener> list = listeners.get(event);
        if (list != null) list.remove(listener);
        return (Self) this;
    }

    // ---- its kind ----

    /** The bundles its components come from, after React's runtime (a library's: {@code PageScript.resource(...)}). */
    protected List<PageScript> libraries() {
        return List.of();
    }

    @Override
    protected final String kind() {
        return "react";
    }

    @Override
    protected final List<PageScript> kindScripts() {
        List<PageScript> all = new ArrayList<>();
        all.add(React.runtime());
        all.addAll(libraries());
        return all;
    }

    @Override
    protected final Object kindData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("component", component);
        Map<String, Object> now;
        synchronized (props) {
            now = new LinkedHashMap<>(props);
        }
        if (!getStyle().isEmpty()) now.putIfAbsent("style", styleObject(getStyle()));
        java.util.Set<String> used = new java.util.HashSet<>();
        io.joshuasalcedo.swaswa.ffi.App app = pageApp();
        @SuppressWarnings("unchecked")
        Map<String, Object> forPage = (Map<String, Object>) forPage(now, app, used);
        data.put("props", forPage);
        if (app != null) {
            synchronized (servedUrls) {   // bytes no prop holds any more: served no more
                servedUrls.values().removeIf(url -> {
                    if (used.contains(url)) return false;
                    app.unserveBytes(url);
                    return true;
                });
            }
        }
        List<String> events = new ArrayList<>(listeners.keySet());
        synchronized (binds) {
            for (String e : binds.values()) if (!events.contains(e)) events.add(e);
            data.put("binds", new LinkedHashMap<>(binds));
        }
        data.put("events", events);
        data.put("children", takesChildren);
        synchronized (slots) {
            data.put("slots", new ArrayList<>(slots.keySet()));
        }
        synchronized (adapters) {
            Map<String, Object> specs = new LinkedHashMap<>();
            adapters.forEach((prop, a) -> specs.put(prop, Map.of("methods", a.methods(), "events", a.events())));
            data.put("adapters", specs);
        }
        synchronized (handles) {
            data.put("handles", new ArrayList<>(handles));
        }
        data.put("menus", menuTables());
        return data;
    }

    // ---- adapters: the component calls Java (a chat's commands, queries, events) ----

    private record Adapter(List<String> methods, List<String> events, ReactAdapter adapter) {}

    private final Map<String, Adapter> adapters = new LinkedHashMap<>();

    /**
     * Prop {@code name} is an adapter: an object of functions the component calls ({@code methods}, by path:
     * {@code "commands.sendMessage"}), answered by {@code adapter} in Java, and of functions it listens with
     * ({@code events}: {@code "events.subscribe"}), told what happens by {@code adapter}.
     */
    @SuppressWarnings("unchecked")
    protected final Self adapter(String name, List<String> methods, List<String> events, ReactAdapter adapter) {
        synchronized (adapters) {
            if (adapter == null) adapters.remove(name);
            else adapters.put(name, new Adapter(List.copyOf(methods), List.copyOf(events), adapter));
        }
        kindChanged();
        return (Self) this;
    }

    /** The component called an adapter's function: Java answers its promise when it has the answer. */
    private void called(Map<?, ?> p) {
        Object id = p.get("id");
        Adapter a;
        synchronized (adapters) {
            a = adapters.get(String.valueOf(p.get("prop")));
        }
        CompletableFuture<?> answer;
        try {
            if (a == null) throw new IllegalStateException("no adapter " + p.get("prop"));
            @SuppressWarnings("unchecked")
            List<Object> args = p.get("args") instanceof List<?> l ? (List<Object>) l : List.of();
            answer = a.adapter().call(new ReactCall(String.valueOf(p.get("method")), args));
            if (answer == null) answer = CompletableFuture.completedFuture(null);
        } catch (RuntimeException e) {
            answer = CompletableFuture.failedFuture(e);
        }
        answer.whenComplete((value, failed) -> {
            Throwable cause = failed instanceof CompletionException c && c.getCause() != null ? c.getCause() : failed;
            String json = cause == null ? toPage(value)
                    : Json.write(cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage());
            pageEval("window.__swaswaReact && __swaswaReact.answer(" + Json.write(id) + ", " + (cause == null) + ", " + json + ")");
        });
    }

    /** The component listens at an adapter's function: what happens there is given to it. */
    private void subscribed(Map<?, ?> p) {
        String prop = String.valueOf(p.get("prop"));
        String method = String.valueOf(p.get("method"));
        Adapter a;
        synchronized (adapters) {
            a = adapters.get(prop);
        }
        if (a == null) return;
        a.adapter().subscribe(method, event -> pageEval("window.__swaswaReact && __swaswaReact.emit(" + pageKey() + ", "
                + Json.write(prop) + ", " + Json.write(method) + ", " + toPage(event) + ")"));
    }

    /** A value Java gives the page outside its props (an answer, an event): as JSON, its records remembered for menus. */
    private String toPage(Object value) {
        int before = origins.size();
        remember(value);
        Object page = forPage(json(value), pageApp(), new java.util.HashSet<>());
        if (origins.size() != before && !menus.isEmpty()) kindChanged();   // menus for what the page has now
        return Json.write(page);
    }

    // ---- handles: Java calls the component (a dialog's close, an editor's reveal) ----

    private final java.util.Set<String> handles = new java.util.LinkedHashSet<>();

    /**
     * Calls {@code method} of what the component put in its ref prop {@code name} ({@code actionsRef}: its actions):
     * what it returns, when the page gives it.
     */
    protected final CompletableFuture<Object> handle(String name, String method, Object... args) {
        boolean added;
        synchronized (handles) {
            added = handles.add(name);
        }
        if (added) kindChanged();   // the page gives the component a ref for it from now on
        List<Object> list = new ArrayList<>();
        for (Object a : args) list.add(json(a));
        return pageEval("window.__swaswaReact && __swaswaReact.invoke(" + pageKey() + ", " + Json.write(name) + ", "
                + Json.write(method) + ", " + Json.write(list) + ")");
    }

    /** Ref prop {@code name} is given to the component from the start (its handle's methods may be called any time). */
    protected final void handleProp(String name) {
        synchronized (handles) {
            handles.add(name);
        }
    }

    // ---- menus: the component asks for an item's menu (a row's, a file's, a message's), Java has it ready ----

    private record Menu(Class<?> type, Function<Object, List<MenuItem>> builder) {}

    private static final String SEP = "\u0000";
    private final Map<String, Menu> menus = new ConcurrentHashMap<>();
    /** The records Java gave the page (in props, answers, events), by their key: an item's menu is made from it. */
    private final Map<String, Object> origins = Collections.synchronizedMap(new LinkedHashMap<>());
    /** What each menu entry does, by prop, item and place. */
    private final Map<String, Runnable> menuActions = new ConcurrentHashMap<>();

    /**
     * Prop {@code name} is a menu of an item ({@code rowMenu: (row) => entries}): for each {@code type} Java gave the
     * page, {@code builder}'s entries, ready when the component asks; a chosen entry's action runs in Java.
     */
    @SuppressWarnings("unchecked")
    protected final <T> Self menu(String name, Class<T> type, Function<? super T, List<MenuItem>> builder) {
        if (builder == null) menus.remove(name);
        else menus.put(name, new Menu(type, (Function<Object, List<MenuItem>>) (Function<?, ?>) builder));
        kindChanged();
        return (Self) this;
    }

    /** Every menu's entries for every item the page has, by prop and item key. */
    private Map<String, Object> menuTables() {
        Map<String, Object> tables = new LinkedHashMap<>();
        if (menus.isEmpty()) return tables;
        List<Object> now;
        synchronized (props) {
            now = new ArrayList<>(raw.values());
        }
        remember(now);
        Map<String, Object> known;
        synchronized (origins) {
            known = new LinkedHashMap<>(origins);
        }
        menuActions.clear();
        menus.forEach((prop, menu) -> {
            Map<String, Object> table = new LinkedHashMap<>();
            known.forEach((key, item) -> {
                if (!menu.type().isInstance(item)) return;
                List<MenuItem> entries;
                try {
                    entries = menu.builder().apply(item);
                } catch (RuntimeException e) {
                    System.getLogger("swaswa.react").log(System.Logger.Level.WARNING, component + " " + prop + " failed for " + key, e);
                    return;
                }
                List<Object> out = new ArrayList<>();
                for (int i = 0; entries != null && i < entries.size(); i++) {
                    MenuItem e = entries.get(i);
                    if (e.isSeparator()) {
                        out.add("separator");
                        continue;
                    }
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("label", e.label());
                    if (e.shortcut() != null) entry.put("shortcut", e.shortcut());
                    if (e.checked() != null) entry.put("checked", e.checked());
                    if (e.disabled()) entry.put("disabled", true);
                    if (e.action() != null) menuActions.put(prop + SEP + key + SEP + i, e.action());
                    out.add(entry);
                }
                table.put(key, out);
            });
            tables.put(prop, table);
        });
        return tables;
    }

    /** The records in {@code value} (and in what they hold), by key: their id, or their whole JSON. */
    private void remember(Object value) {
        switch (value) {
            case null -> {}
            case java.nio.file.Path path -> {}
            case Record record -> {
                origins.put(keyOf(record), record);
                for (java.lang.reflect.RecordComponent part : record.getClass().getRecordComponents()) {
                    try {
                        java.lang.reflect.Method read = part.getAccessor();
                        read.setAccessible(true);
                        remember(read.invoke(record));
                    } catch (ReflectiveOperationException e) {
                        // not read: not remembered
                    }
                }
            }
            case Map<?, ?> map -> map.values().forEach(this::remember);
            case Iterable<?> items -> {
                for (Object item : items) remember(item);
            }
            default -> {}
        }
    }

    /** An item's key, as the page finds it: its {@code id} (a whole number as such), else its JSON. */
    private static String keyOf(Record record) {
        for (java.lang.reflect.RecordComponent part : record.getClass().getRecordComponents()) {
            if (!part.getName().equals("id")) continue;
            try {
                java.lang.reflect.Method read = part.getAccessor();
                read.setAccessible(true);
                Object id = read.invoke(record);
                if (id instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue())) return Long.toString(n.longValue());
                if (id != null) return String.valueOf(id);
            } catch (ReflectiveOperationException e) {
                break;
            }
        }
        return Json.write(json(record));
    }

    @Override
    protected final void kindEvent(Map<String, Object> data) {
        String name = String.valueOf(data.get("name"));
        Object payload = data.get("payload");
        // the bridge's own: an adapter's call or listening, a menu entry chosen
        if (payload instanceof Map<?, ?> p) {
            switch (name) {
                case "$call" -> { called(p); return; }
                case "$subscribe" -> { subscribed(p); return; }
                case "$menu" -> {
                    Runnable action = menuActions.get(p.get("prop") + SEP + p.get("key") + SEP + ((Number) p.get("index")).intValue());
                    if (action != null) action.run();
                    return;
                }
                default -> {}
            }
        }
        List<String> changed = new ArrayList<>();
        synchronized (binds) {
            binds.forEach((prop, event) -> { if (event.equals(name)) changed.add(prop); });
        }
        for (String prop : changed) {
            Object v = bound(prop, payload);
            synchronized (props) {
                props.put(prop, v);   // quietly: the page has it already
            }
            Property<Object> p = propProperties.get(prop);
            if (p != null) p.set(v);  // its prop() sees no change: nothing sent back
        }
        List<ReactListener> list = listeners.get(name);
        if (list == null) return;
        ReactEvent event = new ReactEvent(this, name, payload);
        for (ReactListener l : list) {
            try {
                l.handle(event);
            } catch (RuntimeException e) {
                System.getLogger("swaswa.react").log(System.Logger.Level.WARNING, component + " " + name + " listener failed", e);
            }
        }
    }

    /** What a bound prop is now, from its event: a DOM event's value (or checked, for {@code checked}), else the payload. */
    private static Object bound(String prop, Object payload) {
        if (payload instanceof Map<?, ?> m && (m.containsKey("value") || m.containsKey("checked"))) {
            return prop.equals("checked") ? m.get("checked") : m.get("value");
        }
        return payload;
    }
}
