package io.joshuasalcedo.webview4j.explorer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Javadoc, as pretty-docs keeps it (HTML and inline tags), turned into Markdown for the explorer's pages. */
final class Javadocs {

    private static final Pattern PRE = Pattern.compile("(?is)<pre>\\s*(.*?)\\s*</pre>");
    private static final Pattern A = Pattern.compile("(?is)<a\\s[^>]*>(.*?)</a>");

    private Javadocs() {}

    /** The code examples in {@code doc}: each {@code <pre>{@code ...}</pre>}, as plain code. */
    static List<String> examples(String doc) {
        List<String> out = new ArrayList<>();
        Matcher m = PRE.matcher(doc == null ? "" : doc);
        while (m.find()) out.add(preCode(m.group(1)));
        return out;
    }

    /** {@code doc} and its block tags as Markdown. */
    static String markdown(String doc, List<ApiIndex.Tag> tags) {
        StringBuilder out = new StringBuilder(markdown(doc));
        List<String> params = new ArrayList<>();
        List<String> other = new ArrayList<>();
        for (ApiIndex.Tag tag : tags == null ? List.<ApiIndex.Tag>of() : tags) {
            String text = inline(tag.text());
            switch (tag.name()) {
                case "@param" -> params.add("- `" + tag.param() + "` " + text);
                case "@return" -> other.add("**Returns** " + text);
                case "@throws", "@exception" -> other.add("**Throws** " + text);
                case "@see" -> other.add("**See** " + text);
                case "@since" -> other.add("**Since** " + text);
                case "@deprecated" -> other.add("**Deprecated** " + text);
                default -> {}
            }
        }
        if (!params.isEmpty()) out.append("\n\n**Parameters**\n\n").append(String.join("\n", params));
        if (!other.isEmpty()) out.append("\n\n").append(String.join("\n\n", other));
        return out.toString().strip();
    }

    /** {@code doc} as Markdown: code blocks fenced, inline tags and HTML turned into their Markdown. */
    static String markdown(String doc) {
        if (doc == null || doc.isBlank()) return "";
        List<String> blocks = new ArrayList<>();
        Matcher m = PRE.matcher(doc);
        StringBuilder text = new StringBuilder();
        while (m.find()) {
            blocks.add("```java\n" + preCode(m.group(1)) + "\n```");
            m.appendReplacement(text, Matcher.quoteReplacement("\n\n\u0000" + (blocks.size() - 1) + "\u0000\n\n"));
        }
        m.appendTail(text);
        String md = inline(text.toString());
        for (int i = 0; i < blocks.size(); i++) md = md.replace("\u0000" + i + "\u0000", blocks.get(i));
        return md.replaceAll("\n{3,}", "\n\n").strip();
    }

    /** The first sentence of {@code doc}, as plain text: a member's line in a list. */
    static String summary(String doc) {
        String plain = inline(PRE.matcher(doc == null ? "" : doc).replaceAll(" ")).replaceAll("[`*]", "").replaceAll("\\s+", " ").strip();
        int end = plain.indexOf(". ");
        if (end >= 0) plain = plain.substring(0, end + 1);
        return plain.length() > 160 ? plain.substring(0, 157) + "…" : plain;
    }

    /** What is inside a {@code <pre>}: {@code {@code ...}} unwrapped, HTML entities decoded, indentation removed. */
    private static String preCode(String pre) {
        String code = pre.strip();
        if (code.startsWith("{@code")) {
            code = code.substring("{@code".length());
            int end = code.lastIndexOf('}');
            if (end >= 0) code = code.substring(0, end);
        } else {
            code = code.replaceAll("<[^>]+>", "");
            code = entities(code);
        }
        return dedent(code.replace("\r\n", "\n").replaceAll("^\\n+", "").stripTrailing());
    }

