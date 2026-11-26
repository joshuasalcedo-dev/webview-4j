package io.joshuasalcedo.webview4j.plugin.theme;

import io.joshuasalcedo.webview4j.Theme;
import io.joshuasalcedo.webview4j.View;
import io.joshuasalcedo.webview4j.ui.Style;
import io.joshuasalcedo.webview4j.ui.UiWindow;
import io.joshuasalcedo.webview4j.ui.components.Platform;
import io.joshuasalcedo.webview4j.ui.state.Tracking;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

/**
 * How the app looks now, for every window: its {@link DesignSpec} and light, dark or as the system is
 * ({@link Theme}). Changing either draws every window again at once, and the windows' own frames follow light or
 * dark. A {@link io.joshuasalcedo.webview4j.WebviewApplication} remembers the choice between runs.
 * <pre>{@code
 * Appearance.current().use(DesignSpec.MACOS).mode(Theme.DARK);
 * Appearance.current().register(brand);           // offered by the ThemeSwitcher
 * }</pre>
 * Until a spec is chosen, each system gets its own: Windows 11 on Windows, macOS on a Mac (the platform's look).
 */
public final class Appearance {

    private static final Appearance CURRENT = new Appearance();

    /** Specs added in code ({@link #register}), after the provider's. */
    private final List<DesignSpec> specs = new CopyOnWriteArrayList<>();
    private final List<Consumer<Appearance>> listeners = new CopyOnWriteArrayList<>();
    private final Tracking.Watched watched = new Tracking.Watched();
    private volatile DesignSpec spec;
    private volatile Theme mode = Theme.SYSTEM;
    private volatile Preferences remembered;

    private Appearance() {
        // the app gave (or took back) its DesignSpecProvider: its list, and its first spec, now
        io.joshuasalcedo.webview4j.plugin.Providers.addChangeListener(type -> {
            if (type == DesignSpecProvider.class) {
                restore();
                changed();
            }
        });
    }

    /** The app's provider of specs, else webview-4j's own (Windows 11 and macOS). */
    private static DesignSpecProvider provider() {
        return io.joshuasalcedo.webview4j.plugin.Providers.get(DesignSpecProvider.class, DesignSpecProvider.BUILT_IN);
    }

    /** The app's appearance (one per process). */
    public static Appearance current() {
        return CURRENT;
    }

    /**
     * The design spec in use: the one chosen, else the provider's {@link DesignSpecProvider#initialSpec()}, else the
     * system's own when it is offered ({@link DesignSpec#WINDOWS} or {@link DesignSpec#MACOS}), else the first offered.
     */
    public DesignSpec spec() {
        DesignSpec chosen = spec;
        if (chosen != null) return chosen;
        DesignSpec initial = provider().initialSpec();
        if (initial != null) return initial;
        List<DesignSpec> offered = specs();
        DesignSpec own = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("mac") ? DesignSpec.MACOS : DesignSpec.WINDOWS;
        return offered.contains(own) || offered.isEmpty() ? own : offered.getFirst();
    }

    /** Light, dark, or as the system is. */
    public Theme mode() {
        return mode;
    }

    /** The specs a {@link ThemeSwitcher} offers: the {@link DesignSpecProvider}'s (webview-4j's own two without one), then those registered in code. */
    public List<DesignSpec> specs() {
        List<DesignSpec> all = new java.util.ArrayList<>();
        for (DesignSpec s : provider().designSpecs()) if (s != null && all.stream().noneMatch(a -> a.name().equals(s.name()))) all.add(s);
        for (DesignSpec s : specs) {
            all.removeIf(a -> a.name().equals(s.name()));
            all.add(s);
        }
        return List.copyOf(all);
    }

