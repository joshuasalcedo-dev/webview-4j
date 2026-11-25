package io.joshuasalcedo.webview4j.ui;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * An HTML element in a Java screen ({@link Html#div}, {@link Html#img}...): drawn as itself, beside the kit's
 * components and holding them. Being a {@link Component}, it has all of HTML's global attributes, a style and
 * classes; {@link Html}'s classes add each element's own attributes, typed. Events are Java's: {@link #onClick},
 * {@link #onInput}, a form's {@link #onSubmit}...
 *
 * @param <C> the element's own class, so its settings chain
 */
public abstract class HtmlElement<C extends HtmlElement<C>> extends Component<C> {

    protected HtmlElement(String tag) {
        super("html");
        prop("tag", Objects.requireNonNull(tag, "tag"));
    }

    /** The element's own attributes, by React's name for each ({@code htmlFor}, {@code readOnly}). */
    @SuppressWarnings("unchecked")
    private Map<String, Object> attributes() {
        return (Map<String, Object>) props.computeIfAbsent("a", k -> new LinkedHashMap<String, Object>());
    }

    /** Sets one of the element's own attributes; null takes it off. Used by {@link Html}'s generated setters. */
    protected C attribute(String name, Object value) {
        if (value == null) attributes().remove(name);
        else attributes().put(name, value);
        return self();
    }

    /**
     * A URL attribute's value, checked: http(s), mailto, tel, the app's own files and paths, a fragment, data: images,
     * blob:. Never {@code javascript:}, {@code vbscript:} or a data: document.
     */
    protected static String url(String value) {
        Objects.requireNonNull(value, "url");
        String v = value.strip().replaceAll("[\\s\\u0000-\\u001f]", "").toLowerCase(Locale.ROOT);
        boolean scheme = v.matches("^[a-z][a-z0-9+.-]*:.*");
        boolean safe = !scheme || v.startsWith("http:") || v.startsWith("https:") || v.startsWith("mailto:") || v.startsWith("tel:")
                || v.startsWith("blob:") || v.matches("^data:(image|audio|video)/[a-z0-9.+-]+(;[a-z0-9=.+-]+)*[;,].*");
        if (!safe) throw new IllegalArgumentException("not a URL a page may use here: " + value);
        return value;
    }

    /**
     * As the browser draws it, not with the kit's look: plain HTML fields, buttons, checkboxes, links and tables take
     * the kit's look by default (its fields, buttons, the system's accent; light and dark).
     */
    public C browserLook() {
        return prop("browser", true);
    }

    // --- what it holds ---

    /** Holds these: HTML elements, the kit's components, text ({@link Html#text}). */
    public C with(Component<?>... children) {
        return add(children);
    }

    /** Adds children at the end; a window showing it draws them. */
    @Override public C add(Component<?>... children) { return super.add(children); }
    /** Takes {@code child} out; a window showing it draws again. */
    @Override public C remove(Component<?> child) { return super.remove(child); }
    /** Takes every child out. */
    @Override public C clear() { return super.clear(); }

    /** Holds only this text now (what it held goes). */
    public C setText(String text) {
        clear();
        return add(Html.text(text));
    }

    /** Holds this text (after what it holds already). */
    public C text(String text) {
        return add(Html.text(text));
    }

    // --- events: Java code, never attribute text ---

    public C onClick(Runnable action) { return on("onClick", v -> action.run()); }
    public C onDoubleClick(Runnable action) { return on("onDoubleClick", v -> action.run()); }
    public C onPointerEnter(Runnable action) { return on("onPointerEnter", v -> action.run()); }
    public C onPointerLeave(Runnable action) { return on("onPointerLeave", v -> action.run()); }
    public C onFocus(Runnable action) { return on("onFocus", v -> action.run()); }
    public C onBlur(Runnable action) { return on("onBlur", v -> action.run()); }
    /** A key pressed while it has the focus: the key's name ({@code "Enter"}, {@code "a"}, {@code "ArrowDown"}). */
    public C onKeyDown(Consumer<String> action) { return on("onKeyDown", v -> action.accept(String.valueOf(v))); }
    /** What is typed or chosen, as it changes (an input, a textarea, a select, a contenteditable): its value. */
    public C onInput(Consumer<String> action) { return on("onInput", v -> action.accept(v == null ? "" : String.valueOf(v))); }
    /** Its value once committed (a select chosen, a checkbox ticked: "true" or "false", a field left). */
    public C onChange(Consumer<String> action) { return on("onChange", v -> action.accept(v == null ? "" : String.valueOf(v))); }
    /**
     * A form sent (Enter, a submit button): its fields by name, the page staying where it is. A field's several
     * values (checkboxes of one name) come as a list.
     */
    @SuppressWarnings("unchecked")
    public C onSubmit(Consumer<Map<String, Object>> action) {
        return on("onSubmit", v -> action.accept(v instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of()));
    }
    /** A details opened or closed, a popover shown or hidden: whether it is open now. */
    public C onToggle(Consumer<Boolean> action) { return on("onToggle", v -> action.accept(Boolean.TRUE.equals(v))); }
}
