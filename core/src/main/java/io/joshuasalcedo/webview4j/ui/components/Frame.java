package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** The whole window: an optional title bar, toolbar and status bar around the body. {@link #platform} and
 * {@link #scheme} set the look; by default the system's. */
public final class Frame extends Component<Frame> {

    public Frame(Component<?> body) {
        super("frame");
        add(slot(body, "body"));
    }

    public Frame platform(Platform platform) { return prop("platform", platform.key()); }
    public Frame scheme(ColorScheme scheme) { return prop("theme", scheme.key()); }
    /** Another accent colour than the system's blue, as CSS ("#7c3aed"). */
    public Frame accent(String color) { return prop("accent", color); }
    public Frame titleBar(TitleBar titleBar) { return place("titleBar", titleBar); }
    public Frame toolbar(Toolbar toolbar) { return place("toolbar", toolbar); }
    public Frame statusBar(StatusBar statusBar) { return place("statusBar", statusBar); }

    /**
     * The app's side bar, as tall as the window under its title bar: the toolbar and the status bar sit beside it,
     * over and under the body only (as Windows 11's apps, VS Code and macOS's do). Usually a {@link Sidebar}.
     */
    public Frame sidebar(Component<?> sidebar) { return place("sidebar", sidebar); }

    private Frame place(String name, Component<?> component) {
        children().removeIf(c -> name.equals(slotOf(c)));
        if (component != null) add(slot(component, name));
        return this;
    }
}
