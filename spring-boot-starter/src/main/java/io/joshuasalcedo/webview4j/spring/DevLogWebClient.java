package io.joshuasalcedo.webview4j.spring;

import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;

import reactor.core.publisher.Mono;

import io.joshuasalcedo.webview4j.DevLogger;

/**
 * The calls the app makes with WebClient, to the dev logger: method, URL, status, the time to the answer's headers,
 * and the headers. Not the bodies: they are streams, read by the app as they come.
 */
final class DevLogWebClient implements ExchangeFilterFunction {

    private final DevLogSink sink;

    DevLogWebClient(DevLogSink sink) {
        this.sink = sink;
    }

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        long time = System.currentTimeMillis();
        long start = System.nanoTime();
        String method = request.method().name();
        String url = request.url().toString();
        return next.exchange(request)
                .doOnNext(response -> sink.add(DevLogger.javaCall(time, method, url, response.statusCode().value(),
                        DevLogHttp.millis(start), DevLogHttp.headers(request.headers()), null,
                        DevLogHttp.headers(response.headers().asHttpHeaders()), null, null)))
                .doOnError(e -> sink.add(DevLogger.javaCall(time, method, url, 0, DevLogHttp.millis(start),
                        DevLogHttp.headers(request.headers()), null, java.util.Map.of(), null, DevLogHttp.why(e))));
    }
}
