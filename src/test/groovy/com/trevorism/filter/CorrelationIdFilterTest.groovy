package com.trevorism.filter

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.filter.ServerFilterChain
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux

class CorrelationIdFilterTest {

    @Test
    void testGeneratesAndEchoesCorrelationId() {
        def downstream = HttpResponse.ok("pong")
        ServerFilterChain chain = [proceed: { req -> Flux.just(downstream) }] as ServerFilterChain
        def filter = new CorrelationIdFilter()

        def response = Flux.from(filter.doFilter(HttpRequest.GET("/api/ping"), chain)).blockFirst()

        assert response.headers.get(CorrelationIdFilter.HEADER) != null
    }

    @Test
    void testHonorsInboundCorrelationId() {
        def downstream = HttpResponse.ok("pong")
        ServerFilterChain chain = [proceed: { req -> Flux.just(downstream) }] as ServerFilterChain
        def request = HttpRequest.GET("/api/ping").header(CorrelationIdFilter.HEADER, "abc-123")

        def response = Flux.from(new CorrelationIdFilter().doFilter(request, chain)).blockFirst()

        assert response.headers.get(CorrelationIdFilter.HEADER) == "abc-123"
    }
}
