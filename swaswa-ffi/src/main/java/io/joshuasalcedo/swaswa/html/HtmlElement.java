package io.joshuasalcedo.swaswa.html;

import io.joshuasalcedo.swaswa.component.ClientKey;
import io.joshuasalcedo.swaswa.component.Component;
import io.joshuasalcedo.swaswa.component.Constraint;
import io.joshuasalcedo.swaswa.component.Container;
import io.joshuasalcedo.swaswa.component.Layout;
import io.joshuasalcedo.swaswa.component.Listener;
import io.joshuasalcedo.swaswa.component.Property;
import io.joshuasalcedo.swaswa.css.Style;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

/**
 * An HTML element: the HTML implementation of {@link Container}, and the base of every element class ({@link Html}'s:
 * {@code Div}, {@code Input}...). Every element is a component and can hold others, as Swing's {@code JComponent}
 * can; a {@code Div} is a {@code JPanel}. It has HTML's global attributes (from {@link Component}), its own as typed
 * setters and getters (in its class: {@code setHref}, {@code getType}, {@code isRequired}), CSS, listeners for its
 * DOM events, a {@link Layout}, and drag and drop. Plain HTML: what it is, the page has, nothing in between.
 * <pre>{@code
 * Div panel = Html.div().setLayout(new BorderLayout());
 * Button save = Html.button("Save");
 * save.addActionListener(e -> store());
 * panel.add(save, BorderLayout.SOUTH);
 * Document.of(view).body().add(panel);
 * }</pre>
 *
 * @param <Self> the element's own type (a {@code Div}'s is {@code Div})
 */
public abstract class HtmlElement<Self extends HtmlElement<Self>> extends Node implements Container<Self, Node> {

    private static final Pattern NAME = Pattern.compile("[a-zA-Z_:][-a-zA-Z0-9_:.]*");
    private static final Pattern CLASS = Pattern.compile("-?[_a-zA-Z][_a-zA-Z0-9-]*");

    private final String tag;
    private final boolean empty;
    private final Map<String, String> attributes = new LinkedHashMap<>();
    private final Set<String> classes = new LinkedHashSet<>();
    /** The typed classes it has, by name: those that bring CSS put it in its page. */
    private final Map<String, io.joshuasalcedo.swaswa.css.CssClass> typed = new ConcurrentHashMap<>();
    private final Style style = new Style();
    private final List<Node> children = new ArrayList<>();
    private final Map<String, List<Listener>> listeners = new ConcurrentHashMap<>();
    /** What the page holds and tells back (value, checked, open), by name. */
    private final Map<String, String> held = new ConcurrentHashMap<>();
    private final Set<String> holds = new LinkedHashSet<>();
    private final Map<String, Property<String>> heldProperties = new ConcurrentHashMap<>();
    private final Map<ClientKey<?>, Object> clientProperties = new ConcurrentHashMap<>();
    private Property<Boolean> visible;
    private Property<Boolean> enabled;
    private Layout layout;
    private Set<String> layoutKeys = Set.of();
    private final Map<Node, Constraint> constraints = new IdentityHashMap<>();
    private final Map<HtmlElement<?>, Set<String>> placed = new IdentityHashMap<>();
    private boolean dropTarget;

    /** {@code tag}: the element, as HTML writes it ({@code "div"}); {@code empty}: a void element, holding nothing. */
    protected HtmlElement(String tag, boolean empty) {
        this.tag = Objects.requireNonNull(tag, "tag");
        this.empty = empty;
        io.joshuasalcedo.swaswa.internal.Access.looks().listen(style, this::styled);
    }

    /** This, as its own type: for setters that chain. */
    @SuppressWarnings("unchecked")
    protected final Self self() {
        return (Self) this;
    }

    /** Its tag: {@code "div"}. */
    public String getTag() {
        return tag;
    }

    // ---- attributes ----

