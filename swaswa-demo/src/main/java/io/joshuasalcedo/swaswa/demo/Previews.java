package io.joshuasalcedo.swaswa.demo;

import io.joshuasalcedo.swaswa.html.Div;
import io.joshuasalcedo.swaswa.html.Img;
import io.joshuasalcedo.swaswa.html.Input;
import io.joshuasalcedo.swaswa.html.Li;
import io.joshuasalcedo.swaswa.html.P;
import io.joshuasalcedo.swaswa.html.Span;
import io.joshuasalcedo.swaswa.html.Ul;
import io.joshuasalcedo.swaswa.css.CssClass;
import io.joshuasalcedo.swaswa.tailwind.Tw;
import io.joshuasalcedo.swaswa.tailwind.Utility;
import java.util.Set;

/**
 * A showcase of a Tailwind class, as Tailwind's docs show one: an example where what the class does can be seen. A
 * colour as a swatch, a layout class on a container of three boxes, a position inside a frame, overflow on long
 * text... Made here, from the class itself.
 */
final class Previews {

    private Previews() {}

    static final String LONG_TEXT = "The quick brown fox jumps over the lazy dog, again and again, far past the edge of its box.";

    /** On a container of three boxes: they show how it lays out its children. */
    static final Set<String> CONTAINERS = Set.of("flex", "grid", "gap", "justify", "items", "content", "place", "space", "divide");

    /** On a child, among three: it shows how the child sits among the others. */
    static final Set<String> CHILDREN = Set.of("grow", "shrink", "basis", "order", "self", "col", "row");

    /** A colour only (border-red-500, ring-white...): it needs a width or a shadow to colour. */
    static boolean colourOnly(String name) {
        if (name.matches("[a-z-]+-(inherit|current|transparent|black|white)(/\\d+)?")) return true;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[a-z-]+-([a-z]+)-\\d+(/\\d+)?").matcher(name);
        return m.matches() && COLOURS.contains(m.group(1));
    }

    /** Tailwind's colours by name (red, slate...): those it has a bg-*-500 of. */
    static final Set<String> COLOURS = io.joshuasalcedo.swaswa.tailwind.Tailwind.names().stream()
            .filter(n -> n.matches("bg-[a-z]+-500")).map(n -> n.substring(3, n.length() - 4))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    /** The display classes of the block and inline families (the rest of them are sizes: block-4, inline-full). */
    static final Set<String> DISPLAYS = Set.of("block", "inline", "inline-block", "inline-flex", "inline-grid", "inline-table");

    /** A showcase of the class {@code name}. */
    static Div of(String name) {
        Utility utility = Utility.of(name);
        String family = TailwindDocs.family(name);
        // classes their family's showcase does not fit
        if (name.matches("flex-(\\d.*|auto|initial|none)")) return child("grow", utility);   // flex-1: a child's share
        if ((family.equals("block") || family.equals("inline")) && !DISPLAYS.contains(name)) {
            return family.equals("block") ? column(utility, null) : bar(utility, null);    // block-size, inline-size
        }
        if (name.matches("(from|via|to)-\\d+%")) return gradientStop(utility);
        if (name.startsWith("line-clamp")) return clamped(utility);
        if (CONTAINERS.contains(family)) return container(family, utility);
        if (CHILDREN.contains(family)) return child(family, utility);
        return switch (family) {
            case "bg", "from", "via", "to" -> swatch(utility);
            case "text", "font", "tracking", "italic", "uppercase", "lowercase", "capitalize", "line", "indent", "align"
                    -> text(utility);
            case "underline", "decoration" -> underlined(utility);
            case "leading", "columns" -> clamped(utility);
            case "truncate", "whitespace", "break", "hyphens", "overflow", "text-ellipsis" -> longText(utility);
            case "border", "outline", "ring" -> outlined(utility);
            case "rounded" -> rounded(utility);
            case "shadow" -> card(utility);
            case "opacity", "blur", "brightness", "contrast", "grayscale", "hue", "invert", "saturate", "sepia", "drop", "backdrop"
                    -> filtered(utility);
            case "p", "px", "py", "pt", "pr", "pb", "pl", "ps", "pe" -> padded(utility);
            case "m", "mx", "my", "mt", "mr", "mb", "ml", "ms", "me" -> margined(utility);
            case "w" -> bar(utility, null);
            case "min", "max" -> name.matches("-?(min|max)-(h|block)-.*")
                    ? column(utility, family.equals("min") ? Tw.H.H_1 : Tw.H.H_FULL)
                    : bar(utility, family.equals("min") ? Tw.W.W_1 : Tw.W.W_FULL);
            case "h", "size" -> column(utility, null);
            case "box" -> boxSizing(utility);
            case "block", "inline", "hidden", "visible", "invisible", "contents" -> middleOfThree(utility);
            case "static", "fixed", "absolute", "relative", "sticky" -> positioned(utility);
            case "inset", "top", "right", "bottom", "left", "start", "end" -> placed(utility);
            case "z" -> stacked(utility);
            case "float", "clear" -> floated(utility);
            case "aspect" -> aspect(utility);
            case "object" -> picture(utility);
            case "list" -> list(utility);
            case "animate" -> animated(utility);
            case "transition", "duration", "ease", "delay" -> transitioned(utility);
            case "cursor", "pointer", "select", "touch", "resize" -> hoverMe(utility);
            case "rotate", "scale", "skew", "translate" -> transformed(utility);
            case "origin" -> pivoted(utility);
            case "accent" -> checkbox(utility);
            case "caret" -> field(utility);
            default -> sample(utility);
        };
    }

