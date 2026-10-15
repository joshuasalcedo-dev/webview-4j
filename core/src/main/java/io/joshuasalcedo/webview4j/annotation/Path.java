package io.joshuasalcedo.webview4j.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds a route's {@code {name}} path segment to this parameter: text, a number, a boolean or an
 * enum constant. The name is the parameter's own unless {@code value} says otherwise.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.PARAMETER)
public @interface Path {
    String value() default "";
}
