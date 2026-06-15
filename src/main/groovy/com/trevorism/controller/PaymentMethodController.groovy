package com.trevorism.controller

import com.trevorism.data.Repository
import com.trevorism.model.PaymentMethod
import com.trevorism.payment.PaymentProviderRegistry
import com.trevorism.secure.Roles
import com.trevorism.secure.Secure
import com.trevorism.security.TenantResolver
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Delete
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Put
import io.micronaut.security.authentication.Authentication
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Controller("/api/payment-method")
class PaymentMethodController {

    private static final Logger log = LoggerFactory.getLogger(PaymentMethodController)

    private final Repository<PaymentMethod> repository
    private final PaymentProviderRegistry registry

    PaymentMethodController(Repository<PaymentMethod> repository, PaymentProviderRegistry registry) {
        this.repository = repository
        this.registry = registry
    }

    @Tag(name = "Payment Method Operations")
    @Operation(summary = "List all payment methods")
    @Secure(Roles.USER)
    @Get(produces = MediaType.APPLICATION_JSON)
    List<PaymentMethod> list() {
        return repository.list()
    }

    @Tag(name = "Payment Method Operations")
    @Operation(summary = "Get a payment method by id")
    @Secure(Roles.USER)
    @Get(value = "/{id}", produces = MediaType.APPLICATION_JSON)
    PaymentMethod get(@PathVariable String id) {
        return repository.get(id)
    }

    @Tag(name = "Payment Method Operations")
    @Operation(summary = "Create a payment method (its provider determines the rail)")
    @Secure(Roles.USER)
    @Post(produces = MediaType.APPLICATION_JSON, consumes = MediaType.APPLICATION_JSON)
    PaymentMethod create(@Body PaymentMethod paymentMethod, @Nullable Authentication authentication) {
        // Canonicalize to the registry's lowercase key so routing and the providers' own checks always agree.
        paymentMethod.provider = paymentMethod.provider?.toLowerCase()
        registry.get(paymentMethod.provider)   // reject unknown providers early
        if (paymentMethod.dateCreated == null) {
            paymentMethod.dateCreated = new Date()
        }
        paymentMethod.tenant = TenantResolver.resolve(authentication)
        log.info("Creating {} payment method for owner {}", paymentMethod.provider, paymentMethod.ownerId)
        return repository.create(paymentMethod)
    }

    @Tag(name = "Payment Method Operations")
    @Operation(summary = "Update a payment method's routing fields (identity, owner, provider and tenant are immutable)")
    @Secure(Roles.USER)
    @Put(value = "/{id}", produces = MediaType.APPLICATION_JSON, consumes = MediaType.APPLICATION_JSON)
    PaymentMethod update(@PathVariable String id, @Body PaymentMethod paymentMethod) {
        PaymentMethod existing = load(id)
        // Identity, ownership, provider and tenant are immutable; only the routing fields may change. This
        // prevents a PUT from flipping the rail, reassigning the owner, or clobbering the tenant.
        existing.address = paymentMethod.address
        existing.destinationTag = paymentMethod.destinationTag
        existing.stripeCustomerId = paymentMethod.stripeCustomerId
        existing.stripeAccountId = paymentMethod.stripeAccountId
        existing.dateUpdated = new Date()
        return repository.update(id, existing)
    }

    private PaymentMethod load(String id) {
        PaymentMethod existing = null
        try {
            existing = repository.get(id)
        } catch (Exception e) {
            log.warn("Failed to load payment method {}: {}", id, e.message)
        }
        if (!existing || !existing.id) {
            throw new IllegalArgumentException("Payment method not found: ${id}")
        }
        return existing
    }

    @Tag(name = "Payment Method Operations")
    @Operation(summary = "Delete a payment method")
    @Secure(Roles.USER)
    @Delete(value = "/{id}", produces = MediaType.APPLICATION_JSON)
    PaymentMethod delete(@PathVariable String id) {
        log.info("Deleting payment method {}", id)
        return repository.delete(id)
    }
}
