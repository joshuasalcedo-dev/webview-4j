package io.joshuasalcedo.swaswa.css;

import java.util.Locale;

/**
 * An app's own CSS classes as an enum, typed: each constant is the class of its name, in lower case with dashes
 * ({@code BRAND_BUTTON} is {@code brand-button}). Their CSS is the app's style sheet's ({@code MyApp.css}).
 * <pre>{@code
 * enum Styles implements CssClasses { HERO, BRAND_BUTTON }
 *
 * banner.cssClass(Styles.HERO);
 * }</pre>
 */
public interface CssClasses extends CssClass {

    @Override
    default String className() {
        if (!(this instanceof Enum<?> e)) throw new IllegalStateException("CssClasses is for an enum: " + getClass().getName());
        return e.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
