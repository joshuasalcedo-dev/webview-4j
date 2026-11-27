package io.joshuasalcedo.swaswa.dom;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The page's function a method of a {@link Dom#bind bound} interface calls, when it is not the method's name: a path
 * on {@code window} too ({@code "app.cart.total"}).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PageFunction {

    String value();
}