    @Override
    public Self setAttribute(String name, String value) {
        Objects.requireNonNull(name, "name");
        String n = name.toLowerCase(Locale.ROOT);
        if (!NAME.matcher(name).matches()) throw new IllegalArgumentException("not an attribute's name: " + name);
        if (n.startsWith("on")) throw new IllegalArgumentException(name + ": an event handler, not an attribute: listen with on(\"" + n.substring(2) + "\", ...)");
        if (n.equals("class")) throw new IllegalArgumentException("class: set with cssClass(...)");
        if (n.equals("style")) throw new IllegalArgumentException("style: set with getStyle() or style(...)");
        if (n.equals("hidden")) return setVisible(value == null);
        if (n.equals("disabled")) return setEnabled(value == null);
        if (holds.contains(n)) return setHeld(n, value);
        if ((n.equals("href") || n.equals("src") || n.equals("action") || n.equals("formaction")) && value != null) url(value);
        return attribute(n, value);
    }

    @Override
    public String getAttribute(String name) {
        return attributes.get(name.toLowerCase(Locale.ROOT));
    }

    @Override
    public boolean hasAttribute(String name) {
        return attributes.containsKey(name.toLowerCase(Locale.ROOT));
    }

    @Override
    public Map<String, String> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    /** For the typed setters: the attribute as it is written, null taking it off. */
    protected Self attribute(String name, String value) {
        String old = value == null ? attributes.remove(name) : attributes.put(name, value);
        if (!Objects.equals(old, value)) op("attr", key, name, value);
        return self();
    }

    /** A boolean attribute: there ({@code required}) or not. */
    protected Self flag(String name, boolean on) {
        return attribute(name, on ? "" : null);
    }

    protected static String number(double value) {
        return value == Math.rint(value) && !Double.isInfinite(value) ? Long.toString((long) value) : Double.toString(value);
    }

