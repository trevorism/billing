package com.trevorism.model

import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema

/**
 * A person or organization that money is paid out to. Provider specific routing details live in
 * {@link PaymentMethod} implementations owned by this vendor, not on this record.
 */
@Introspected
@Schema(description = "A vendor that money is sent to")
class Vendor {

    @Schema(description = "Unique identifier, assigned by the datastore on create")
    String id
    @Schema(description = "Display name of the vendor")
    String name
    @Schema(description = "Contact email address")
    String email
    @Schema(description = "Owning tenant guid (empty for the default/single tenant)")
    String tenant
    @Schema(description = "When this vendor record was created")
    Date dateCreated
}
