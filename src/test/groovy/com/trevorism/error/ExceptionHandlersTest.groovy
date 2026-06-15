package com.trevorism.error

import io.micronaut.http.HttpStatus
import org.junit.jupiter.api.Test

class ExceptionHandlersTest {

    @Test
    void testIllegalArgumentMapsTo400() {
        def response = new IllegalArgumentExceptionHandler().handle(null, new IllegalArgumentException("bad input"))
        assert response.status == HttpStatus.BAD_REQUEST
        assert response.body().message == "bad input"
    }

    @Test
    void testIllegalStateMapsTo409() {
        def response = new IllegalStateExceptionHandler().handle(null, new IllegalStateException("expired"))
        assert response.status == HttpStatus.CONFLICT
        assert response.body().message == "expired"
    }

    @Test
    void testUnsupportedOperationMapsTo501() {
        def response = new UnsupportedOperationExceptionHandler().handle(null, new UnsupportedOperationException("nope"))
        assert response.status == HttpStatus.NOT_IMPLEMENTED
        assert response.body().message == "nope"
    }
}
