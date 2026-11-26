package io.joshuasalcedo.webview4j.ui.icons;

import io.joshuasalcedo.webview4j.ui.Component;
import io.joshuasalcedo.webview4j.ui.Style;
import java.util.Objects;

/**
 * An icon on its own, in a screen: one of Lucide's ({@link Icon}), in the text's colour unless given one of the
 * kit's.
 * <pre>{@code
 * new IconView(Icon.SHOPPING_CART)
 * new IconView(Icon.STAR).size(20).color(Style.Token.ACCENT).label("Favourite")
 * }</pre>
 */
public final class IconView extends Component<IconView> {

    public IconView(Icon icon) {
        super("icon");
        setIcon(icon);
    }

    /** Shows {@code icon} now; a window showing it draws again. */
    public IconView setIcon(Icon icon) {
        return prop("name", Objects.requireNonNull(icon, "icon").key());
    }

    /** Its size, CSS pixels (16 at first, the text's). */
    public IconView size(double pixels) {
        if (!(pixels > 0 && pixels <= 512)) throw new IllegalArgumentException("an icon is 1 to 512 pixels: " + pixels);
        return prop("size", pixels);
    }

    /** One of the kit's colours (it follows light and dark); without one, the text's. */
    public IconView color(Style.Token token) {
        return prop("color", token == null ? null : token.css());
    }

    /** What it means, for screen readers; without one it is decoration, and they skip it. */
    public IconView label(String label) {
        return prop("label", label);
    }
}
