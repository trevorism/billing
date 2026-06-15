package com.trevorism.error

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Produces
import io.micronaut.http.server.exceptions.ExceptionHandler
import jakarta.inject.Singleton
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Catch-all for unmapped exceptions (e.g. a chain backend or ledger client failure while building a
 * transaction). Returns 500 but includes the message so the client can show why instead of a bare 500.
 * More specific handlers (IllegalArgument -> 400, IllegalState -> 409, UnsupportedOperation -> 501) take
 * precedence over this one.
 */
@Produces
@Singleton
@Requires(classes = [Exception, ExceptionHandler])
class GenericExceptionHandler implements ExceptionHandler<Exception, HttpResponse> {

    private static final Logger log = LoggerFactory.getLogger(GenericExceptionHandler)

    @Override
    HttpResponse handle(HttpRequest request, Exception exception) {
        log.error("Unhandled error on {} {}", request.method, request.path, exception)
        String message = exception.message ?: exception.class.simpleName
        return HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).body([status: 500, message: message])
    }
}
