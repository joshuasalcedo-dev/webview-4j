package io.joshuasalcedo.swaswa.ffi;

/**
 * A value that says itself how it is written as JSON, instead of the default (an enum by its name, a record with
 * all its components): an enum by the page's own word for it ({@code "mod+enter"}), a record without the optional
 * parts left out.
 */
public interface JsonValue {

    /** What is written in its place: a string, a number, a map, a list... */
    Object json();
}