    // ---- colours ----

    /** A colour: a square of it. A gradient's shape: with two colours to show it. */
    private static Div swatch(Utility utility) {
        Div swatch = new Div();
        swatch.cssClass(Tw.Size.SIZE_10, Tw.Rounded.ROUNDED_MD, utility);
        String name = utility.className();
        boolean shape = name.contains("linear") || name.contains("conic") || name.contains("radial") || name.contains("gradient");
        if (shape) swatch.cssClass(Tw.From.FROM_PINK_500, Tw.To.TO_INDIGO_500);
        // a stop: in a gradient, the other end white (only the end it is not, so the two do not compete)
        if (name.startsWith("from-")) swatch.cssClass(Tw.Bg.BG_LINEAR_TO_R, Tw.To.TO_WHITE);
        if (name.startsWith("to-")) swatch.cssClass(Tw.Bg.BG_LINEAR_TO_R, Tw.From.FROM_WHITE);
        if (name.startsWith("via-")) swatch.cssClass(Tw.Bg.BG_LINEAR_TO_R, Tw.From.FROM_WHITE, Tw.To.TO_WHITE);
        return swatch;
    }

    // ---- text ----

    /** A font, a size, a colour or a decoration: some text in it. */
    private static Div text(Utility utility) {
        Div text = new Div("The quick brown fox");
        text.cssClass(utility);
        return text;
    }

    /** A decoration: on underlined text (a colour, a style or an offset needs the line to show on). */
    private static Div underlined(Utility utility) {
        Div text = new Div("The quick brown fox");
        text.cssClass(Tw.Underline.UNDERLINE);
        if (colourOnly(utility.className()) || utility.className().matches("decoration-(solid|double|dotted|dashed|wavy)")) {
            text.cssClass(Tw.Decoration.DECORATION_2);
        }
        text.cssClass(utility);
        return text;
    }

    /** Long text in a narrow box, as tall as it needs: line-clamp, leading and columns show on it. */
    private static Div clamped(Utility utility) {
        Div text = new Div(LONG_TEXT);
        text.cssClass(Tw.W.W_48, Tw.Text.TEXT_XS, utility);
        return text;
    }

    /** What happens to text too long for its box: a small box of long text. */
    private static Div longText(Utility utility) {
        Div box = new Div(LONG_TEXT);
        box.cssClass(Tw.W.W_48, Tw.H.H_12, Tw.Text.TEXT_XS, Tw.P.P_1, Tw.Bg.BG_SLATE_100, Tw.Rounded.ROUNDED_SM, utility);
        return box;
    }

    private static Div list(Utility utility) {
        Ul list = new Ul(new Li("One"), new Li("Two"), new Li("Three"));
        list.cssClass(Tw.Pl.PL_5, Tw.Text.TEXT_SM, utility);
        return new Div(list);
    }

