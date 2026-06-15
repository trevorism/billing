package com.trevorism.filter

import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.ResponseFilter
import io.micronaut.http.annotation.ServerFilter

@ServerFilter("/**")
class CacheControlFilter {

    private static final String IMMUTABLE = "public, max-age=31536000, immutable"
    private static final String NO_STORE = "no-store, no-cache, must-revalidate"

    @ResponseFilter
    void applyCacheControl(HttpRequest<?> request, MutableHttpResponse<?> response) {
        String path = request.path
        if (path.startsWith("/assets/")) {
            setCacheControl(response, IMMUTABLE)
        } else if (acceptsHtml(request)) {
            setCacheControl(response, NO_STORE)
            response.getHeaders().remove(HttpHeaders.LAST_MODIFIED)
            response.getHeaders().remove(HttpHeaders.ETAG)
        }
    }

    private static boolean acceptsHtml(HttpRequest<?> request) {
        request.getHeaders().accept().stream().anyMatch { it.name.contains(MediaType.TEXT_HTML) }
    }

    private static void setCacheControl(MutableHttpResponse<?> response, String value) {
        response.getHeaders().remove(HttpHeaders.CACHE_CONTROL)
        response.getHeaders().add(HttpHeaders.CACHE_CONTROL, value)
    }
}
