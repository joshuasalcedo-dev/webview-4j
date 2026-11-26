package io.joshuasalcedo.webview4j.plugin.document;

import io.joshuasalcedo.webview4j.ui.Component;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Values taken from a PDF, each linked to where it is: a box over its page. Pointing at a value marks its box,
 * choosing it scrolls the PDF there; a box clicked chooses its value. With {@link #review}, each value is shown next
 * to what it should be, which can be corrected. The Java side of the kit's {@code BoundingBoxCitations}.
 * <pre>{@code
 * BoundingBoxCitations.of(Path.of("statement.pdf"),
 *         Field.of("ending_balance", "Ending balance", 12840.18).at(Box.of(1, 11.1, 53.0, 77.8, 3.0)),
 *         Field.group("account", "Account",
 *                 Field.of("holder", "Holder", "Jordan Lee").at(Box.of(1, 11.1, 20.2, 30.4, 2.8))))
 *     .active(chosen).onActiveChange(k -> chosen = k)
 * }</pre>
 */
public final class BoundingBoxCitations extends Component<BoundingBoxCitations> {

    /**
     * Where a value is: its page (1 for the first) and its box, in percent of the page's width and height (0 to
     * 100) from its top left corner, as the page is in the file (before the viewer turns it).
     */
    public record Box(int page, double left, double top, double width, double height) {

        public static Box of(int page, double left, double top, double width, double height) {
            return new Box(page, left, top, width, height);
        }

        /**
         * A box in the page's own units (PDF points from the top left, or an OCR service's pixels), with the page's
         * size in the same units: {@code Box.inUnits(1, 72, 140, 220, 22, 612, 792)}.
         */
        public static Box inUnits(int page, double x, double y, double width, double height, double pageWidth, double pageHeight) {
            return new Box(page, x / pageWidth * 100, y / pageHeight * 100, width / pageWidth * 100, height / pageHeight * 100);
        }

        Map<String, Object> write() {
            return Map.of("page", page, "area", Map.of("left", left, "top", top, "width", width, "height", height));
        }
    }

    /**
     * One value taken from the PDF: its key in the data ("ending_balance"), what people call it, the value (a String,
     * a Number, a Boolean, a List or a Map, or null), what it should be, and where it is. A group's fields each have
     * their own box.
     */
    public record Field(String key, String label, Object value, Object expected, String description, Box box, List<Field> fields) {

        public Field {
            Objects.requireNonNull(key, "key");
            fields = fields == null ? List.of() : List.copyOf(fields);
        }

        public static Field of(String key, String label, Object value) {
            return new Field(key, label, value, null, null, null, List.of());
        }

        /** An object's fields; a field inside is found as "key.child". */
        public static Field group(String key, String label, Field... fields) {
            return new Field(key, label, null, null, null, null, List.of(fields));
        }

        public Field at(Box box) { return new Field(key, label, value, expected, description, box, fields); }

        /** What it should be (shown with {@link BoundingBoxCitations#review}). */
        public Field expected(Object expected) { return new Field(key, label, value, expected, description, box, fields); }

        public Field description(String description) { return new Field(key, label, value, expected, description, box, fields); }

        Map<String, Object> write() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", key);
            if (label != null) m.put("label", label);
            if (description != null) m.put("description", description);
            m.put("value", value);
            if (expected != null) m.put("expected", expected);
            if (box != null) m.put("location", box.write());
            if (!fields.isEmpty()) {
                m.put("type", "object");
                List<Map<String, Object>> children = new ArrayList<>();
                for (Field f : fields) children.add(f.write());
                m.put("fields", children);
            }
            return m;
        }
    }

    private BoundingBoxCitations(String src, String fileName, List<Field> fields) {
        super("boundingBoxCitations");
        prop("src", src);
        prop("fileName", fileName);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Field f : fields) rows.add(f.write());
        prop("fields", rows);
    }

    /** The PDF at a URL, and its fields. */
    public static BoundingBoxCitations src(String url, Field... fields) {
        return new BoundingBoxCitations(url, null, List.of(fields));
    }

    public static BoundingBoxCitations of(Path pdf, Field... fields) {
        return of(pdf, List.of(fields));
    }

    public static BoundingBoxCitations of(Path pdf, List<Field> fields) {
        return new BoundingBoxCitations(PdfSource.dataUrl(PdfSource.read(pdf)), pdf.getFileName().toString(), fields);
    }

    public static BoundingBoxCitations of(byte[] pdf, List<Field> fields) {
        return new BoundingBoxCitations(PdfSource.dataUrl(pdf), null, fields);
    }

    public BoundingBoxCitations fileName(String name) { return prop("fileName", name); }

    public BoundingBoxCitations label(String label) { return prop("label", label); }

    /** The field chosen (its key; "group.child" inside a group). */
    public BoundingBoxCitations active(String key) { return prop("active", key); }

    /** A field was chosen (its key), or a box clicked; null when none is. */
    public BoundingBoxCitations onActiveChange(Consumer<String> action) {
        return on("onActiveChange", v -> action.accept(v == null ? null : String.valueOf(v)));
    }

    /** Each value next to its expected value, which can be corrected. */
    public BoundingBoxCitations review() { return prop("review", true); }

    /** An expected value was corrected: its key and the new value (a String, a Number, a Boolean, or null). */
    public BoundingBoxCitations onExpectedChange(BiConsumer<String, Object> action) {
        return on("onExpectedChange", v -> {
            Map<?, ?> m = v instanceof Map<?, ?> map ? map : Map.of();
            action.accept(String.valueOf(m.get("key")), m.get("value"));
        });
    }

    /** Every field's box shown faintly, not only the one pointed at (on unless false). */
    public BoundingBoxCitations allBoxes(boolean shown) { return prop("allBoxes", shown); }

    /** Where the divider between the PDF and the values is, 0 to 1 from the left (0.58 unless set). */
    public BoundingBoxCitations dividerPosition(double position) { return prop("dividerPosition", position); }

    public BoundingBoxCitations zoom(PdfViewer.Zoom zoom) { return prop("zoom", zoom.key); }
}
