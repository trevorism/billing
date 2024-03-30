package com.trevorism.controller

import com.trevorism.model.Payment
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Controller("/api/payment")
class SendPaymentController {

    @Tag(name = "Root Operations")
    @Operation(summary = "Requests a payment from the sender to the recipient")
    @Post(produces = MediaType.APPLICATION_JSON, consumes = MediaType.APPLICATION_JSON)
    Payment sendPayment(@Body Payment payment) {
        payment
    }
}