    // ---- boxes ----

    /**
     * A border, an outline or a ring. A colour gets a width to colour; a width or a style gets a colour (never both
     * colours: the later one in Tailwind's order would win).
     */
    private static Div outlined(Utility utility) {
        String name = utility.className();
        String family = TailwindDocs.family(name);
        Div box = new Div();
        box.cssClass(Tw.Size.SIZE_10, Tw.Rounded.ROUNDED_MD, Tw.Bg.BG_WHITE);
        if (colourOnly(name)) {
            switch (family) {
                case "border" -> box.cssClass(Tw.Border.BORDER_4);
                case "outline" -> box.cssClass(Tw.Outline.OUTLINE_4);
                default -> box.cssClass(Tw.Ring.RING_4);
            }
        } else {
            switch (family) {
                case "border" -> box.cssClass(Tw.Border.BORDER_SLATE_400);
                case "outline" -> box.cssClass(Tw.Outline.OUTLINE_INDIGO_500);
                default -> box.cssClass(Tw.Ring.RING_INDIGO_500);
            }
            if (name.matches("border-(solid|dashed|dotted|double|hidden|none)")) box.cssClass(Tw.Border.BORDER_2);
            if (name.matches("-?outline-(offset-.*|solid|dashed|dotted|double|hidden|none)")) box.cssClass(Tw.Outline.OUTLINE_2);
            if (name.matches("ring-offset-.*|ring-inset")) box.cssClass(Tw.Ring.RING_2);
        }
        box.cssClass(utility);
        return box;
    }

    private static Div rounded(Utility utility) {
        Div box = new Div();
        box.cssClass(Tw.Size.SIZE_12, Tw.Bg.BG_INDIGO_500, utility);
        return box;
    }

    private static Div card(Utility utility) {
        Div card = new Div();
        card.cssClass(Tw.Size.SIZE_12, Tw.Rounded.ROUNDED_LG, Tw.Bg.BG_WHITE, utility);
        boolean colourOnly = utility.className().matches("shadow-(inherit|current|transparent|black|white|[a-z]+-\\d+)");
        if (colourOnly) card.cssClass(Tw.Shadow.SHADOW_LG);   // a colour needs a shadow to colour
        return card;
    }

    private static Div filtered(Utility utility) {
        Div box = box("Aa");
        box.cssClass(Tw.Bg.BG_LINEAR_TO_BR, Tw.From.FROM_PINK_500, Tw.To.TO_INDIGO_500, utility);
        if (utility.className().startsWith("drop-shadow-") && colourOnly(utility.className())) box.cssClass(Tw.Drop.DROP_SHADOW_LG);
        return box;
    }

    /** Padding: the space inside a box around its content. */
    private static Div padded(Utility utility) {
        Div content = new Div();
        content.cssClass(Tw.Size.SIZE_4, Tw.Bg.BG_INDIGO_500, Tw.Rounded.ROUNDED_SM);

        Div box = new Div(content);
        box.cssClass(Tw.Inline.INLINE_BLOCK, Tw.Bg.BG_INDIGO_100, Tw.Rounded.ROUNDED_MD, utility);
        return box;
    }

    /** Margin: the space outside a box, inside its frame. */
    private static Div margined(Utility utility) {
        Div box = new Div();
        box.cssClass(Tw.Size.SIZE_4, Tw.Bg.BG_INDIGO_500, Tw.Rounded.ROUNDED_SM, utility);

        Div frame = new Div(box);
        frame.cssClass(Tw.Inline.INLINE_BLOCK, Tw.Bg.BG_INDIGO_100, Tw.Rounded.ROUNDED_MD, Tw.Overflow.OVERFLOW_HIDDEN);
        return frame;
    }

    /** box-border or box-content: a box w-24 with padding and a border, in a guide 24 wide: content-box spills past it. */
    private static Div boxSizing(Utility utility) {
        Div box = new Div();
        box.cssClass(Tw.W.W_24, Tw.H.H_8, Tw.P.P_2, Tw.Border.BORDER_4, Tw.Border.BORDER_INDIGO_300, Tw.Bg.BG_INDIGO_500, utility);

        Div guide = new Div(box);
        guide.cssClass(Tw.W.W_24, Tw.Border.BORDER_DASHED, Tw.Border.BORDER_SLATE_400);
        return guide;
    }

