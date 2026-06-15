package com.trevorism.error

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Produces
import io.micronaut.http.server.exceptions.ExceptionHandler
import jakarta.inject.Singleton

/**
 * Bad client input (e.g. missing/invalid fields, wrong-network address, deposit mismatch) -> 400 instead of
 * a generic 500.
 */
@Produces
@Singleton
@Requires(classes = [IllegalArgumentException, ExceptionHandler])
class IllegalArgumentExceptionHandler implements ExceptionHandler<IllegalArgumentException, HttpResponse> {

    @Override
    HttpResponse handle(HttpRequest request, IllegalArgumentException exception) {
        return HttpResponse.badRequest([status: 400, message: exception.message])
    }
}