    /** Offers {@code more} in the theme switcher (a spec of the same name takes the old one's place). */
    public Appearance register(DesignSpec... more) {
        for (DesignSpec s : more) {
            Objects.requireNonNull(s, "spec");
            specs.removeIf(old -> old.name().equals(s.name()));
            specs.add(s);
        }
        // a spec remembered from the last run, registered only now
        restore();
        changed();
        return this;
    }

    /** Every window takes {@code spec} now. */
    public Appearance use(DesignSpec spec) {
        Objects.requireNonNull(spec, "spec");
        if (specs().stream().noneMatch(s -> s.name().equals(spec.name()))) specs.add(spec);
        this.spec = spec;
        save();
        changed();
        return this;
    }

    /** Every window is light, dark, or as the system is, now: its page and its frame. */
    public Appearance mode(Theme mode) {
        this.mode = Objects.requireNonNull(mode, "mode");
        save();
        for (UiWindow window : UiWindow.all()) {
            View view = window.view();
            if (view != null) view.setTheme(mode);
        }
        changed();
        return this;
    }

    /** Runs after each change of spec or mode. */
    public Appearance addChangeListener(Consumer<Appearance> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return this;
    }

    public Appearance removeChangeListener(Consumer<Appearance> listener) {
        listeners.remove(listener);
        return this;
    }

    /**
     * Remembers the choice between runs, under {@code app} (the application's class name): the choice saved last
     * time is used again now. {@link io.joshuasalcedo.webview4j.WebviewApplication} does this by itself.
     */
    public Appearance remember(String app) {
        // -Dwebview4j.appearance.remember=false: nothing read or saved (tests, kiosks)
        if ("false".equalsIgnoreCase(System.getProperty("webview4j.appearance.remember"))) return this;
        remembered = Preferences.userRoot().node("webview4j/appearance").node(Objects.requireNonNull(app, "app").replace('/', '.'));
        restore();
        return this;
    }

    /** Back to how it starts (tests: the appearance is the whole process's). */
    void reset() {
        specs.clear();
        listeners.clear();
        spec = null;
        mode = Theme.SYSTEM;
        remembered = null;
        watched.changed();
    }

    private void restore() {
        Preferences p = remembered;
        if (p == null) return;
        String savedMode = p.get("mode", null);
        if (savedMode != null) {
            try {
                mode = Theme.valueOf(savedMode);
            } catch (IllegalArgumentException ignored) {
                // a mode from another version: the default
            }
        }
        String savedSpec = p.get("spec", null);
        if (savedSpec != null && spec == null) {
            for (DesignSpec s : specs()) if (s.name().equals(savedSpec)) spec = s;
        }
    }

    private void save() {
        Preferences p = remembered;
        if (p == null) return;
        p.put("mode", mode.name());
        if (spec != null) p.put("spec", spec.name());
    }

    private void changed() {
        watched.changed();
        for (Consumer<Appearance> l : listeners) l.accept(this);
    }

    /**
     * For the renderer: puts the appearance on a window's root frame (its platform, light or dark, and the spec's
     * colours for both), unless the frame set its own platform or scheme. Read while the window draws, so a change
     * draws it again.
     */
    public void decorate(Map<String, Object> frame) {
        watched.read();
        DesignSpec s = spec();
        frame.putIfAbsent("platform", s.platform() == Platform.MAC ? "mac" : "windows");
        frame.putIfAbsent("theme", mode.name().toLowerCase(java.util.Locale.ROOT));
        Map<String, Object> tokens = new LinkedHashMap<>();
        tokens.put("light", css(s.tokens(Theme.LIGHT)));
        tokens.put("dark", css(s.tokens(Theme.DARK)));
        frame.put("tokens", tokens);
    }

    private static Map<String, String> css(Map<Style.Token, Color> colours) {
        Map<String, String> out = new LinkedHashMap<>();
        List<Style.Token> order = new ArrayList<>(colours.keySet());
        order.sort(null);
        for (Style.Token t : order) out.put(t.variable(), colours.get(t).css());
        return out;
    }
}
