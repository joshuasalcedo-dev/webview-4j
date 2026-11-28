package io.joshuasalcedo.swaswa.css;

/**
 * Turns CSS as its author wrote it into CSS a page reads, when a style sheet is loaded ({@link StyleSheet#load}): what a
 * build step does for a web page. swaswa-tailwind's does Tailwind's {@code @apply}, {@code @theme} and {@code @utility}.
 * Found on the class path (a {@code provides ... with} of a module, or {@code META-INF/services}).
 */
public interface CssProcessor {

    /** Whether {@code css} has anything of this processor's (only then is it given to {@link #process}). */
    boolean handles(String css);

    /** {@code css} as a page reads it. */
    String process(String css);

    /** What the processed CSS needs in the page before it (variables, defaults): one shared sheet; null for nothing. */
    default StyleSheet base() {
        return null;
    }
}
