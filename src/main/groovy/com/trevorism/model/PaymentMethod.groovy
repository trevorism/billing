package com.trevorism.model

import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema

/**
 * A payment method chosen by a customer or vendor. A single model carries the provider discriminator and the
 * union of provider-specific routing fields (most are null for any given method): crypto rails use
 * {@code address} (+ XRP {@code destinationTag}); Stripe uses the Stripe identifiers. One model means one
 * datastore kind, one repository, and one CRUD endpoint.
 */
@Introspected
@Schema(description = "A payment method owned by a customer or vendor")
class PaymentMethod {

    @Schema(description = "Unique identifier, assigned by the datastore on create")
    String id
    @Schema(description = "Provider that owns this method", example = "xrp", allowableValues = ["stripe", "xrp", "cardano"])
    String provider
    @Schema(description = "Id of the customer or vendor that owns this method")
    String ownerId

    @Schema(description = "On-chain address: payout target (vendor) or deposit address (customer) — crypto rails")
    String address
    @Schema(description = "XRP destination tag used to attribute inbound deposits to this owner")
    Long destinationTag
    @Schema(description = "Stripe customer id, used when charging this owner")
    String stripeCustomerId
    @Schema(description = "Stripe connected account id, used when paying this owner out")
    String stripeAccountId

    @Schema(description = "Owning tenant guid (empty for the default/single tenant)")
    String tenant
    @Schema(description = "When this payment method was created")
    Date dateCreated
    @Schema(description = "When this payment method was last updated")
    Date dateUpdated
}
