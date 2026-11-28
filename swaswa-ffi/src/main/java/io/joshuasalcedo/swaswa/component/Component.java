package io.joshuasalcedo.swaswa.component;

import io.joshuasalcedo.swaswa.css.CssProperty;
import io.joshuasalcedo.swaswa.css.Style;
import java.util.Map;
import java.util.Set;

/**
 * Anything on the screen: the base contract of every component, whatever shows it (a page's HTML now; others later).
 * Swing's names ({@code setVisible}, {@code setToolTipText}, {@code addActionListener}, {@code putClientProperty}),
 * JavaFX's properties ({@link #visibleProperty()}) and style classes, HTML's drag and drop.
 *
 * <p>Two kinds: a {@link Container} holds components and lays them out; a control (a button, a field) is a component
 * that holds none of its own. Every setter returns the component, so they chain:
 * {@code Html.button("Save").setToolTipText("Ctrl+S").setEnabled(false)}.
 *
 * @param <Self> the component's own type
 */
public interface Component<Self extends Component<Self>> {

    /** This, as its own type: for default methods that chain. */
    @SuppressWarnings("unchecked")
    private Self self() {
        return (Self) this;
    }

    // ---- where it is ----

    /** The container it is in; null if none. */
    Container<?, ?> getParent();

    /** Whether it is on the screen now (in a shown page or window). */
    boolean isShowing();

    /** Its id: unique in its page ({@code lookup("#id")}, CSS's {@code #id}). */
    Self setId(String id);

    String getId();

    // ---- state ----

    Self setVisible(boolean visible);

    boolean isVisible();

    /** Usable or not: a disabled one takes no input and looks it. */
    Self setEnabled(boolean enabled);

    boolean isEnabled();

    /** Whether the keyboard's focus can come to it (Tab). */
    Self setFocusable(boolean focusable);

    /** The keyboard's focus to it. */
    Self requestFocus();

    /** Its tool tip, shown while the pointer rests on it; null for none. */
    Self setToolTipText(String text);

    String getToolTipText();

    /** Whether a person can drag it: off unless set. Let go on a {@link Container#setDropTarget drop target}, it goes in. */
    Self setDraggable(boolean draggable);

    boolean isDraggable();

    // ---- JavaFX's properties: observable, bindable ----

    /** Its visibility, to listen to or bind: {@code help.visibleProperty().bind(showHelp)}. */
    Property<Boolean> visibleProperty();

    Property<Boolean> enabledProperty();

    // ---- the look: CSS ----

    /** Its own style ({@code style="..."}): what changes in it shows at once. */
    Style getStyle();

    /** Adds style classes (JavaFX's {@code getStyleClass()}, HTML's {@code class}), for a style sheet's rules. */
    Self cssClass(String... names);

    Self removeCssClass(String name);

    /**
     * Adds typed classes ({@link io.joshuasalcedo.swaswa.css.CssClass}): one that brings its own CSS (Tailwind's) puts
     * it in the page it is shown in, once.
     */
    Self cssClass(io.joshuasalcedo.swaswa.css.CssClass... classes);

    default Self removeCssClass(io.joshuasalcedo.swaswa.css.CssClass cssClass) {
        return removeCssClass(cssClass.className());
    }

    default boolean hasCssClass(io.joshuasalcedo.swaswa.css.CssClass cssClass) {
        return hasCssClass(cssClass.className());
    }

    boolean hasCssClass(String name);

    Set<String> getCssClasses();

    default Self style(CssProperty property, String value) {
        getStyle().set(property, value);
        return self();
    }

    default Self style(String property, String value) {
        getStyle().set(property, value);
        return self();
    }

    /** Swing's {@code setBackground}: a CSS colour ({@code "#fff"}, {@code Style.lightDark(...)}); null takes it off. */
    default Self setBackground(String color) { return style(CssProperty.BACKGROUND, color); }

    default Self setForeground(String color) { return style(CssProperty.COLOR, color); }

    /** A CSS font: {@code "600 14px system-ui"}. */
    default Self setFont(String font) { return style(CssProperty.FONT, font); }

    /** A CSS border: {@code "1px solid #ccc"}. */
    default Self setBorder(String border) { return style(CssProperty.BORDER, border); }

    /** JavaFX's {@code setPadding}: space inside its edge, as CSS writes it ({@code "8px 12px"}). */
    default Self setPadding(String padding) { return style(CssProperty.PADDING, padding); }

    /** Space outside its edge. */
    default Self setMargin(String margin) { return style(CssProperty.MARGIN, margin); }

