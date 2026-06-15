package com.trevorism.error

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Produces
import io.micronaut.http.server.exceptions.ExceptionHandler
import jakarta.inject.Singleton

/**
 * Unsupported capability (e.g. a non-custodial send for a provider that lacks it, or witness assembly on a
 * provider that does not support it) -> 501.
 */
@Produces
@Singleton
@Requires(classes = [UnsupportedOperationException, ExceptionHandler])
class UnsupportedOperationExceptionHandler implements ExceptionHandler<UnsupportedOperationException, HttpResponse> {

    @Override
    HttpResponse handle(HttpRequest request, UnsupportedOperationException exception) {
        return HttpResponse.status(HttpStatus.NOT_IMPLEMENTED).body([status: 501, message: exception.message])
    }
}
