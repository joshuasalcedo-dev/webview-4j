package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;
import java.util.function.Consumer;

/** A search in a {@link Sidebar}'s header: what is typed reaches Java as it is typed. */
public final class SidebarInput extends Component<SidebarInput> {

    public SidebarInput(String placeholder) {
        super("sidebarInput");
        prop("placeholder", placeholder);
    }

    public SidebarInput value(String value) { return prop("value", value); }

    public SidebarInput onChange(Consumer<String> action) { return on("onChange", v -> action.accept(Keys.text(v))); }
}
