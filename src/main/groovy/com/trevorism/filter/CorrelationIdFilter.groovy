package com.trevorism.filter

import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Filter
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import org.reactivestreams.Publisher
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux

/**
 * Assigns a correlation id to every request (honoring an inbound X-Correlation-Id), echoes it on the
 * response, and logs a single access line per request for traceability. No request bodies or secrets logged.
 */
@Filter("/**")
class CorrelationIdFilter implements HttpServerFilter {

    static final String HEADER = "X-Correlation-Id"
    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter)

    @Override
    Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        String correlationId = request.headers.get(HEADER) ?: UUID.randomUUID().toString()
        return Flux.from(chain.proceed(request)).doOnNext { response ->
            response.headers.add(HEADER, correlationId)
            log.info("{} {} -> {} [{}]", request.method, request.path, response.status.code, correlationId)
        }
    }
}
