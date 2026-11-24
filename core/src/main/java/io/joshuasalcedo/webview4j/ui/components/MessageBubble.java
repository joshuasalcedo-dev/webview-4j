package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** A chat message: one's own on the right, others' on the left, with the time and how far it got. */
public final class MessageBubble extends Component<MessageBubble> {

    public MessageBubble(String text, boolean mine) {
        super("messageBubble");
        prop("text", text);
        prop("mine", mine);
    }

    /** Who sent it, shown above others' messages. */
    public MessageBubble sender(String name) { return prop("sender", name); }
    public MessageBubble sentAt(java.time.Instant at) { return prop("sentAt", at.toString()); }
    /** "sending", "sent", "delivered", "read" or "failed": one's own messages. */
    public MessageBubble state(String state) { return prop("state", state); }
}