    private static String dedent(String code) {
        int indent = Integer.MAX_VALUE;
        for (String line : code.split("\n")) {
            if (line.isBlank()) continue;
            int i = 0;
            while (i < line.length() && line.charAt(i) == ' ') i++;
            indent = Math.min(indent, i);
        }
        if (indent == 0 || indent == Integer.MAX_VALUE) return code;
        StringBuilder out = new StringBuilder();
        for (String line : code.split("\n", -1)) {
            out.append(line.length() >= indent ? line.substring(indent) : line.strip()).append('\n');
        }
        return out.toString().stripTrailing();
    }

    /** Inline tags and HTML outside code blocks. */
    private static String inline(String text) {
        String out = inlineTags(text == null ? "" : text);
        out = A.matcher(out).replaceAll(r -> Matcher.quoteReplacement(r.group(1)));
        out = out.replaceAll("(?i)<p>\\s*", "\n\n").replaceAll("(?i)</p>", "\n\n")
                .replaceAll("(?i)<br\\s*/?>", "  \n")
                .replaceAll("(?i)</?(b|strong)>", "**")
                .replaceAll("(?i)</?(i|em)>", "*")
                .replaceAll("(?i)</?code>", "`")
                .replaceAll("(?i)<li>\\s*", "\n- ").replaceAll("(?i)</li>", "")
                .replaceAll("(?i)</?(ul|ol)>", "\n\n")
                .replaceAll("(?i)<h\\d[^>]*>", "\n\n#### ").replaceAll("(?i)</h\\d>", "\n\n")
                .replaceAll("<[^>]+>", "");
        out = entities(out);
        // Lines a source comment broke are one paragraph; blank lines and list items stay.
        out = out.replaceAll("(?<!\n)\n(?![\n-])", " ").replaceAll("[ \t]+\n", "\n").replaceAll(" {2,}", " ");
        return out;
    }

    /** {@code {@code x}}, {@code {@link A#b label}}, {@code {@literal x}}... with nested braces. */
    private static String inlineTags(String text) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            int start = text.indexOf("{@", i);
            if (start < 0) {
                out.append(text, i, text.length());
                break;
            }
            out.append(text, i, start);
            int depth = 0;
            int end = start;
            for (; end < text.length(); end++) {
                char c = text.charAt(end);
                if (c == '{') depth++;
                else if (c == '}' && --depth == 0) break;
            }
            if (end >= text.length()) {
                out.append(text, start, text.length());
                break;
            }
            String body = text.substring(start + 2, end);
            int space = body.indexOf(' ');
            String tag = space < 0 ? body : body.substring(0, space);
            String rest = space < 0 ? "" : body.substring(space + 1).strip();
            out.append(switch (tag) {
                case "code" -> "`" + rest + "`";
                case "literal" -> rest;
                case "link", "linkplain" -> link(rest, tag.equals("link"));
                case "value" -> rest.isEmpty() ? "" : "`" + rest + "`";
                default -> "";
            });
            i = end + 1;
        }
        return out.toString();
    }

    private static String link(String reference, boolean code) {
        String target = reference;
        String label = null;
        int depth = 0;
        for (int i = 0; i < reference.length(); i++) {
            char c = reference.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ' ' && depth == 0) {
                target = reference.substring(0, i);
                label = reference.substring(i + 1).strip();
                break;
            }
        }
        if (label != null && !label.isEmpty()) return code ? "`" + label + "`" : label;
        String shown = target.startsWith("#") ? target.substring(1) : target.replace('#', '.');
        int dot = shown.lastIndexOf('.', shown.indexOf('(') < 0 ? shown.length() : shown.indexOf('('));
        // pkg.Type -> Type; Type.member(..) stays as it is
        if (dot >= 0 && !target.contains("#")) shown = shown.substring(dot + 1);
        return "`" + shown + "`";
    }

    private static String entities(String text) {
        return text.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&nbsp;", " ").replace("&#64;", "@").replace("&amp;", "&");
    }
}
