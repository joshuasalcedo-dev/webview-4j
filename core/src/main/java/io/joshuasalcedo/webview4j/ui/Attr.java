package io.joshuasalcedo.webview4j.ui;

/**
 * The values HTML's global attributes take where the HTML standard lists them, for {@link Component}'s typed
 * methods ({@code dir(Attr.Dir.RTL)}, {@code popover(Attr.Popover.MANUAL)}). Each constant is the attribute's
 * own word.
 *
 * @see <a href="https://developer.mozilla.org/en-US/docs/Web/HTML/Reference/Global_attributes">MDN: global attributes</a>
 */
public final class Attr {

    private Attr() {}

    /** {@code dir}: which way its text runs. */
    public enum Dir {
        LTR("ltr"), RTL("rtl"), AUTO("auto");
        final String word;
        Dir(String word) { this.word = word; }
    }

    /** {@code contenteditable}: whether the person can edit its text. */
    public enum ContentEditable {
        TRUE("true"), FALSE("false"), PLAINTEXT_ONLY("plaintext-only");
        final String word;
        ContentEditable(String word) { this.word = word; }
    }

    /**
     * {@code popover}: it is a popover, hidden until a button with {@code popovertarget} (or the light dismiss)
     * shows it. AUTO closes others and on a click outside; MANUAL only when told; HINT, a hint over auto ones.
     */
    public enum Popover {
        AUTO("auto"), MANUAL("manual"), HINT("hint");
        final String word;
        Popover(String word) { this.word = word; }
    }

    /** {@code inputmode}: which on-screen keyboard to show. */
    public enum InputMode {
        NONE("none"), TEXT("text"), DECIMAL("decimal"), NUMERIC("numeric"), TEL("tel"), SEARCH("search"), EMAIL("email"), URL("url");
        final String word;
        InputMode(String word) { this.word = word; }
    }

    /** {@code enterkeyhint}: what the on-screen keyboard's Enter key says. */
    public enum EnterKeyHint {
        ENTER("enter"), DONE("done"), GO("go"), NEXT("next"), PREVIOUS("previous"), SEARCH("search"), SEND("send");
        final String word;
        EnterKeyHint(String word) { this.word = word; }
    }

    /** {@code autocapitalize}: which letters an on-screen keyboard capitalises. */
    public enum AutoCapitalize {
        NONE("none"), SENTENCES("sentences"), WORDS("words"), CHARACTERS("characters");
        final String word;
        AutoCapitalize(String word) { this.word = word; }
    }

    /** {@code virtualkeyboardpolicy}: the on-screen keyboard shows on focus (AUTO), or when the app asks (MANUAL). */
    public enum VirtualKeyboardPolicy {
        AUTO("auto"), MANUAL("manual");
        final String word;
        VirtualKeyboardPolicy(String word) { this.word = word; }
    }
}
