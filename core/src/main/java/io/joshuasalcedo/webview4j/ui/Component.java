package io.joshuasalcedo.webview4j.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A part of a screen built in Java: one of {@code @webview4j/ui}'s components, with its properties, its children
 * and the Java code that runs when the user acts on it. Made with {@link Ui}'s methods; a {@link Screen} returns
 * a tree of them from {@code render()}, and the window shows it with the real components.
 *
 * @param <C> the component's own type, so its settings chain: {@code button("Save").accent().onClick(...)}
 */
public abstract class Component<C extends Component<C>> {

    private final String type;
    final Map<String, Object> props = new LinkedHashMap<>();
    final List<Component<?>> children = new ArrayList<>();
    final Map<String, Consumer<Object>> handlers = new LinkedHashMap<>();
    private String key;
    /** The windows that drew it: changing it draws them again (as Swing and JavaFX controls do). */
    private final io.joshuasalcedo.webview4j.ui.state.Tracking.Watched watched = new io.joshuasalcedo.webview4j.ui.state.Tracking.Watched();

    protected Component(String type) {
        this.type = Objects.requireNonNull(type, "type");
    }

    @SuppressWarnings("unchecked")
    protected C self() {
        return (C) this;
    }

    /**
     * What tells this component apart from its siblings, when a list of them changes (an id): so each keeps what
     * the user typed in it, its scroll and its focus. Without one, its place among its siblings.
     */
    public C key(Object key) {
        this.key = String.valueOf(Objects.requireNonNull(key, "key"));
        return self();
    }

    /** Takes the room left in its row or column. */
    public C grow() {
        return prop("grow", true);
    }

    /** A width in CSS pixels. */
    public C width(double width) {
        return prop("width", width);
    }

    /** A height in CSS pixels. */
    public C height(double height) {
        return prop("height", height);
    }

    /** The narrowest it gets, CSS pixels (JavaFX's minWidth). */
    public C minWidth(double width) {
        return prop("minWidth", width);
    }

    /** The widest it gets, CSS pixels (JavaFX's maxWidth). */
    public C maxWidth(double width) {
        return prop("maxWidth", width);
    }

    /** The shortest it gets, CSS pixels. */
    public C minHeight(double height) {
        return prop("minHeight", height);
    }

    /** The tallest it gets, CSS pixels. */
    public C maxHeight(double height) {
        return prop("maxHeight", height);
    }

    // --- the shape of HTML: what every element has, on the component's outermost element in the page ---------

    private static final java.util.regex.Pattern NAME = java.util.regex.Pattern.compile("[a-z][a-z0-9]*(-[a-z0-9]+)*");
    private static final java.util.regex.Pattern CLASS_NAME = java.util.regex.Pattern.compile("-?[_a-zA-Z][_a-zA-Z0-9-]*");
    /**
     * All of HTML's global attributes, as MDN lists them (Web/HTML/Reference/Global_attributes): each has a typed
     * method below too. {@code class} and {@code style} are {@link #className} and {@link #style}; {@code data-*}
     * {@link #dataAttribute}; the event attributes ({@code onclick}...) are Java handlers, never text.
     */
    private static final java.util.Set<String> GLOBAL = java.util.Set.of(
            "accesskey", "anchor", "autocapitalize", "autocorrect", "autofocus", "containertiming", "containertimingignore",
            "contenteditable", "dir", "draggable", "enterkeyhint", "exportparts", "headingoffset", "headingreset", "hidden",
            "id", "inert", "inputmode", "is", "itemid", "itemprop", "itemref", "itemscope", "itemtype", "lang", "nonce",
            "part", "popover", "role", "slot", "spellcheck", "tabindex", "title", "translate", "virtualkeyboardpolicy",
            "writingsuggestions");

    /** Sets a global attribute ({@code value}), or takes it off ({@code null}); a boolean one is "" when on. */
    private C global(String name, String value) {
        if (value == null) htmlMap("attrs").remove(name);
        else htmlMap("attrs").put(name, value);
        return self();
    }

    private C flag(String name, boolean on) {
        return global(name, on ? "" : null);
    }

