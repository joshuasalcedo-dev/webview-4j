package io.joshuasalcedo.webview4j.spring;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import io.joshuasalcedo.webview4j.DevLogger;

/**
 * The calls the app makes with RestClient and RestTemplate (and Spring's HTTP service clients, made from them), to
 * the dev logger: method, URL, status, time, headers and text bodies. Streams (server-sent events, NDJSON) are
 * not read, so they still stream. Secrets in headers (Authorization, cookies) are hidden: the entries are kept in
 * a file too.
 */
final class DevLogHttp implements ClientHttpRequestInterceptor {

    /** Headers whose values are not shown. */
    static final Set<String> SECRET = Set.of("authorization", "proxy-authorization", "cookie", "set-cookie", "x-api-key");
    /** Answers longer than this (by Content-Length) are not read for the logger: it would cut them anyway. */
    static final long MAX_READ = 1024 * 1024;

    private final DevLogSink sink;

    DevLogHttp(DevLogSink sink) {
        this.sink = sink;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        long time = System.currentTimeMillis();
        long start = System.nanoTime();
        String method = request.getMethod().name();
        String url = request.getURI().toString();
        Map<String, String> requestHeaders = headers(request.getHeaders());
        String requestBody = text(request.getHeaders().getContentType(), body);
        ClientHttpResponse response;
        try {
            response = execution.execute(request, body);
        } catch (IOException | RuntimeException e) {
            sink.add(DevLogger.javaCall(time, method, url, 0, millis(start), requestHeaders, requestBody, Map.of(), null, why(e)));
            throw e;
        }
        HttpHeaders answered = response.getHeaders();
        int status = response.getStatusCode().value();
        if (!readable(answered)) {
            sink.add(DevLogger.javaCall(time, method, url, status, millis(start), requestHeaders, requestBody, headers(answered), null, null));
            return response;
        }
        byte[] read;
        try (InputStream in = response.getBody()) {
            read = in.readAllBytes();
        } catch (IOException e) {
            sink.add(DevLogger.javaCall(time, method, url, status, millis(start), requestHeaders, requestBody, headers(answered), null, why(e)));
            throw e;
        }
        sink.add(DevLogger.javaCall(time, method, url, status, millis(start), requestHeaders, requestBody, headers(answered),
                text(answered.getContentType(), read), null));
        return new Read(response, read);
    }

    /** Whether the answer is read for the logger: text of a known, small enough length, or JSON/text of no length. */
    static boolean readable(HttpHeaders headers) {
        MediaType type = headers.getContentType();
        if (type == null || !textual(type)) return false;
        String subtype = type.getSubtype().toLowerCase();
        if (subtype.equals("event-stream") || subtype.contains("ndjson") || subtype.equals("stream+json")) return false;
        long length = headers.getContentLength();
        return length <= MAX_READ;
    }

    static boolean textual(MediaType type) {
        String subtype = type.getSubtype().toLowerCase();
        return type.getType().equalsIgnoreCase("text") || subtype.equals("json") || subtype.endsWith("+json")
                || subtype.equals("xml") || subtype.endsWith("+xml") || subtype.equals("x-www-form-urlencoded")
                || subtype.equals("javascript") || subtype.equals("graphql") || subtype.contains("ndjson");
    }

    /** A body as text when it is text (or of no type, and not empty); null otherwise ("not read"). */
    static String text(MediaType type, byte[] body) {
        if (body == null || body.length == 0) return null;
        if (type != null && !textual(type)) return null;
        Charset charset = type != null && type.getCharset() != null ? type.getCharset() : StandardCharsets.UTF_8;
        return new String(body, charset);
    }

    static Map<String, String> headers(HttpHeaders headers) {
        Map<String, String> out = new LinkedHashMap<>();
        headers.forEach((name, values) -> out.put(name.toLowerCase(),
                SECRET.contains(name.toLowerCase()) ? "(hidden)" : String.join(", ", values)));
        return out;
    }

    static double millis(long start) {
        return (System.nanoTime() - start) / 1_000_000.0;
    }

    static String why(Throwable e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    /** The answer, its body already read: given again to whoever asked. */
    private record Read(ClientHttpResponse response, byte[] body) implements ClientHttpResponse {
        @Override public HttpStatusCode getStatusCode() throws IOException { return response.getStatusCode(); }
        @Override public String getStatusText() throws IOException { return response.getStatusText(); }
        @Override public HttpHeaders getHeaders() { return response.getHeaders(); }
        @Override public InputStream getBody() { return new ByteArrayInputStream(body); }
        @Override public void close() { response.close(); }
    }
}