    protected Double numberOf(String name) {
        String v = attributes.get(name);
        try {
            return v == null ? null : Double.valueOf(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    protected <E extends Enum<E> & HtmlLiteral> E literal(String name, Class<E> words) {
        String v = attributes.get(name);
        if (v == null) return null;
        for (E e : words.getEnumConstants()) if (e.literal().equals(v)) return e;
        return null;
    }

    /** {@code data:} types a page could run script in, if it were followed or loaded: HTML, XHTML, SVG, XML. */
    private static final Pattern SCRIPTED_DATA = Pattern.compile(
            "data:(text/html|application/xhtml\\+xml|image/svg\\+xml|text/xml|application/xml|[^,;]*\\+xml)[,;].*|data:[,;].*");

    /**
     * A URL the page may follow or load: not {@code javascript:} or {@code vbscript:}, and not a {@code data:} document
     * script could run in (HTML, XHTML, SVG, XML; with no type, a browser takes it as text but some as HTML). Plain
     * text, JSON, images and files to download are fine.
     */
    protected static String url(String url) {
        if (url == null) return null;
        String u = url.strip().toLowerCase(Locale.ROOT).replaceAll("[\\s\\u0000-\\u001f]", "");
        if (u.startsWith("javascript:") || u.startsWith("vbscript:") || SCRIPTED_DATA.matcher(u).matches())
            throw new IllegalArgumentException("not a URL a page may follow: " + url);
        return url;
    }

    /**
     * A URL only ever loaded as an image ({@code img src}, {@code input type=image src}, {@code video poster}): as
     * {@link #url}, and an SVG too ({@code data:image/svg+xml,...}): a browser runs no script in an SVG shown as an
     * image. Elsewhere (a link, a frame) an SVG is a document, and refused.
     */
    protected static String imageUrl(String url) {
        if (url == null) return null;
        String u = url.strip().toLowerCase(Locale.ROOT).replaceAll("[\\s\\u0000-\\u001f]", "");
        if (u.startsWith("data:image/svg+xml,") || u.startsWith("data:image/svg+xml;")) return url;
        return url(url);
    }

    // ---- the component's state ----

    @Override public Self setId(String id) { return attribute("id", id); }

    @Override public String getId() { return attributes.get("id"); }

    @Override public Self setToolTipText(String text) { return attribute("title", text); }

    @Override public String getToolTipText() { return attributes.get("title"); }

    @Override
    public Self setVisible(boolean on) {
        if (visible != null) {
            if (visible.isBound()) throw new IllegalStateException("visibleProperty() is bound: it follows another");
            visible.set(on);   // its listener shows it
            return self();
        }
        return flag("hidden", !on);
    }

    @Override public boolean isVisible() { return !attributes.containsKey("hidden"); }

    @Override
    public Self setEnabled(boolean on) {
        if (enabled != null) {
            if (enabled.isBound()) throw new IllegalStateException("enabledProperty() is bound: it follows another");
            enabled.set(on);
            return self();
        }
        return flag("disabled", !on);
    }

    @Override public boolean isEnabled() { return !attributes.containsKey("disabled"); }

    @Override
    public synchronized Property<Boolean> visibleProperty() {
        if (visible == null) {
            visible = new Property<>(isVisible());
            visible.addListener((old, now) -> flag("hidden", !Boolean.TRUE.equals(now)));
        }
        return visible;
    }

    @Override
    public synchronized Property<Boolean> enabledProperty() {
        if (enabled == null) {
            enabled = new Property<>(isEnabled());
            enabled.addListener((old, now) -> flag("disabled", !Boolean.TRUE.equals(now)));
        }
        return enabled;
    }

    @Override public Self setFocusable(boolean focusable) { return attribute("tabindex", focusable ? "0" : "-1"); }

    @Override public Self setDraggable(boolean draggable) { return attribute("draggable", draggable ? "true" : null); }

    @Override public boolean isDraggable() { return "true".equals(attributes.get("draggable")); }

    @Override
    public <T> Self putClientProperty(ClientKey<T> key, T value) {
        Objects.requireNonNull(key, "key");
        if (value == null) clientProperties.remove(key);
        else clientProperties.put(key, key.type().cast(value));
        return self();
    }

    @Override
    public <T> T getClientProperty(ClientKey<T> key) {
        return key.type().cast(clientProperties.get(key));
    }

    // ---- CSS: classes and style ----

    @Override
    public Self cssClass(String... names) {
        boolean changed = false;
        for (String name : names) {
            if (name.isEmpty() || name.chars().anyMatch(c -> Character.isWhitespace(c) || c == '"' || c == '<' || c == '>'))
                throw new IllegalArgumentException("not a CSS class name: " + name);
            changed |= classes.add(name);
        }
        if (changed) op("attr", key, "class", classText());
        return self();
    }

    @Override
    public Self cssClass(io.joshuasalcedo.swaswa.css.CssClass... more) {
        String[] names = new String[more.length];
        for (int i = 0; i < more.length; i++) {
            names[i] = Objects.requireNonNull(more[i], "class").className();
            typed.put(names[i], more[i]);
            Document d = document;
            if (d != null) d.uses(more[i]);
        }
        return cssClass(names);
    }

    @Override
    public Self removeCssClass(String name) {
        typed.remove(name);
        if (classes.remove(name)) op("attr", key, "class", classText());
        return self();
    }

    @Override public boolean hasCssClass(String name) { return classes.contains(name); }

    @Override public Set<String> getCssClasses() { return Collections.unmodifiableSet(classes); }

    /** The typed classes it has ({@link #cssClass(io.joshuasalcedo.swaswa.css.CssClass...)}): their CSS goes with its page. */
    public java.util.Collection<io.joshuasalcedo.swaswa.css.CssClass> getTypedCssClasses() {
        return List.copyOf(typed.values());
    }

    private String classText() {
        return classes.isEmpty() ? null : String.join(" ", classes);
    }

    @Override
    public Style getStyle() {
        return style;
    }

    private void styled() {
        op("attr", key, "style", style.isEmpty() ? null : style.toCss());
    }

    // ---- children ----

    @Override
    public Self add(Node... nodes) {
        for (Node n : nodes) add(n, children.size());
        return self();
    }

    @Override
    public Self add(Node node, Constraint constraint) {
        Objects.requireNonNull(node, "node");
        if (node.parent != null) node.parent.remove(node);
        if (constraint != null) constraints.put(node, constraint);
        return add(node, children.size());
    }

    @Override
    public Self add(Node node, int index) {
        Objects.requireNonNull(node, "node");
        if (empty) throw new UnsupportedOperationException("<" + tag + "> holds nothing");
        for (HtmlElement<?> p = this; p != null; p = p.parent) {
            if (p == node) throw new IllegalArgumentException("an element cannot hold itself or what it is in");
        }
        if (node.parent != null && node.parent != this) {
            node.parent.remove(node);
        } else if (node.parent == this) {
            Constraint kept = constraints.get(node);
            remove(node);
            if (kept != null) constraints.put(node, kept);
            index = Math.min(index, children.size());
        }
        children.add(index, node);
        node.parent = this;
        if (node instanceof HtmlElement<?> e) place(e, index);
        Document d = document;
        if (d != null) {
            Node before = index + 1 < children.size() ? children.get(index + 1) : null;
            if (before == null) d.op("append", key, node.tree(d));
            else d.op("insert", key, node.tree(d), before.key);
        }
        return self();
    }

    @Override
    public Self remove(Node node) {
        if (children.remove(node)) {
            if (node instanceof HtmlElement<?> e) unplace(e);
            constraints.remove(node);
            node.op("remove", node.key);
            node.leave();
            node.parent = null;
        }
        return self();
    }

    @Override
    public Self removeAll() {
        for (Node n : List.copyOf(children)) remove(n);
        return self();
    }

    @Override
    public List<Node> getComponents() {
        return List.copyOf(children);
    }

    @Override
    public int getComponentCount() {
        return children.size();
    }

    /** Its text: what it holds, as one text (null takes all it holds away). */
    public Self setText(String text) {
        if (empty) {
            if (text != null) throw new UnsupportedOperationException("<" + tag + "> holds nothing");
            return self();
        }
        if (children.size() == 1 && children.getFirst() instanceof Text t && text != null) {
            t.setText(text);
            return self();
        }
        removeAll();
        if (text != null) add(new Text(text));
        return self();
    }

    /** The text it holds, its children's too. */
    public String getText() {
        StringBuilder b = new StringBuilder();
        text(this, b);
        return b.toString();
    }

    private static void text(HtmlElement<?> e, StringBuilder b) {
        for (Node n : e.children) {
            if (n instanceof Text t) b.append(t.getText());
            else if (n instanceof HtmlElement<?> c) text(c, b);
        }
    }

    // ---- layout ----

    @Override
    public synchronized Self setLayout(Layout layout) {
        List<HtmlElement<?>> elements = new ArrayList<>();
        for (Node n : children) if (n instanceof HtmlElement<?> e) elements.add(e);
        for (HtmlElement<?> e : elements) unplace(e);
        for (String k : layoutKeys) style.set(k, null);
        this.layout = layout;
        layoutKeys = Set.of();
        if (layout != null) {
            layoutKeys = written(style, () -> layout.layOut(style));
            for (HtmlElement<?> e : elements) place(e, children.indexOf(e));
        }
        return self();
    }

    @Override
    public Layout getLayout() {
        return layout;
    }

    private void place(HtmlElement<?> child, int index) {
        Layout l = layout;
        if (l == null) return;
        placed.put(child, written(child.style, () -> l.place(child.style, constraints.get(child), index)));
    }

    private void unplace(HtmlElement<?> child) {
        Set<String> keys = placed.remove(child);
        if (layout != null) layout.removed(child.style);
        if (keys != null) for (String k : keys) child.style.set(k, null);
    }

    /** The properties {@code writing} sets in {@code style}: to take away when the layout goes. */
    private static Set<String> written(Style style, Runnable writing) {
        Map<String, String> before = new HashMap<>(style.declarations());
        writing.run();
        Set<String> keys = new HashSet<>();
        style.declarations().forEach((k, v) -> {
            if (!Objects.equals(before.get(k), v)) keys.add(k);
        });
        return keys;
    }

    // ---- drag and drop ----

    @Override
    public Self setDropTarget(boolean on) {
        dropTarget = on;
        return attribute("data-swaswa-drop", on ? "" : null);
    }

    @Override
    public boolean isDropTarget() {
        return dropTarget;
    }

    // ---- finding ----

    @Override
    public Optional<Component<?>> lookup(String selector) {
        List<Component<?>> all = new ArrayList<>();
        find(Selector.of(selector), all, true);
        return all.stream().findFirst();
    }

    @Override
    public List<Component<?>> lookupAll(String selector) {
        List<Component<?>> all = new ArrayList<>();
        find(Selector.of(selector), all, false);
        return all;
    }

    private boolean find(Selector s, List<Component<?>> out, boolean first) {
        for (Node n : children) {
            if (!(n instanceof HtmlElement<?> e)) continue;
            if (s.matches(e)) {
                out.add(e);
                if (first) return true;
            }
            if (e.find(s, out, first) && first) return true;
        }
        return false;
    }

    /** {@code #id}, {@code .class}, {@code tag}, {@code tag.class}, {@code tag#id}. */
    private record Selector(String tag, String id, List<String> classes) {
        private static final Pattern WHOLE = Pattern.compile("([a-zA-Z][a-zA-Z0-9-]*)?((?:[#.][-_a-zA-Z0-9]+)*)");
        private static final Pattern PART = Pattern.compile("([#.])([-_a-zA-Z0-9]+)");

        static Selector of(String selector) {
            String s = Objects.requireNonNull(selector, "selector").strip();
            java.util.regex.Matcher m = WHOLE.matcher(s);
            if (s.isEmpty() || !m.matches()) throw new IllegalArgumentException("a selector is #id, .class, a tag, or a tag with those: " + selector);
            String id = null;
            List<String> cls = new ArrayList<>();
            java.util.regex.Matcher part = PART.matcher(m.group(2));
            while (part.find()) {
                if (part.group(1).equals("#")) id = part.group(2);
                else cls.add(part.group(2));
            }
            return new Selector(m.group(1) == null ? null : m.group(1).toLowerCase(Locale.ROOT), id, cls);
        }

        boolean matches(HtmlElement<?> e) {
            return (tag == null || tag.equals(e.getTag())) && (id == null || id.equals(e.getId())) && e.classes.containsAll(classes);
        }
    }

    // ---- events ----

    @Override
    public Self on(String type, Listener listener) {
        Objects.requireNonNull(listener, "listener");
        if (!type.matches("[a-z][a-z0-9]*")) throw new IllegalArgumentException("not an event's name: " + type);
        List<Listener> list = listeners.computeIfAbsent(type, t -> new CopyOnWriteArrayList<>());
        list.add(listener);
        if (list.size() == 1) op("listen", key, type);
        return self();
    }

    @Override
    public Self off(String type, Listener listener) {
        List<Listener> list = listeners.get(type);
        if (list != null && list.remove(listener) && list.isEmpty()) {
            listeners.remove(type);
            op("unlisten", key, type);
        }
        return self();
    }

    @Override
    public Self requestFocus() {
        return call("focus");
    }

    @Override
    public Self scrollIntoView() {
        return call("scrollIntoView");
    }

    /** Calls the element's own method (no arguments) in the page: {@code focus}, {@code showModal}... */
    protected Self call(String method) {
        op("call", key, method);
        return self();
    }

    /**
     * An event from the page: what it holds kept first; a drop on a drop target moves the dragged one in; then its
     * listeners, each on its own.
     */
    void fire(String type, Map<String, Object> data, Node draggedNode) {
        for (String name : holds) {
            if (data.containsKey(name)) {
                Object v = data.get(name);
                String now = v == null || Boolean.FALSE.equals(v) ? null : String.valueOf(v);
                setHeldQuietly(name, now);
                Property<String> p = heldProperties.get(name);
                if (p != null && !p.isBound()) p.set(now);
            }
        }
        HtmlElement<?> dragged = draggedNode instanceof HtmlElement<?> d ? d : null;
        if (type.equals("drop") && dropTarget && dragged != null && !contains(dragged, this)) add(dragged);
        List<Listener> list = listeners.get(type);
        if (list == null) return;
        HtmlEvent event = new HtmlEvent(this, type, data, dragged);
        for (Listener l : list) {
            try {
                l.handle(event);
            } catch (Exception e) {
                System.getLogger("swaswa.html").log(System.Logger.Level.WARNING, "<" + tag + "> " + type + " listener failed", e);
            }
        }
    }

    /** Whether {@code inner} is {@code outer} or in it. */
    private static boolean contains(HtmlElement<?> outer, HtmlElement<?> inner) {
        for (HtmlElement<?> p = inner; p != null; p = p.parent) if (p == outer) return true;
        return false;
    }

    // ---- what the page holds: value, checked, open ----

    /** Listens only so what the page holds is kept here (its events bring it). */
    private static final Listener KEEP = event -> {};

    /**
     * A property the page holds and tells back with its events: kept here as the page has it, always (the events
     * that change it are listened to for that).
     */
    protected void hold(String name) {
        holds.add(name);
        switch (name) {
            case "value" -> {
                on("input", KEEP);
                on("change", KEEP);
            }
            case "checked" -> on("change", KEEP);
            case "open" -> on("toggle", KEEP);
            default -> { }
        }
    }

    protected String held(String name) {
        return held.get(name);
    }

    protected Self setHeld(String name, String value) {
        setHeldQuietly(name, value);
        op("prop", key, name, value);
        Property<String> p = heldProperties.get(name);
        if (p != null && !p.isBound()) p.set(value);
        return self();
    }

    protected void setHeldQuietly(String name, String value) {
        if (value == null) held.remove(name);
        else held.put(name, value);
    }

    /** What the page holds, as a property: set it and the page shows it; the person changes it and it is told. */
    protected Property<String> heldProperty(String name) {
        return heldProperties.computeIfAbsent(name, n -> {
            Property<String> p = new Property<>(held(n));
            p.addListener((old, now) -> {
                if (!Objects.equals(held(n), now)) setHeld(n, now);
            });
            return p;
        });
    }

    // ---- as HTML, as the page builds it ----

    @Override
    void write(StringBuilder out) {
        out.append('<').append(tag);
        if (!classes.isEmpty()) out.append(" class=\"").append(escape(classText())).append('"');
        if (!style.isEmpty()) out.append(" style=\"").append(escape(style.toCss())).append('"');
        attributes.forEach((k, v) -> {
            out.append(' ').append(k);
            if (!v.isEmpty()) out.append("=\"").append(escape(v)).append('"');
        });
        // what it holds, as HTML writes it at first (a select's choice and a text area's text are its children's)
        if (held.containsKey("value") && tag.equals("input")) out.append(" value=\"").append(escape(held.get("value"))).append('"');
        if (held.containsKey("checked")) out.append(" checked");
        if (held.containsKey("open")) out.append(" open");
        out.append('>');
        if (empty) return;
        if (tag.equals("textarea") && held.containsKey("value")) out.append(escape(held.get("value")));
        else for (Node n : children) n.write(out);
        out.append("</").append(tag).append('>');
    }

    @Override
    Map<String, Object> tree(Document document) {
        this.document = document;
        key = document.number(this);
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("key", key);
        t.put("tag", tag);
        for (io.joshuasalcedo.swaswa.css.CssClass c : typed.values()) document.uses(c);   // their CSS, in its page
        Map<String, String> attrs = new LinkedHashMap<>(attributes);
        if (!classes.isEmpty()) attrs.put("class", classText());
        if (!style.isEmpty()) attrs.put("style", style.toCss());
        t.put("attrs", attrs);
        if (!listeners.isEmpty()) t.put("events", List.copyOf(listeners.keySet()));
        if (!held.isEmpty()) t.put("props", Map.copyOf(held));
        List<Object> kids = new ArrayList<>();
        for (Node n : children) kids.add(n.tree(document));
        t.put("children", kids);
        return t;
    }

    @Override
    void leave() {
        for (Node n : children) n.leave();
        if (document != null) document.forget(key);
        document = null;
        key = 0;
    }

    @Override
    public String toString() {
        return "<" + tag + (getId() == null ? "" : " id=" + getId()) + ">";
    }
}
