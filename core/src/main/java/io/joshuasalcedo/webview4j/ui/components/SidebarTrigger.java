package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/**
 * The button that folds and opens a {@link Sidebar}, for a toolbar or the page's top: {@code sidebarTrigger(() ->
 * folded = !folded)}, the sidebar given {@code .collapsed(folded)}.
 */
public final class SidebarTrigger extends Component<SidebarTrigger> {

    public SidebarTrigger(Runnable action) {
        super("sidebarTrigger");
        on("onClick", v -> action.run());
    }
}
