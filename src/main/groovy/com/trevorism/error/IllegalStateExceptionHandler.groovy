package com.trevorism.error

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Produces
import io.micronaut.http.server.exceptions.ExceptionHandler
import jakarta.inject.Singleton

/**
 * Conflicting state (e.g. a prepared transaction that has expired) -> 409 instead of a generic 500.
 */
@Produces
@Singleton
@Requires(classes = [IllegalStateException, ExceptionHandler])
class IllegalStateExceptionHandler implements ExceptionHandler<IllegalStateException, HttpResponse> {

    @Override
    HttpResponse handle(HttpRequest request, IllegalStateException exception) {
        return HttpResponse.status(HttpStatus.CONFLICT).body([status: 409, message: exception.message])
    }
}
