package io.joshuasalcedo.webview4j;

import java.util.Locale;

/**
 * Something the native library can do. The three groups are {@link WindowAction},
 * {@link WebViewAction} and {@link InspectorAction}; their codes match
 * {@code crates/webview/src/action.rs}, and FFM passes an action as its code.
 */
sealed interface Action permits WindowAction, WebViewAction, InspectorAction {

    /** The group, e.g. {@code window}. */
    String group();

    /** The stable number both sides know the action by, e.g. {@code 102}. */
    int code();

    /** The enum constant's name; the enums supply it. */
    String name();

    /** The action as written outside Java, e.g. {@code set-title} for {@code SET_TITLE}. */
    default String wireName() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
