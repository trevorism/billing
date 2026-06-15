package com.trevorism.model

import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema

/**
 * A persisted record of a money movement, created for every send or receive operation.
 */
@Introspected
@Schema(description = "An audit record of a money movement")
class Transaction {

    @Schema(description = "Unique identifier, assigned by the datastore on create")
    String id
    @Schema(description = "Direction of money movement", example = "SEND", allowableValues = ["SEND", "RECEIVE"])
    String type
    @Schema(description = "Provider that handled the movement")
    String provider
    @Schema(description = "Id of the payment method used")
    String paymentMethodId
    @Schema(description = "Id of the customer or vendor that owns the payment method")
    String ownerId
    @Schema(description = "Owning tenant guid (empty for the default/single tenant)")
    String tenant
    @Schema(description = "The client wallet address a send was prepared from (non-custodial send)")
    String senderAddress
    @Schema(description = "Recipient address snapshot at receive time (deposit verification uses this, not the mutable method)")
    String recipientAddress
    @Schema(description = "Recipient XRP destination tag snapshot at receive time")
    Long recipientTag
    @Schema(description = "The unsigned transaction built at prepare time, used to assemble a client witness at submit")
    String unsignedPayload
    @Schema(description = "When a PREPARED send expires and may no longer be submitted")
    Date expiresAt
    @Schema(description = "Amount of money moved")
    BigDecimal amount
    @Schema(description = "Currency code")
    String currency
    @Schema(description = "Status reported by the provider")
    String status
    @Schema(description = "Provider specific reference (Stripe session id or XRPL transaction hash)")
    String externalReference
    @Schema(description = "When this transaction record was created")
    Date dateCreated
    @Schema(description = "When this transaction was last updated (status change)")
    Date dateUpdated
}
