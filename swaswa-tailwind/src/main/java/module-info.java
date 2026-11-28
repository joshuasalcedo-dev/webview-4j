/**
 * swaswa-tailwind: Tailwind's classes, typed, for swaswa's components ({@code cssClass(Tw.P.P_4)}). A page gets the
 * CSS of the classes it uses, and only those, written in Java from Tailwind's own data: no Node, no Tailwind build.
 */
module io.joshuasalcedo.swaswa.tailwind {
    requires transitive io.joshuasalcedo.swaswa.ffi;
    exports io.joshuasalcedo.swaswa.tailwind;
    // a style sheet loaded with it on the class path: Tailwind's @apply, @theme and @utility done
    provides io.joshuasalcedo.swaswa.css.CssProcessor with io.joshuasalcedo.swaswa.tailwind.TailwindProcessor;
}