    private static Div aspect(Utility utility) {
        Div box = new Div();
        box.cssClass(Tw.W.W_16, Tw.Bg.BG_INDIGO_500, Tw.Rounded.ROUNDED_SM, utility);
        return box;
    }

    // ---- sizes ----

    /** A width: a bar in a track. {@code base}: the width the bar has without it (min-w and max-w act on one). */
    private static Div bar(Utility utility, CssClass base) {
        Div bar = new Div();
        bar.cssClass(Tw.H.H_3, Tw.Bg.BG_INDIGO_500, Tw.Rounded.ROUNDED_FULL);
        if (base != null) bar.cssClass(base);
        bar.cssClass(utility);

        Div track = new Div(bar);
        track.cssClass(Tw.W.W_48, Tw.Bg.BG_SLATE_200, Tw.Rounded.ROUNDED_FULL, Tw.Overflow.OVERFLOW_HIDDEN);
        return track;
    }

    /** A height: a column in a track. {@code base}: the height it has without it (min-h and max-h act on one). */
    private static Div column(Utility utility, CssClass base) {
        Div column = new Div();
        column.cssClass(Tw.W.W_3, Tw.Bg.BG_INDIGO_500, Tw.Rounded.ROUNDED_FULL);
        if (base != null) column.cssClass(base);
        column.cssClass(utility);

        Div track = new Div(column);
        track.cssClass(Tw.H.H_16, Tw.Flex.FLEX, Tw.Items.ITEMS_END, Tw.Overflow.OVERFLOW_HIDDEN);
        return track;
    }

    // ---- layout ----

    /** A layout class on a container of three numbered boxes: how it places them. */
    private static Div container(String family, Utility utility) {
        Div container = new Div(numbered(1), numbered(2), numbered(3));
        container.cssClass(Tw.W.W_48, Tw.Min.MIN_H_16, Tw.P.P_1, Tw.Bg.BG_SLATE_100, Tw.Rounded.ROUNDED_MD);
        // what it needs to work in: a flex container for justify-*, items-*, gap-*; a grid for place-*
        if (Set.of("justify", "items", "content", "gap").contains(family)) container.cssClass(Tw.Flex.FLEX, Tw.Flex.FLEX_WRAP);
        if (family.equals("place")) container.cssClass(Tw.Grid.GRID, Tw.Grid.GRID_COLS_2, Tw.H.H_20);
        if (family.equals("grid") && !utility.className().equals("grid")) container.cssClass(Tw.Grid.GRID, Tw.Gap.GAP_1);
        if (family.equals("divide")) {
            String name = utility.className();
            container.cssClass(Tw.Flex.FLEX);
            if (!name.matches("divide-[xy].*")) container.cssClass(Tw.Divide.DIVIDE_X_2);   // a colour or a style: a width to show on
            if (!colourOnly(name)) container.cssClass(Tw.Divide.DIVIDE_INDIGO_400);
        }
        container.cssClass(utility);
        return container;
    }

    /** A child's class on the first of three (order-*: the middle one), in a row (a grid of three for col-* and row-*). */
    private static Div child(String family, Utility utility) {
        Div first = numbered(1);
        Div second = numbered(2);
        Div pink = family.equals("order") ? second : first;
        pink.cssClass(Tw.Bg.BG_PINK_500, utility);

        Div row = new Div(first, second, numbered(3));
        row.cssClass(Tw.W.W_48, Tw.P.P_1, Tw.Gap.GAP_1, Tw.Bg.BG_SLATE_100, Tw.Rounded.ROUNDED_MD);
        if (family.equals("col") || family.equals("row")) row.cssClass(Tw.Grid.GRID, Tw.Grid.GRID_COLS_3, Tw.Grid.GRID_ROWS_2);
        else row.cssClass(Tw.Flex.FLEX, Tw.H.H_16);
        return row;
    }

    /** A display class on the middle of three: how it shows (or does not). */
    private static Div middleOfThree(Utility utility) {
        Span middle = new Span("two");
        middle.cssClass(Tw.Bg.BG_PINK_500, Tw.Text.TEXT_WHITE, Tw.Px.PX_1, Tw.Rounded.ROUNDED_SM, utility);

        Div line = new Div(new Span("one "), middle, new Span(" three"));
        line.cssClass(Tw.Text.TEXT_SM, Tw.W.W_48);
        return line;
    }

