import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Writes {@code Tw}: every Tailwind class as a typed constant, one nested class per family ({@code Tw.P.P_4},
 * {@code Tw.Bg.BG_RED_500}; a family too big for one class split by its next word: {@code Tw.Bg.Red.BG_RED_500}), and
 * the variants as methods ({@code Tw.hover(...)}, {@code Tw.md(...)}). Read from the data Tailwind itself made
 * ({@code tailwind.json.gz}, src/build/tailwind-data.ts).
 *
 * <p>Run by the build before compiling, as a single source file: {@code java TailwindJava.java <tailwind.json.gz> <out>}.
 */
public class TailwindJava {

    static final String PACKAGE = "io.joshuasalcedo.swaswa.tailwind";
    static final String HEADER = "// Generated (src/build/TailwindJava.java) from tailwind.json.gz, kept in the sources: do not edit.\n";
    /** At most this many constants in one class: about 13 bytes each, under the JVM's 64 KB for a class's initializer. */
    static final int MOST = 3600;

    /** Tailwind's variants this library has, as methods: method -> the Variant constant. */
    static final String[][] VARIANTS = {
            {"hover", "HOVER"}, {"focus", "FOCUS"}, {"focusVisible", "FOCUS_VISIBLE"}, {"focusWithin", "FOCUS_WITHIN"},
            {"active", "ACTIVE"}, {"visited", "VISITED"}, {"disabled", "DISABLED"}, {"enabled", "ENABLED"}, {"checked", "CHECKED"},
            {"required", "REQUIRED"}, {"invalid", "INVALID"}, {"placeholderShown", "PLACEHOLDER_SHOWN"}, {"open", "OPEN"},
            {"first", "FIRST"}, {"last", "LAST"}, {"only", "ONLY"}, {"odd", "ODD"}, {"even", "EVEN"}, {"empty", "EMPTY"},
            {"placeholder", "PLACEHOLDER"}, {"selection", "SELECTION"}, {"marker", "MARKER"}, {"file", "FILE"},
            {"groupHover", "GROUP_HOVER"}, {"groupFocus", "GROUP_FOCUS"}, {"peerHover", "PEER_HOVER"}, {"peerChecked", "PEER_CHECKED"},
            {"dark", "DARK"}, {"sm", "SM"}, {"md", "MD"}, {"lg", "LG"}, {"xl", "XL"}, {"xxl", "XXL"},
            {"maxSm", "MAX_SM"}, {"maxMd", "MAX_MD"}, {"maxLg", "MAX_LG"}, {"print", "PRINT"},
            {"motionSafe", "MOTION_SAFE"}, {"motionReduce", "MOTION_REDUCE"}, {"portrait", "PORTRAIT"}, {"landscape", "LANDSCAPE"},
    };

