package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** A link: to an address, or running Java. */
public final class Link extends Component<Link> {

    public Link(String text, String href) {
        super("link");
        prop("text", text);
        prop("href", href);
    }

    /** Runs Java instead of opening the address. */
    public Link onClick(Runnable action) { return on("onClick", v -> action.run()); }
}