    /** A position: a box in a frame (offset a little, so relative, absolute and the rest differ). */
    private static Div positioned(Utility utility) {
        Div box = numbered(1);
        box.cssClass(Tw.Bg.BG_PINK_500, Tw.Top.TOP_2, Tw.Left.LEFT_4, utility);

        Div frame = new Div(numbered(2), box);
        frame.cssClass(Tw.Relative.RELATIVE, Tw.W.W_40, Tw.H.H_16, Tw.Bg.BG_SLATE_100, Tw.Rounded.ROUNDED_MD);
        return frame;
    }

    /** An offset: on an absolute box in a relative frame. */
    private static Div placed(Utility utility) {
        boolean allSides = utility.className().matches("-?inset-(?![xy]-).*");
        Div box = allSides ? new Div() : numbered(1);   // inset sets every side: the box takes its size from it
        box.cssClass(Tw.Absolute.ABSOLUTE, Tw.Bg.BG_PINK_500, Tw.Rounded.ROUNDED_SM, utility);

        Div frame = new Div(box);
        frame.cssClass(Tw.Relative.RELATIVE, Tw.W.W_40, Tw.H.H_16, Tw.Bg.BG_SLATE_100, Tw.Rounded.ROUNDED_MD, Tw.Overflow.OVERFLOW_HIDDEN,
                Tw.Outline.OUTLINE, Tw.Outline.OUTLINE_SLATE_300);
        return frame;
    }

    /** A stacking order: the first of two overlapping boxes, the other at z-10. */
    private static Div stacked(Utility utility) {
        Div first = box("z");
        first.cssClass(Tw.Bg.BG_PINK_500, Tw.Relative.RELATIVE, utility);

        Div second = box("10");
        second.cssClass(Tw.Bg.BG_INDIGO_500, Tw.Relative.RELATIVE, Tw.Z.Z_10, Tw.Ml.NEG_ML_4, Tw.Mt.MT_2);

        Div pair = new Div(first, second);
        pair.cssClass(Tw.Flex.FLEX);
        return pair;
    }

    /** A float or a clear: a box among text. */
    private static Div floated(Utility utility) {
        Div box = numbered(1);
        box.cssClass(Tw.Bg.BG_PINK_500, Tw.M.M_1, utility);

        P text = new P(box);
        text.add(new Span(LONG_TEXT));
        text.cssClass(Tw.W.W_48, Tw.Text.TEXT_XS);
        return new Div(text);
    }

    // ---- motion and the pointer ----

    private static Div animated(Utility utility) {
        Div box = box("");
        box.cssClass(Tw.Size.SIZE_8, Tw.Bg.BG_INDIGO_500, utility);
        return box;
    }

    /** A transition: hover the box to see it (it grows, at the class's pace). */
    private static Div transitioned(Utility utility) {
        Div box = box("hover");
        box.cssClass(Tw.Bg.BG_INDIGO_500, Tw.Transition.TRANSITION, Tw.hover(Tw.Scale.SCALE_125), Tw.hover(Tw.Bg.BG_PINK_500), utility);
        return box;
    }

    private static Div hoverMe(Utility utility) {
        Div box = new Div("hover me, select me");
        box.cssClass(Tw.P.P_2, Tw.Text.TEXT_XS, Tw.Bg.BG_SLATE_100, Tw.Rounded.ROUNDED_MD, utility);
        return box;
    }

    private static Div transformed(Utility utility) {
        Div box = box("Aa");
        box.cssClass(Tw.Bg.BG_INDIGO_500, utility);
        return box;
    }

    /** A transform origin: a box turned 45 degrees about it, over a dashed frame where it was. */
    private static Div pivoted(Utility utility) {
        Div box = box("Aa");
        box.cssClass(Tw.Bg.BG_INDIGO_500, Tw.Absolute.ABSOLUTE, Tw.Inset.INSET_0, Tw.Rotate.ROTATE_45, Tw.Opacity.OPACITY_80, utility);

        Div frame = new Div(box);
        frame.cssClass(Tw.Relative.RELATIVE, Tw.Size.SIZE_12, Tw.M.M_6, Tw.Outline.OUTLINE_2, Tw.Outline.OUTLINE_DASHED,
                Tw.Outline.OUTLINE_SLATE_400, Tw.Rounded.ROUNDED_MD);
        return frame;
    }