    /** The pointer's look over it: {@code "pointer"}, {@code "grab"}, {@code "text"}. */
    default Self setCursor(String cursor) { return style(CssProperty.CURSOR, cursor); }

    /** 0 (unseen) to 1 (solid). */
    default Self setOpacity(double opacity) { return style(CssProperty.OPACITY, Double.toString(Math.max(0, Math.min(1, opacity)))); }

    /** The size it would have: CSS lengths ({@code "240px"}, {@code "50%"}, {@code "auto"}); null leaves one as it is. */
    default Self setPreferredSize(String width, String height) {
        if (width != null) style(CssProperty.WIDTH, width);
        if (height != null) style(CssProperty.HEIGHT, height);
        return self();
    }

    /** In CSS pixels. */
    default Self setPreferredSize(double width, double height) { return setPreferredSize(width + "px", height + "px"); }

    default Self setMinimumSize(String width, String height) {
        if (width != null) style(CssProperty.MIN_WIDTH, width);
        if (height != null) style(CssProperty.MIN_HEIGHT, height);
        return self();
    }

    default Self setMaximumSize(String width, String height) {
        if (width != null) style(CssProperty.MAX_WIDTH, width);
        if (height != null) style(CssProperty.MAX_HEIGHT, height);
        return self();
    }

    // ---- events ----

    /** Listens to one of its events by the DOM's name ({@code click}, {@code dblclick}, {@code input}, {@code keydown}...). */
    Self on(String type, Listener listener);

    Self off(String type, Listener listener);

    /** It was clicked, or pressed from the keyboard (a button, a link): Swing's {@code ActionListener}. */
    default Self addActionListener(Listener listener) { return on("click", listener); }

    default Self removeActionListener(Listener listener) { return off("click", listener); }

    /** What it holds changed as the user typed or picked: each change. */
    default Self addChangeListener(Listener listener) { return on("input", listener); }

    default Self removeChangeListener(Listener listener) { return off("input", listener); }

    /** A key went down while it had the focus ({@link Event#key()}). */
    default Self addKeyListener(Listener listener) { return on("keydown", listener); }

    default Self removeKeyListener(Listener listener) { return off("keydown", listener); }

    /** The focus came ({@code focus}) or went ({@code blur}): {@link Event#type()} says which. */
    default Self addFocusListener(Listener listener) {
        on("focus", listener);
        return on("blur", listener);
    }

    default Self removeFocusListener(Listener listener) {
        off("focus", listener);
        return off("blur", listener);
    }

    /** The pointer: a click ({@code click}, {@code dblclick}), coming onto it ({@code pointerenter}), leaving it ({@code pointerleave}). */
    default Self addMouseListener(Listener listener) {
        for (String type : new String[] {"click", "dblclick", "pointerenter", "pointerleave"}) on(type, listener);
        return self();
    }

    default Self removeMouseListener(Listener listener) {
        for (String type : new String[] {"click", "dblclick", "pointerenter", "pointerleave"}) off(type, listener);
        return self();
    }

    // ---- HTML's attributes: every component has HTML's global ones ----

    /**
     * Sets an attribute by HTML's name; null takes it off. Event handlers ({@code onclick}) are not attributes: listen
     * with {@link #on}. Its class and style are set with {@link #cssClass} and {@link #getStyle()}.
     */
    Self setAttribute(String name, String value);

    String getAttribute(String name);

    boolean hasAttribute(String name);

    /** Its attributes by HTML's names (not its class and style). */
    Map<String, String> getAttributes();

    default Self removeAttribute(String name) {
        return setAttribute(name, null);
    }

    /** A boolean attribute: there ({@code inert}) or not. */
    private Self flagAttribute(String name, boolean on) {
        return setAttribute(name, on ? "" : null);
    }

    /** HTML's {@code title}: its tool tip ({@link #setToolTipText}). */
    default Self setTitle(String title) { return setToolTipText(title); }

    default String getTitle() { return getToolTipText(); }

    /** HTML's {@code hidden}: the opposite of {@link #setVisible}. */
    default Self setHidden(boolean hidden) { return setVisible(!hidden); }

    default boolean isHidden() { return !isVisible(); }

    /** Its language: {@code "en"}, {@code "fil"}, {@code "ja"}. */
    default Self setLang(String lang) { return setAttribute("lang", lang); }

    default String getLang() { return getAttribute("lang"); }

    /** Its text's direction. */
    enum Dir { LTR, RTL, AUTO }

    default Self setDir(Dir dir) { return setAttribute("dir", dir == null ? null : dir.name().toLowerCase(java.util.Locale.ROOT)); }

