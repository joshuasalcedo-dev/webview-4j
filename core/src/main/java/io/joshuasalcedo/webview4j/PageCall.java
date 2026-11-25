package io.joshuasalcedo.webview4j;

/**
 * A page's call that Java answered: a command ({@code invoke("users.find", {id: 42})}, {@code name} the command)
 * or a route ({@code GET /users/42}, {@code name} the method and path). Sent once it is answered, with what
 * the page sent, what Java answered (or why it failed) and how long Java took. For watching an app while it is
 * developed (the dev logger): {@code app.subscribe(PageCall.class, call -> ...)}.
 *
 * @param args       what the page sent, as JSON values (a command's arguments, a route's body as text); may be null
 * @param result     what Java answered (the handler's value, before it became JSON); null when it failed
 * @param error      why it failed (the exception's message), or null
 * @param durationMs from the page's call reaching Java to the answer
 */
public record PageCall(String view, String name, Object args, Object result, String error, double durationMs)
        implements WebViewEvent {}
