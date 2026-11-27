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
import java.util.concurrent.ConcurrentHashMap;
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
        getStyle().set(CssProperty.DISPLAY, "contents");   // React's elements lay out as if they were its parent's
    }

    /** Its name, as its library registered it. */
    public final String getComponent() {
        return component;
    }

    // ---- props: for the typed setters ----

    /** Sets prop {@code name} (JSON: text, numbers, booleans, records, lists; a literal's word); null removes it. */
    @SuppressWarnings("unchecked")
    protected final Self prop(String name, Object value) {
        Object v = value instanceof HtmlLiteral literal ? literal.literal() : value;
        Object old;
        synchronized (props) {
            old = v == null ? props.remove(name) : props.put(name, v);
        }
        if (!Objects.equals(old, v)) {
            Property<Object> p = propProperties.get(name);
            if (p != null) p.set(v);
            kindChanged();
        }
        return (Self) this;
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
        synchronized (props) {
            data.put("props", new LinkedHashMap<>(props));
        }
        List<String> events = new ArrayList<>(listeners.keySet());
        synchronized (binds) {
            for (String e : binds.values()) if (!events.contains(e)) events.add(e);
            data.put("binds", new LinkedHashMap<>(binds));
        }
        data.put("events", events);
        data.put("children", takesChildren);
        return data;
    }

    @Override
    protected final void kindEvent(Map<String, Object> data) {
        String name = String.valueOf(data.get("name"));
        Object payload = data.get("payload");
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
