package io.joshuasalcedo.webview4j.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The window a Spring Boot app opens in. Nothing has to be set: the title is the app's name, the window shows the
 * app's page when it has one at {@code /}, and the API explorer when it has an OpenAPI description (springdoc's
 * {@code /v3/api-docs}, {@code /openapi.json}...), as the app's window when it has no page. {@code webview4j.*} in
 * {@code application.properties} changes any of it:
 * <pre>
 * webview4j.title=Books
 * webview4j.path=/app            # the page to open on the app's own server
 * webview4j.url=https://...      # or another address altogether
 * webview4j.api-explorer=false   # never the API explorer
 * webview4j.api-docs=/docs.json  # where the description is
 * webview4j.quit-on-close=true   # closing the window stops the app
 * </pre>
 */
@ConfigurationProperties("webview4j")
public class WebView4jProperties {

    /**
     * Whether the app opens in a window. Unset: yes, except while tests run (JUnit on the class path), so a
     * {@code @SpringBootTest} opens nothing unless it sets {@code webview4j.enabled=true}.
     */
    private Boolean enabled;

    /** The window's title; the app's name ({@code spring.application.name}) when not set. */
    private String title;

    private double width = 1280;
    private double height = 800;

    /** The page to open on the app's own server, {@code http://localhost:<port><path>}; {@code /} when it serves a page there. */
    private String path;

    /** Another address to open instead of the app's own server. */
    private String url;

    /** False: no system frame (the page draws its own title bar). */
    private boolean decorations = true;

    /** True: the window stays hidden (automation, tests). */
    private boolean headless;

    /** Closing the window stops the app, as closing a desktop app does. */
    private boolean quitOnClose = true;

    /** The browser's developer tools in the window (right-click, Inspect). */
    private boolean devtools;

    /**
     * webview-4j's API explorer on the app's OpenAPI description (needs webview-4j-devtools). Unset: when the app
     * has a description; beside its page, or as its window when it has no page.
     */
    private Boolean apiExplorer;

    /** A desktop client's server ({@code @WebViewApplication}): the base URL of every HTTP service client it imports. */
    private String clientUrl;

    public String getClientUrl() { return clientUrl; }
    public void setClientUrl(String clientUrl) { this.clientUrl = clientUrl; }

    /** Where the description is on the app's own server. Unset: the first of /v3/api-docs, /openapi.json, /q/openapi, /openapi that is one. */
    private String apiDocs;

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public double getWidth() { return width; }
    public void setWidth(double width) { this.width = width; }
    public double getHeight() { return height; }
    public void setHeight(double height) { this.height = height; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public boolean isDecorations() { return decorations; }
    public void setDecorations(boolean decorations) { this.decorations = decorations; }
    public boolean isHeadless() { return headless; }
    public void setHeadless(boolean headless) { this.headless = headless; }
    public boolean isQuitOnClose() { return quitOnClose; }
    public void setQuitOnClose(boolean quitOnClose) { this.quitOnClose = quitOnClose; }
    public boolean isDevtools() { return devtools; }
    public void setDevtools(boolean devtools) { this.devtools = devtools; }
    public Boolean getApiExplorer() { return apiExplorer; }
    public void setApiExplorer(Boolean apiExplorer) { this.apiExplorer = apiExplorer; }
    public String getApiDocs() { return apiDocs; }
    public void setApiDocs(String apiDocs) { this.apiDocs = apiDocs; }
}
