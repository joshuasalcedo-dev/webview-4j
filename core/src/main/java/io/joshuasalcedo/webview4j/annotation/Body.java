package io.joshuasalcedo.webview4j.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds the request's JSON body to this parameter: a record, a list, a map, text, a number, a
 * boolean or an enum. At most one per route, and not on {@link Get}.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.PARAMETER)
public @interface Body {}
