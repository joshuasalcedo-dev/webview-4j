package io.joshuasalcedo.webview4j.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds a query parameter ({@code ?name=...}) to this parameter: text, a number, a boolean or an
 * enum constant. A primitive is required; anything else is null when the page leaves it out.
 * The name is the parameter's own unless {@code value} says otherwise.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.PARAMETER)
public @interface Query {
    String value() default "";
}