    /** Space-separated words, each checked: ids, part names, class-like names. */
    private static String words(String what, String... words) {
        StringBuilder out = new StringBuilder();
        for (String w : words) {
            if (w == null || !CLASS_NAME.matcher(w).matches()) throw new IllegalArgumentException("not " + what + ": " + w);
            if (!out.isEmpty()) out.append(' ');
            out.append(w);
        }
        return out.toString();
    }

    // --- every global attribute, typed (MDN's list) ---

    /** {@code accesskey}: the keys that focus or activate it (Alt+key on Windows), first that the system has. */
    public C accessKey(String... keys) {
        for (String k : keys) if (k == null || k.codePointCount(0, k.length()) != 1) throw new IllegalArgumentException("accessKey takes single characters: " + k);
        return global("accesskey", String.join(" ", keys));
    }
    /** {@code anchor}: the id of the element it is placed beside (CSS anchor positioning). */
    public C anchor(String id) { return global("anchor", words("an id", id)); }
    /** {@code autocapitalize}: which letters an on-screen keyboard capitalises. */
    public C autoCapitalize(Attr.AutoCapitalize mode) { return global("autocapitalize", mode.word); }
    /** {@code autocorrect}: whether the system corrects spelling as it is typed. */
    public C autoCorrect(boolean on) { return global("autocorrect", on ? "on" : "off"); }
    /** {@code autofocus}: it takes the keyboard's focus when it appears. */
    public C autofocus() { return flag("autofocus", true); }
    /** {@code containertiming}: names it for the page's Container Timing measurements. */
    public C containerTiming(String name) { return global("containertiming", Objects.requireNonNull(name, "name")); }
    /** {@code containertimingignore}: Container Timing leaves it out. */
    public C containerTimingIgnore(boolean on) { return flag("containertimingignore", on); }
    /** {@code contenteditable}: the person can edit its text (Java gets what they type through its own events). */
    public C contentEditable(Attr.ContentEditable mode) { return global("contenteditable", mode.word); }
    /** {@code dir}: which way its text runs. */
    public C dir(Attr.Dir dir) { return global("dir", dir.word); }
    /** {@code draggable}: whether it can be dragged. */
    public C draggable(boolean on) { return global("draggable", Boolean.toString(on)); }
    /** {@code enterkeyhint}: what the on-screen keyboard's Enter key says. */
    public C enterKeyHint(Attr.EnterKeyHint hint) { return global("enterkeyhint", hint.word); }
    /** {@code exportparts}: the parts of its shadow tree it passes on ({@code "label, icon: badge-icon"}). */
    public C exportParts(String parts) {
        if (parts == null || !parts.matches("[A-Za-z0-9_-]+(\\s*:\\s*[A-Za-z0-9_-]+)?(\\s*,\\s*[A-Za-z0-9_-]+(\\s*:\\s*[A-Za-z0-9_-]+)?)*"))
            throw new IllegalArgumentException("exportparts: name or name:alias, comma-separated: " + parts);
        return global("exportparts", parts);
    }
    /** {@code headingoffset}: how many levels its headings go down (0 to 8): an h1 inside counts as an h(1 + offset). */
    public C htmlHeadingOffset(int offset) {
        if (offset < 0 || offset > 8) throw new IllegalArgumentException("headingoffset is 0 to 8: " + offset);
        return global("headingoffset", Integer.toString(offset));
    }
    /** {@code headingreset}: headings inside start again from level 1, whatever is around it. */
    public C headingReset(boolean on) { return flag("headingreset", on); }
    /** {@code hidden="until-found"}: hidden, but the page's find (Ctrl+F) and links to it show it. */
    public C hiddenUntilFound() { return global("hidden", "until-found"); }
    /** {@code inert}: it and all inside it cannot be focused, clicked or read by screen readers. */
    public C inert(boolean on) { return flag("inert", on); }
    /** {@code inputmode}: which on-screen keyboard to show. */
    public C inputMode(Attr.InputMode mode) { return global("inputmode", mode.word); }
    /** {@code is}: the customized built-in element it is (a registered custom element's name, with a dash). */
    public C isElement(String customElement) {
        if (customElement == null || !customElement.matches("[a-z][a-z0-9]*(-[a-z0-9]+)+")) throw new IllegalArgumentException("a custom element's name has a dash: " + customElement);
        return global("is", customElement);
    }
    /** {@code itemid}: the global identifier of the item it is (microdata), e.g. a URN or URL. */
    public C itemId(String id) { return global("itemid", Objects.requireNonNull(id, "id")); }
    /** {@code itemprop}: the microdata properties it gives its item ({@code "name"}, {@code "price"}). */
    public C itemProp(String... names) { return global("itemprop", words("a property name", names)); }
    /** {@code itemref}: the ids of elements elsewhere whose properties belong to its item. */
    public C itemRef(String... ids) { return global("itemref", words("an id", ids)); }
    /** {@code itemscope}: it is a microdata item; {@link #itemType} says of which vocabulary. */
    public C itemScope(boolean on) { return flag("itemscope", on); }
    /** {@code itemtype}: the vocabulary of its item ({@code "https://schema.org/Product"}); it is an item too. */
    public C itemType(String... urls) {
        for (String u : urls) if (u == null || !u.matches("https?://\\S+")) throw new IllegalArgumentException("itemtype is a URL: " + u);
        itemScope(true);
        return global("itemtype", String.join(" ", urls));
    }
    /** {@code lang}: the language of its text, a BCP 47 tag ({@code "en"}, {@code "fil-PH"}). */
    public C lang(String tag) {
        if (tag == null || !tag.matches("[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*|")) throw new IllegalArgumentException("not a BCP 47 language tag: " + tag);
        return global("lang", tag);
    }
    /** {@code nonce}: the number used once that a Content Security Policy lets through. */
    public C nonce(String nonce) {
        if (nonce == null || !nonce.matches("[A-Za-z0-9+/_=-]+")) throw new IllegalArgumentException("a nonce is base64: " + nonce);
        return global("nonce", nonce);
    }
    /** {@code part}: its part names, for {@code ::part()} CSS from outside its shadow tree. */
    public C part(String... names) { return global("part", words("a part name", names)); }
    /** {@code popover}: it is a popover, hidden until shown (a {@code popovertarget} button, or the page's own code). */
    public C popover(Attr.Popover mode) { return global("popover", mode.word); }
    /** {@code role}: what it is to screen readers, an ARIA role ({@code "region"}, {@code "status"}, {@code "alert"}). */
    public C role(String role) {
        if (role == null || !role.matches("[a-z]+( [a-z]+)*")) throw new IllegalArgumentException("not an ARIA role: " + role);
        return global("role", role);
    }
    /** {@code slot}: the named slot of a shadow tree it goes into. */
    public C slotName(String name) { return global("slot", words("a slot name", name)); }
    /** {@code spellcheck}: whether its text is checked for spelling. */
    public C spellCheck(boolean on) { return global("spellcheck", Boolean.toString(on)); }
    /** {@code title}: advice the system shows as a tooltip when the pointer rests on it. */
    public C htmlTitle(String title) { return global("title", Objects.requireNonNull(title, "title")); }
    /** {@code translate}: whether a page translator translates its text. */
    public C translate(boolean on) { return global("translate", on ? "yes" : "no"); }
    /** {@code virtualkeyboardpolicy}: the on-screen keyboard shows on focus (AUTO), or when the app asks (MANUAL). */
    public C virtualKeyboardPolicy(Attr.VirtualKeyboardPolicy policy) { return global("virtualkeyboardpolicy", policy.word); }
    /** {@code writingsuggestions}: whether the system suggests how to go on as it is typed. */
    public C writingSuggestions(boolean on) { return global("writingsuggestions", Boolean.toString(on)); }

