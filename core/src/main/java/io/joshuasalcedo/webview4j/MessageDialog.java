package io.joshuasalcedo.webview4j;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * A native message box: a title, a text, an icon for its {@link Level} and a set of
 * {@link Buttons}. Show it with {@link App#message}; {@link App#confirm} is the common case.
 * <pre>{@code
 * MessageDialog.Answer answer = app.message(MessageDialog.of("Save changes?", "Your edits will be lost otherwise.")
 *         .level(MessageDialog.Level.WARNING).buttons(MessageDialog.Buttons.YES_NO_CANCEL).parent(view)).join();
 * }</pre>
 *
 * <p>Closing the box without choosing answers {@link Answer#CANCEL}, or {@link Answer#NO} when
 * there is no cancel button. On Linux the box comes from {@code zenity} or the desktop portal.
 */
public final class MessageDialog {

    public enum Level { INFO, WARNING, ERROR }

    public enum Buttons { OK, OK_CANCEL, YES_NO, YES_NO_CANCEL }

    public enum Answer { OK, CANCEL, YES, NO }

    private final String title;
    private final String text;
    private Level level = Level.INFO;
    private Buttons buttons = Buttons.OK;
    private View parent;

    private MessageDialog(String title, String text) {
        this.title = Objects.requireNonNull(title, "title");
        this.text = Objects.requireNonNull(text, "text");
    }

    public static MessageDialog of(String title, String text) {
        return new MessageDialog(title, text);
    }

    public MessageDialog level(Level level) {
        this.level = Objects.requireNonNull(level, "level");
        return this;
    }

    public MessageDialog buttons(Buttons buttons) {
        this.buttons = Objects.requireNonNull(buttons, "buttons");
        return this;
    }

    /** The window it belongs to: it opens over it, modal to it. */
    public MessageDialog parent(View view) {
        this.parent = Objects.requireNonNull(view, "view");
        return this;
    }

    /** The payload of {@code app.dialog.message} (PROTOCOL.md §5.7). */
    Map<String, Object> toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", title);
        payload.put("text", text);
        payload.put("level", level.name().toLowerCase(Locale.ROOT));
        payload.put("buttons", buttons.name().toLowerCase(Locale.ROOT).replace('_', '-'));
        if (parent != null) payload.put("view", parent.id());
        return payload;
    }

    static Answer answer(String answer) {
        return Answer.valueOf(Objects.requireNonNull(answer, "answer").toUpperCase(Locale.ROOT));
    }
}
