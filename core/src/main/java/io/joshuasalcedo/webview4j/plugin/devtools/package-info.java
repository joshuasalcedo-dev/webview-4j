/**
 * Dev tools: a window to keep beside a webview-4j app while you develop it.
 * <p>
 * <img src="doc-files/dev-window.png" alt="The dev window beside an app">
 * <ul>
 *   <li><b>What the app does now:</b> frames per second between Java and the native side, each way, and kilobytes
 *   per second; actions and pages' calls per second; how long the latest action took, with the mean and the 95th
 *   percentile; the same for the pages' calls (commands and routes); each page's frames per second.</li>
 *   <li><b>Log:</b> Java's logging, the native side's log, the pages' console and events, with a filter, pause and
 *   follow.</li>
 *   <li><b>Network and storage:</b> each page's requests, cookies and storage.</li>
 *   <li><b>API explorer:</b> an HTTP API from its OpenAPI description ({@code /v3/api-docs}, {@code /openapi.json}):
 *   try it, run a mock server from it, run the project's tests.</li>
 * </ul>
 * <pre>{@code
 * DevWindow.attachIfDev(app);   // only with -Dwebview4j.dev=true or WEBVIEW4J_DEV=true
 * DevWindow.attach(app);        // always
 * }</pre>
 * A {@link io.joshuasalcedo.webview4j.WebviewApplication} attaches it by itself in development.
 * The numbers come from {@code App.metrics()}, which any app can read.
 */
package io.joshuasalcedo.webview4j.plugin.devtools;