    /**
     * What the HTML settings are kept in, apart from the component's own props (a chart's {@code title} is its
     * title; HTML's title attribute is {@code attr("title", ...)}).
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> html() {
        return (Map<String, Object>) props.computeIfAbsent("html", k -> new LinkedHashMap<String, Object>());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> htmlMap(String name) {
        return (Map<String, Object>) html().computeIfAbsent(name, k -> new LinkedHashMap<String, Object>());
    }

    /** Its id in the page: for the app's CSS ({@code #totals}), a label's {@code for}, a link to it. */
    public C id(String id) {
        if (id == null || !CLASS_NAME.matcher(id).matches()) throw new IllegalArgumentException("not an id: " + id);
        html().put("id", id);
        return self();
    }

    /** Classes for the app's own CSS ({@link Ui#styleSheet}), added to the component's own. */
    public C className(String... names) {
        @SuppressWarnings("unchecked")
        List<String> classes = (List<String>) html().computeIfAbsent("className", k -> new ArrayList<String>());
        for (String name : names) {
            if (name == null || !CLASS_NAME.matcher(name).matches()) throw new IllegalArgumentException("not a class name: " + name);
            if (!classes.contains(name)) classes.add(name);
        }
        return self();
    }

    /**
     * Any of HTML's 36 global attributes by its name, as text ({@code attr("title", "Saves it")}); each also has a
     * typed method ({@link #htmlTitle}, {@link #dir}, {@link #popover}...). {@code class} and {@code style} are
     * {@link #className} and {@link #style}; events are Java's ({@code onClick}), never attribute text.
     */
    public C attr(String name, String value) {
        String n = Objects.requireNonNull(name, "name").toLowerCase(java.util.Locale.ROOT);
        if (!GLOBAL.contains(n)) throw new IllegalArgumentException("not one of HTML's global attributes: " + name + " (class: className(), style: style(), data-*: dataAttribute(), aria-*: ariaAttribute())");
        return global(n, Objects.requireNonNull(value, "value"));
    }