    public static void main(String[] args) throws IOException {
        Path data = Path.of(args[0]);
        Path out = Path.of(args[1]).resolve(PACKAGE.replace('.', '/'));
        Path target = out.resolve("Tw.java");
        byte[] raw = Files.readAllBytes(data);
        // which data (and which generator) it was made from: its second line; made again only when those change
        String stamp = "// from: " + Integer.toHexString(java.util.Arrays.hashCode(raw)) + "-4\n";
        if (Files.exists(target) && Files.readString(target, StandardCharsets.UTF_8).startsWith(HEADER + stamp)) return;

        String json;
        try (InputStream in = new GZIPInputStream(Files.newInputStream(data))) {
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        // only the names are needed here: "n":"..."
        List<String> names = new ArrayList<>();
        Matcher m = Pattern.compile("\"n\":\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        while (m.find()) names.add(m.group(1).replace("\\\\", "\\").replace("\\\"", "\""));
        Matcher v = Pattern.compile("\"version\":\"([^\"]+)\"").matcher(json);
        String version = v.find() ? v.group(1) : "?";

        // families: the first word ("bg" of bg-red-500; "m" of -m-4)
        Map<String, List<String>> families = new TreeMap<>();
        for (String n : names) families.computeIfAbsent(family(n), k -> new ArrayList<>()).add(n);

        StringBuilder b = new StringBuilder(HEADER).append(stamp);
        b.append("package ").append(PACKAGE).append(";\n\n");
        b.append("/**\n * Tailwind ").append(version).append("'s classes, typed: {@code Tw.P.P_4} is {@code p-4}, {@code Tw.Bg.BG_RED_500}")
                .append(" {@code bg-red-500};\n * one nested class per family. Variants are methods: {@code Tw.hover(Tw.Bg.BG_BLUE_500)}, {@code Tw.md(...)}.\n")
                .append(" * By name, checked: {@link #of(String)}. ").append(names.size()).append(" classes.\n */\n");
        b.append("public final class Tw {\n\n    private Tw() {}\n\n");
        b.append("    /** A class by name, with variants too ({@code \"md:hover:p-8\"}), checked against Tailwind's. */\n");
        b.append("    public static Utility of(String name) { return Utility.of(name); }\n\n");
        b.append("    /** {@code class=\"group\"}: a parent whose hover or focus {@link #groupHover} and {@link #groupFocus} follow. */\n");
        b.append("    public static final io.joshuasalcedo.swaswa.css.CssClass GROUP = io.joshuasalcedo.swaswa.css.CssClass.of(\"group\");\n");
        b.append("    /** {@code class=\"peer\"}: a sibling whose state {@link #peerHover} and {@link #peerChecked} follow. */\n");
        b.append("    public static final io.joshuasalcedo.swaswa.css.CssClass PEER = io.joshuasalcedo.swaswa.css.CssClass.of(\"peer\");\n\n");
        for (String[] variant : VARIANTS) {
            b.append("    /** {@code ").append(prefixOf(variant[1])).append(":} each of these. */\n");
            b.append("    public static Utility ").append(variant[0]).append("(Utility utility) { return utility.when(Variant.")
                    .append(variant[1]).append("); }\n\n");
        }
        Set<String> usedClassNames = new LinkedHashSet<>(List.of("Tw"));
        for (Map.Entry<String, List<String>> f : families.entrySet()) {
            String cls = unique(pascal(f.getKey()), usedClassNames);
            b.append("    /** {@code ").append(f.getKey()).append("-*}. */\n");
            b.append("    public static final class ").append(cls).append(" {\n        private ").append(cls).append("() {}\n\n");
            if (f.getValue().size() <= MOST) {
                constants(b, f.getValue(), "        ");
            } else {
                // too many for one class: by their next word (bg-red-*, bg-blue-*...)
                Map<String, List<String>> parts = new TreeMap<>();
                for (String n : f.getValue()) parts.computeIfAbsent(second(n), k -> new ArrayList<>()).add(n);
                Set<String> inner = new LinkedHashSet<>(List.of(cls));
                for (Map.Entry<String, List<String>> p : parts.entrySet()) {
                    String sub = unique(pascal(p.getKey().isEmpty() ? "base" : p.getKey()), inner);
                    b.append("        /** {@code ").append(f.getKey()).append("-").append(p.getKey()).append("*}. */\n");
                    b.append("        public static final class ").append(sub).append(" {\n            private ").append(sub).append("() {}\n\n");
                    constants(b, p.getValue(), "            ");
                    b.append("        }\n\n");
                }
            }
            b.append("    }\n\n");
        }
        b.append("}\n");
        Files.createDirectories(out);
        Files.writeString(target, b.toString(), StandardCharsets.UTF_8);
        System.out.println("TailwindJava: " + names.size() + " classes in " + families.size() + " families into " + target);
    }

    static void constants(StringBuilder b, List<String> names, String indent) {
        Set<String> used = new LinkedHashSet<>();
        for (String n : names) {
            String c = unique(constant(n), used);
            b.append(indent).append("/** {@code ").append(n.replace("*/", "*&#47;").replace("@", "&#64;")).append("} */\n");
            b.append(indent).append("public static final Utility ").append(c).append(" = new Utility(\"")
                    .append(n.replace("\\", "\\\\").replace("\"", "\\\"")).append("\");\n");
        }
    }

    static String family(String name) {
        String n = name.startsWith("-") ? name.substring(1) : name;
        if (n.startsWith("@")) n = n.substring(1);
        int dash = n.indexOf('-');
        return dash > 0 ? n.substring(0, dash) : n;
    }

    static String second(String name) {
        String n = name.startsWith("-") ? name.substring(1) : name;
        String[] parts = n.split("-");
        return parts.length > 1 ? parts[1].replaceAll("[^A-Za-z0-9]", "") : "";
    }

    /** {@code bg-red-500} is {@code BG_RED_500}; {@code -m-4} {@code NEG_M_4}; {@code w-1/2} {@code W_1_2}; {@code p-0.5} {@code P_0_5}. */
    static String constant(String name) {
        String n = name;
        String prefix = "";
        if (n.startsWith("-")) { prefix = "NEG_"; n = n.substring(1); }
        if (n.startsWith("@")) { prefix += "AT_"; n = n.substring(1); }
        String c = prefix + n.replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_|_$", "").toUpperCase(Locale.ROOT);
        if (c.isEmpty() || Character.isDigit(c.charAt(0))) c = "_" + c;
        return c;
    }

    static String pascal(String word) {
        StringBuilder b = new StringBuilder();
        for (String part : word.split("[^A-Za-z0-9]+")) {
            if (part.isEmpty()) continue;
            b.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        String s = b.toString();
        return s.isEmpty() || Character.isDigit(s.charAt(0)) ? "X" + s : s;
    }

    static String unique(String name, Set<String> used) {
        String n = name;
        for (int i = 2; !used.add(n); i++) n = name + "_" + i;
        return n;
    }

    static String prefixOf(String constant) {
        return switch (constant) {
            case "XXL" -> "2xl";
            default -> constant.toLowerCase(Locale.ROOT).replace('_', '-');
        };
    }
}
