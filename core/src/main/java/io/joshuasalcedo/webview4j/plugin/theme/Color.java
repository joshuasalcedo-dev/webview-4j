package io.joshuasalcedo.webview4j.plugin.theme;

/**
 * A colour of a design spec: red, green and blue (0xRRGGBB), and how opaque (1 is solid).
 * <pre>{@code
 * Color.rgb(0x0F6CBD)          // Windows' blue
 * Color.rgba(0x000000, 0.08)   // a faint shadow-black wash
 * }</pre>
 */
public record Color(int rgb, double alpha) {

    public Color {
        if (rgb < 0 || rgb > 0xFFFFFF) throw new IllegalArgumentException("a colour is 0xRRGGBB: " + Integer.toHexString(rgb));
        if (!(alpha >= 0 && alpha <= 1)) throw new IllegalArgumentException("alpha is 0 to 1: " + alpha);
    }

    /** A solid colour. */
    public static Color rgb(int rgb) {
        return new Color(rgb, 1);
    }

    /** A colour you see through, {@code alpha} from 0 (not at all) to 1 (solid). */
    public static Color rgba(int rgb, double alpha) {
        return new Color(rgb, alpha);
    }

    /** As CSS: {@code #0f6cbd}, or {@code rgba(0,0,0,0.08)}. */
    public String css() {
        if (alpha == 1) return "#%06x".formatted(rgb);
        String a = alpha == 0 ? "0" : Double.toString(Math.round(alpha * 1000) / 1000.0);
        if (a.startsWith("0.")) a = a.substring(1);
        return "rgba(%d,%d,%d,%s)".formatted(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, a.equals("1.0") ? "1" : a);
    }
}