    /** A {@code data-*} attribute: {@code dataAttribute("row", "42")} is {@code data-row="42"}, for the app's CSS or tests. */
    public C dataAttribute(String name, String value) {
        if (name == null || !NAME.matcher(name).matches()) throw new IllegalArgumentException("not a data-* name (lowercase, dashes): " + name);
        htmlMap("attrs").put("data-" + name, Objects.requireNonNull(value, "value"));
        return self();
    }

    /** An {@code aria-*} attribute: {@code ariaAttribute("label", "Close")}, {@code ariaAttribute("live", "polite")}. */
    public C ariaAttribute(String name, String value) {
        if (name == null || !NAME.matcher(name).matches()) throw new IllegalArgumentException("not an aria-* name: " + name);
        htmlMap("attrs").put("aria-" + name, Objects.requireNonNull(value, "value"));
        return self();
    }

    /** Not shown, and not read by screen readers (HTML's {@code hidden}). */
    public C hidden(boolean hidden) {
        if (hidden) htmlMap("attrs").put("hidden", "");
        else htmlMap("attrs").remove("hidden");
        return self();
    }

    /** Its place in the Tab order: 0 in the page's order, -1 out of it (focusable from code only). */
    public C tabIndex(int index) {
        htmlMap("attrs").put("tabindex", Integer.toString(index));
        return self();
    }

    /** How it looks beyond its own settings: spacing, colours from the kit's tokens, border, corners, type. */
    public C style(Style style) {
        htmlMap("style").putAll(Objects.requireNonNull(style, "style").declarations());
        return self();
    }

    /** One CSS property: {@code css("letter-spacing", "0.02em")}; checked as {@link Style#set} checks it. */
    public C css(String property, String value) {
        return style(Style.of().set(property, value));
    }

    // --- styles while something is so: drawn by the page at once, no round trip to Java ---

    private C stateStyle(String state, Style style) {
        @SuppressWarnings("unchecked")
        Map<String, Object> states = (Map<String, Object>) html().computeIfAbsent("states", k -> new LinkedHashMap<String, Object>());
        @SuppressWarnings("unchecked")
        Map<String, String> declarations = (Map<String, String>) states.computeIfAbsent(state, k -> new LinkedHashMap<String, String>());
        declarations.putAll(Objects.requireNonNull(style, "style").declarations());
        return self();
    }

    /**
     * How it looks while the pointer is on it ({@code button("Save").hoverStyle(Style.of().elevation(2))}): the
     * page changes it at once, with nothing sent to Java; over its own hover look and its {@link #style}.
     */
    public C hoverStyle(Style style) { return stateStyle("hover", style); }

    /** How it looks while it, or something inside it (a field's input), has the keyboard's focus. */
    public C focusStyle(Style style) { return stateStyle("focus", style); }

