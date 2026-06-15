package com.trevorism.controller

import com.trevorism.data.Repository
import com.trevorism.model.Vendor
import com.trevorism.secure.Roles
import com.trevorism.secure.Secure
import com.trevorism.security.TenantResolver
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.security.authentication.Authentication
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Delete
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Put
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Controller("/api/vendor")
class VendorController {

    private static final Logger log = LoggerFactory.getLogger(VendorController)

    private final Repository<Vendor> vendorRepository

    VendorController(Repository<Vendor> vendorRepository) {
        this.vendorRepository = vendorRepository
    }

    @Tag(name = "Vendor Operations")
    @Operation(summary = "List all vendors")
    @Secure(Roles.USER)
    @Get(produces = MediaType.APPLICATION_JSON)
    List<Vendor> list() {
        return vendorRepository.list()
    }

    @Tag(name = "Vendor Operations")
    @Operation(summary = "Get a vendor by id")
    @Secure(Roles.USER)
    @Get(value = "/{id}", produces = MediaType.APPLICATION_JSON)
    Vendor get(@PathVariable String id) {
        return vendorRepository.get(id)
    }

    @Tag(name = "Vendor Operations")
    @Operation(summary = "Create a vendor")
    @Secure(Roles.USER)
    @Post(produces = MediaType.APPLICATION_JSON, consumes = MediaType.APPLICATION_JSON)
    Vendor create(@Body Vendor vendor, @Nullable Authentication authentication) {
        if (vendor.dateCreated == null) {
            vendor.dateCreated = new Date()
        }
        vendor.tenant = TenantResolver.resolve(authentication)
        log.info("Creating vendor {}", vendor.name)
        return vendorRepository.create(vendor)
    }

    @Tag(name = "Vendor Operations")
    @Operation(summary = "Update a vendor")
    @Secure(Roles.USER)
    @Put(value = "/{id}", produces = MediaType.APPLICATION_JSON, consumes = MediaType.APPLICATION_JSON)
    Vendor update(@PathVariable String id, @Body Vendor vendor) {
        return vendorRepository.update(id, vendor)
    }

    @Tag(name = "Vendor Operations")
    @Operation(summary = "Delete a vendor")
    @Secure(Roles.USER)
    @Delete(value = "/{id}", produces = MediaType.APPLICATION_JSON)
    Vendor delete(@PathVariable String id) {
        log.info("Deleting vendor {}", id)
        return vendorRepository.delete(id)
    }
}
