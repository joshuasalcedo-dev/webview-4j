package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** A bar of commands under the title bar; its title is the page's name, shown as the system shows one. */
public final class Toolbar extends Component<Toolbar> {

    public Toolbar(String title) {
        super("toolbar");
        prop("title", title);
    }

    public Toolbar with(Component<?>... children) { return add(children); }
}