    /** A gradient's stop at a place: a bar from pink to indigo through white, the stop moved. */
    private static Div gradientStop(Utility utility) {
        Div bar = new Div();
        bar.cssClass(Tw.W.W_40, Tw.H.H_8, Tw.Rounded.ROUNDED_MD, Tw.Bg.BG_LINEAR_TO_R, Tw.From.FROM_PINK_500, Tw.Via.VIA_WHITE,
                Tw.To.TO_INDIGO_500, utility);
        return bar;
    }

    /** An accent colour: on a checked checkbox. */
    private static Div checkbox(Utility utility) {
        Input box = new Input();
        box.setType(Input.Type.CHECKBOX);
        box.setChecked(true);
        box.cssClass(Tw.Size.SIZE_5, utility);
        return new Div(box);
    }

    /** A caret colour: in a text field (click in it). */
    private static Div field(Utility utility) {
        Input field = new Input();
        field.setValue("click here");
        field.cssClass(Tw.W.W_32, Tw.Px.PX_2, Tw.Py.PY_1, Tw.Text.TEXT_XS, Tw.Border.BORDER, Tw.Border.BORDER_SLATE_300,
                Tw.Rounded.ROUNDED_MD, utility);
        return new Div(field);
    }

    /** A wide picture (an SVG, 2:1: a sun over hills), for object-fit and object-position. */
    static final String PICTURE = "data:image/svg+xml,"
            + "<svg xmlns='http://www.w3.org/2000/svg' width='120' height='60' viewBox='0 0 120 60'>"
            + "<rect width='120' height='60' fill='%23c7d2fe'/><circle cx='88' cy='20' r='10' fill='%23f59e0b'/>"
            + "<path d='M0 60 L35 25 L60 45 L85 30 L120 60 Z' fill='%234f46e5'/><text x='4' y='12' font-size='9' fill='%23312e81'>1 2 3</text></svg>";

    /**
     * An object-fit or object-position: a wide picture in a square frame. A position shows on a picture not stretched
     * to the frame (object-none: at its own size).
     */
    private static Div picture(Utility utility) {
        Img picture = new Img();
        picture.setSrc(PICTURE).setAlt("a sun over hills");
        picture.cssClass(Tw.Size.SIZE_16, Tw.Bg.BG_SLATE_200, Tw.Rounded.ROUNDED_MD);
        if (!utility.className().matches("object-(contain|cover|fill|none|scale-down)")) picture.cssClass(Tw.Object.OBJECT_NONE);
        picture.cssClass(utility);
        return new Div(picture);
    }

    /** Anything else: the class on a small sample. */
    private static Div sample(Utility utility) {
        Div sample = box("Aa");
        sample.cssClass(Tw.Bg.BG_SLATE_200, Tw.Text.TEXT_SLATE_900, utility);
        return sample;
    }

    // ---- small parts ----

    /** A small numbered box. */
    private static Div numbered(int n) {
        Div box = new Div(String.valueOf(n));
        box.cssClass(Tw.Size.SIZE_6, Tw.Bg.BG_INDIGO_500, Tw.Text.TEXT_WHITE, Tw.Text.TEXT_XS, Tw.Rounded.ROUNDED_SM,
                Tw.Flex.FLEX, Tw.Items.ITEMS_CENTER, Tw.Justify.JUSTIFY_CENTER, Tw.Shrink.SHRINK_0);
        return box;
    }

    /** A box with a word in it, centred. */
    private static Div box(String text) {
        Div box = new Div(text);
        box.cssClass(Tw.Size.SIZE_12, Tw.Rounded.ROUNDED_MD, Tw.Text.TEXT_WHITE, Tw.Text.TEXT_XS, Tw.Flex.FLEX, Tw.Items.ITEMS_CENTER,
                Tw.Justify.JUSTIFY_CENTER);
        return box;
    }
}
