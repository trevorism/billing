package com.trevorism.controller

import com.trevorism.model.ConfirmReceiveRequest
import com.trevorism.model.PaymentRequest
import com.trevorism.model.PaymentResult
import com.trevorism.model.SendPrepareRequest
import com.trevorism.model.SignedSendRequest
import com.trevorism.model.StripeConfirmRequest
import com.trevorism.model.Transaction
import com.trevorism.model.UnsignedTransaction
import com.trevorism.model.WitnessSubmitRequest
import com.trevorism.secure.Roles
import com.trevorism.secure.Secure
import com.trevorism.security.TenantResolver
import com.trevorism.service.PaymentService
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.security.authentication.Authentication
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Post
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Controller("/api/payment")
class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController)

    private final PaymentService paymentService

    PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService
    }

    @Tag(name = "Payment Operations")
    @Operation(summary = "Phase 1 of a non-custodial send: build an unsigned transaction for the client to sign")
    @Secure(Roles.USER)
    @Post(value = "/send/prepare", produces = MediaType.APPLICATION_JSON, consumes = MediaType.APPLICATION_JSON)
    HttpResponse<UnsignedTransaction> prepareSend(@Body SendPrepareRequest request, @Nullable Authentication authentication) {
        try {
            return HttpResponse.ok(paymentService.prepareSend(request, TenantResolver.resolve(authentication)))
        } catch (UnsupportedOperationException e) {
            log.warn("Send not supported for payment method {}: {}", request.paymentMethodId, e.message)
            return HttpResponse.status(HttpStatus.NOT_IMPLEMENTED)
        }
    }

    @Tag(name = "Payment Operations")
    @Operation(summary = "Phase 2 of a non-custodial send: broadcast the client-signed transaction")
    @Secure(Roles.USER)
    @Post(value = "/send/submit", produces = MediaType.APPLICATION_JSON, consumes = MediaType.APPLICATION_JSON)
    PaymentResult submitSend(@Body SignedSendRequest request) {
        return paymentService.submitSend(request)
    }

    @Tag(name = "Payment Operations")
    @Operation(summary = "Phase 2 (CIP-30 wallets): submit the wallet witness; the API assembles and broadcasts")
    @Secure(Roles.USER)
    @Post(value = "/send/submit-witness", produces = MediaType.APPLICATION_JSON, consumes = MediaType.APPLICATION_JSON)
    PaymentResult submitSendWitness(@Body WitnessSubmitRequest request) {
        return paymentService.submitSendWitness(request)
    }

    @Tag(name = "Payment Operations")
    @Operation(summary = "Receive money from a customer via the requested payment method")
    @Secure(Roles.USER)
    @Post(value = "/receive", produces = MediaType.APPLICATION_JSON, consumes = MediaType.APPLICATION_JSON)
    HttpResponse<PaymentResult> receive(@Body PaymentRequest request, @Nullable Authentication authentication) {
        try {
            return HttpResponse.ok(paymentService.receive(request, TenantResolver.resolve(authentication)))
        } catch (UnsupportedOperationException e) {
            log.warn("Receive not supported for payment method {}: {}", request.paymentMethodId, e.message)
            return HttpResponse.status(HttpStatus.NOT_IMPLEMENTED)
        }
    }

    @Tag(name = "Payment Operations")
    @Operation(summary = "Confirm a Stripe receive on Checkout completion (called by the trevorism Stripe service)")
    @Secure(value = Roles.SYSTEM, allowInternal = true)
    @Post(value = "/stripe/confirm", produces = MediaType.APPLICATION_JSON, consumes = MediaType.APPLICATION_JSON)
    PaymentResult confirmStripeReceive(@Body StripeConfirmRequest request) {
        return paymentService.confirmStripeReceive(request.sessionId)
    }

    @Tag(name = "Payment Operations")
    @Operation(summary = "Confirm a receive by verifying the customer's on-chain deposit transaction")
    @Secure(Roles.USER)
    @Post(value = "/{id}/confirm-receive", produces = MediaType.APPLICATION_JSON, consumes = MediaType.APPLICATION_JSON)
    PaymentResult confirmReceive(@PathVariable String id, @Body ConfirmReceiveRequest request) {
        return paymentService.confirmReceive(id, request.depositTransaction)
    }

    @Tag(name = "Payment Operations")
    @Operation(summary = "Refresh a transaction's status from the chain (SUBMITTED -> CONFIRMED/FAILED)")
    @Secure(Roles.USER)
    @Post(value = "/{id}/refresh", produces = MediaType.APPLICATION_JSON)
    PaymentResult refresh(@PathVariable String id) {
        return paymentService.refresh(id)
    }

    @Tag(name = "Payment Operations")
    @Operation(summary = "List all recorded transactions")
    @Secure(Roles.USER)
    @Get(produces = MediaType.APPLICATION_JSON)
    List<Transaction> list() {
        return paymentService.getAllTransactions()
    }

    @Tag(name = "Payment Operations")
    @Operation(summary = "Get a transaction by id")
    @Secure(Roles.USER)
    @Get(value = "/{id}", produces = MediaType.APPLICATION_JSON)
    Transaction get(@PathVariable String id) {
        return paymentService.getTransaction(id)
    }
}
