/**
 * swaswa-webview: an app that brings its own web page, which calls Java as a page calls a server
 * ({@link io.joshuasalcedo.swaswa.webview.WebViewApplication}, {@code @WebViewController}s, the same routes over HTTP).
 * Hidden in it (internal.processor, not exported, no META-INF/services): the annotation processor that writes a
 * controller's route table and the page's TypeScript client. An app with controllers names it in its compiler's
 * annotationProcessorPaths and annotationProcessors.
 */
module io.joshuasalcedo.swaswa.webview {
    requires transitive io.joshuasalcedo.swaswa.ffi;
    // the same routes over plain HTTP (HttpApi)
    requires jdk.httpserver;
    // the annotation processor: javac has java.compiler, an app needs none
    requires static java.compiler;
    exports io.joshuasalcedo.swaswa.webview;
    exports io.joshuasalcedo.swaswa.webview.annotation;
}
