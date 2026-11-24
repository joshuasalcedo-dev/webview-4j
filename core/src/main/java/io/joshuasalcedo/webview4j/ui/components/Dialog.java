package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** A dialog over the window, made of components: a title, a message, what is inside, and its buttons. */
public final class Dialog extends Component<Dialog> {

    public Dialog(String title) {
        super("dialog");
        prop("title", title);
    }

    public Dialog message(String message) { return prop("message", message); }
    public Dialog open(boolean open) { return prop("open", open); }
    /** Its look: "info", "question", "warning" or "error". */
    public Dialog kind(String kind) { return prop("kind", kind); }
    public Dialog with(Component<?>... children) { return add(children); }
    /** The main action: the accent button, and what Enter does. */
    public Dialog primary(String label, Runnable action) { return action(label, action, "primary"); }
    /** What Escape does (Cancel, Close). */
    public Dialog cancel(String label, Runnable action) { return action(label, action, "cancel"); }
    /** It deletes or loses something. */
    public Dialog destructive(String label, Runnable action) { return action(label, action, "destructive"); }
    public Dialog action(String label, Runnable action) { return action(label, action, null); }

    private Dialog action(String label, Runnable action, String role) {
        Element a = new Element("dialogAction").set("label", label).handle("onSelect", v -> action.run());
        if (role != null) a.set(role, true);
        return add(slot(a, "action"));
    }
}
