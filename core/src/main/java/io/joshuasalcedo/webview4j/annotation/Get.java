package io.joshuasalcedo.webview4j.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A GET route on a {@link WebViewController} method. {@code value} is the path after the
 * controller's, e.g. {@code "/{id}"}; segments are letters, digits, {@code - _ . ~}, or a
 * {@code {name}} bound to a {@link Path} parameter.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface Get {
    String value() default "";
}