    /** Its place in the Tab order: 0 in the page's order, -1 out of it (focusable by code only), more first. */
    default Self setTabIndex(int index) { return setAttribute("tabindex", Integer.toString(index)); }

    default Integer getTabIndex() {
        String t = getAttribute("tabindex");
        try {
            return t == null ? null : Integer.valueOf(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** A key that, with the system's modifier (Alt), focuses or presses it. */
    default Self setAccessKey(String key) { return setAttribute("accesskey", key); }

    /** Whether the person can edit its text in place. */
    default Self setContentEditable(boolean editable) { return setAttribute("contenteditable", editable ? "true" : null); }

    default boolean isContentEditable() { return "true".equals(getAttribute("contenteditable")) || "".equals(getAttribute("contenteditable")); }

    /** Whether its text is checked for spelling (where it can be edited). */
    default Self setSpellcheck(boolean check) { return setAttribute("spellcheck", Boolean.toString(check)); }

    /** Whether a translator (the browser's) may translate its text. */
    default Self setTranslate(boolean translate) { return setAttribute("translate", translate ? "yes" : "no"); }

    /** Inert: it and all in it take no input, no focus, and are left out for assistive technology. */
    default Self setInert(boolean inert) { return flagAttribute("inert", inert); }

    default boolean isInert() { return hasAttribute("inert"); }

    /** Focused when its page shows it. */
    default Self setAutofocus(boolean autofocus) { return flagAttribute("autofocus", autofocus); }

    /** What it is, for assistive technology: ARIA's {@code role} ({@code "dialog"}, {@code "tab"}, {@code "status"}). */
    default Self setRole(String role) { return setAttribute("role", role); }

    default String getRole() { return getAttribute("role"); }

    /** An ARIA attribute: {@code setAria("label", "Close")} is {@code aria-label="Close"}. */
    default Self setAria(String name, String value) { return setAttribute("aria-" + name, value); }

    default String getAria(String name) { return getAttribute("aria-" + name); }

    /** A data attribute: {@code setData("row", "4")} is {@code data-row="4"}, for CSS and the page's own scripts. */
    default Self setData(String name, String value) { return setAttribute("data-" + name, value); }

    default String getData(String name) { return getAttribute("data-" + name); }

    /** The keyboard a touch screen shows for it. */
    enum InputMode { NONE, TEXT, DECIMAL, NUMERIC, TEL, SEARCH, EMAIL, URL }

    default Self setInputMode(InputMode mode) { return setAttribute("inputmode", mode == null ? null : mode.name().toLowerCase(java.util.Locale.ROOT)); }

    /** What a touch keyboard's Enter key says. */
    enum EnterKeyHint { ENTER, DONE, GO, NEXT, PREVIOUS, SEARCH, SEND }

    default Self setEnterKeyHint(EnterKeyHint hint) { return setAttribute("enterkeyhint", hint == null ? null : hint.name().toLowerCase(java.util.Locale.ROOT)); }

    /** Whether typed text starts with capitals (touch keyboards). */
    enum Autocapitalize { OFF, NONE, ON, SENTENCES, WORDS, CHARACTERS }

    default Self setAutocapitalize(Autocapitalize how) { return setAttribute("autocapitalize", how == null ? null : how.name().toLowerCase(java.util.Locale.ROOT)); }

    /** A pop-over (HTML's {@code popover}): shown over everything by a button that targets it, closed by a click away ({@code AUTO}). */
    enum Popover { AUTO, MANUAL, HINT }

    default Self setPopover(Popover popover) { return setAttribute("popover", popover == null ? null : popover.name().toLowerCase(java.util.Locale.ROOT)); }

    /** A name a style sheet outside its shadow tree styles it by ({@code ::part(name)}). */
    default Self setPart(String part) { return setAttribute("part", part); }

    /** Scrolls its page so it is in view. */
    Self scrollIntoView();

    /** Listens to one of its events, typed: {@code on(EventType.DBLCLICK, ...)}. */
    default Self on(EventType type, Listener listener) { return on(type.dom(), listener); }

    default Self off(EventType type, Listener listener) { return off(type.dom(), listener); }

    // ---- the app's own data on it: Swing's client properties, JavaFX's properties map ----

    /** Keeps the app's own data on it under {@code key} (null takes it off): never shown, never sent anywhere. */
    <T> Self putClientProperty(ClientKey<T> key, T value);

    /** What the app keeps on it under {@code key}; null if nothing. */
    <T> T getClientProperty(ClientKey<T> key);
}