    /** How it looks while it is pressed (the pointer down on it). */
    public C pressedStyle(Style style) { return stateStyle("pressed", style); }

    /**
     * The pointer came onto it ({@code true}) or left it ({@code false}), for Java to act on: a preview, a status
     * line. For a look alone, {@link #hoverStyle} is instant and sends nothing.
     */
    public C onHover(Consumer<Boolean> hovering) {
        return on("onHover", v -> hovering.accept(Boolean.TRUE.equals(v)));
    }

    /** Sets a property the page's renderer reads; null removes it. */
    protected C prop(String name, Object value) {
        synchronized (this) {
            if (value == null) props.remove(name);
            else props.put(name, value);
        }
        watched.changed();
        return self();
    }

    /** Java code for an event of the component ({@code onClick}): it gets what the page sent, as a JSON value. */
    protected C on(String event, Consumer<Object> handler) {
        handlers.put(event, io.joshuasalcedo.webview4j.ui.state.Context.bind(Objects.requireNonNull(handler, "handler")));
        return self();
    }

    protected C add(Component<?>... components) {
        return add(java.util.Arrays.asList(components));
    }

    protected C add(List<? extends Component<?>> components) {
        synchronized (this) {
            for (Component<?> c : components) if (c != null) children.add(c);
        }
        watched.changed();
        return self();
    }

    /** Takes {@code child} out (the same object, not an equal one); a window showing it draws again. */
    protected C remove(Component<?> child) {
        boolean removed;
        synchronized (this) {
            removed = children.removeIf(c -> c == child);
        }
        if (removed) watched.changed();
        return self();
    }

    /** Takes every child out; a window showing it draws again. */
    protected C clear() {
        synchronized (this) {
            children.clear();
        }
        watched.changed();
        return self();
    }

    /** A property set before, or null. */
    protected Object get(String name) {
        return props.get(name);
    }

    /** The children, in order. */
    protected List<Component<?>> children() {
        return children;
    }

    /** Marks {@code component} as filling a named place of its parent (a frame's title bar, a list item's leading part). */
    protected static <T extends Component<?>> T slot(T component, String name) {
        component.props.put("slot", name);
        return component;
    }

    /** The named place {@code component} fills, or null. */
    protected static String slotOf(Component<?> component) {
        return component.props.get("slot") instanceof String s ? s : null;
    }

    /**
     * The tree as the page's renderer reads it: {@code {"t": type, "p": props, "c": children, "k": key}}, each
     * handler written as an id that {@code handlers} maps to the Java code.
     * <p>
     * An id names where its component is (each step its key, or its place among its siblings, and its type) and the
     * event: {@code 0/2.button:onClick}. An action the page sends from a tree a moment older than Java's (a click
     * during a redraw) then reaches the same component if it is still there, and nothing if it is gone, never a
     * neighbour that took its place.
     */
    Map<String, Object> toTree(Map<String, Consumer<Object>> registry) {
        return toTree(registry, "");
    }

    private Map<String, Object> toTree(Map<String, Consumer<Object>> registry, String path) {
        watched.read();
        Map<String, Object> props;
        List<Component<?>> children;
        synchronized (this) {
            props = new LinkedHashMap<>(this.props);
            children = List.copyOf(this.children);
        }
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("t", type);
        Map<String, Object> p = new LinkedHashMap<>(props);
        for (Map.Entry<String, Consumer<Object>> handler : handlers.entrySet()) {
            String id = path + ":" + handler.getKey();
            for (int n = 2; registry.containsKey(id); n++) id = path + ":" + handler.getKey() + "#" + n;
            registry.put(id, handler.getValue());
            p.put(handler.getKey(), id);
        }
        node.put("p", p);
        if (!children.isEmpty()) {
            List<Object> c = new ArrayList<>(children.size());
            for (int i = 0; i < children.size(); i++) {
                Component<?> child = children.get(i);
                String step = (child.key != null ? "k" + child.key : String.valueOf(i)) + "." + child.type;
                c.add(child.toTree(registry, path.isEmpty() ? step : path + "/" + step));
            }
            node.put("c", c);
        }
        if (key != null) node.put("k", key);
        return node;
    }
}
