package com.trevorism.model

import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema

/**
 * A person or organization that pays money into the platform. Provider specific routing details live in
 * {@link PaymentMethod} implementations owned by this customer, not on this record.
 */
@Introspected
@Schema(description = "A customer that money is received from")
class Customer {

    @Schema(description = "Unique identifier, assigned by the datastore on create")
    String id
    @Schema(description = "Display name of the customer")
    String name
    @Schema(description = "Contact email address")
    String email
    @Schema(description = "Owning tenant guid (empty for the default/single tenant)")
    String tenant
    @Schema(description = "When this customer record was created")
    Date dateCreated
}
