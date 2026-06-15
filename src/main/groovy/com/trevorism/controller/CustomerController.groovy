package com.trevorism.controller

import com.trevorism.data.Repository
import com.trevorism.model.Customer
import com.trevorism.secure.Roles
import com.trevorism.secure.Secure
import com.trevorism.security.TenantResolver
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.HttpResponse
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

@Controller("/api/customer")
class CustomerController {

    private static final Logger log = LoggerFactory.getLogger(CustomerController)

    private final Repository<Customer> customerRepository

    CustomerController(Repository<Customer> customerRepository) {
        this.customerRepository = customerRepository
    }

    @Tag(name = "Customer Operations")
    @Operation(summary = "List all customers")
    @Secure(Roles.USER)
    @Get(produces = MediaType.APPLICATION_JSON)
    List<Customer> list() {
        return customerRepository.list()
    }

    @Tag(name = "Customer Operations")
    @Operation(summary = "Get a customer by id")
    @Secure(Roles.USER)
    @Get(value = "/{id}", produces = MediaType.APPLICATION_JSON)
    Customer get(@PathVariable String id) {
        return customerRepository.get(id)
    }

    @Tag(name = "Customer Operations")
    @Operation(summary = "Create a customer")
    @Secure(Roles.USER)
    @Post(produces = MediaType.APPLICATION_JSON, consumes = MediaType.APPLICATION_JSON)
    Customer create(@Body Customer customer, @Nullable Authentication authentication) {
        if (customer.dateCreated == null) {
            customer.dateCreated = new Date()
        }
        customer.tenant = TenantResolver.resolve(authentication)
        log.info("Creating customer {}", customer.name)
        return customerRepository.create(customer)
    }

    @Tag(name = "Customer Operations")
    @Operation(summary = "Update a customer")
    @Secure(Roles.USER)
    @Put(value = "/{id}", produces = MediaType.APPLICATION_JSON, consumes = MediaType.APPLICATION_JSON)
    Customer update(@PathVariable String id, @Body Customer customer) {
        return customerRepository.update(id, customer)
    }

    @Tag(name = "Customer Operations")
    @Operation(summary = "Delete a customer")
    @Secure(Roles.USER)
    @Delete(value = "/{id}", produces = MediaType.APPLICATION_JSON)
    Customer delete(@PathVariable String id) {
        log.info("Deleting customer {}", id)
        return customerRepository.delete(id)
    }
}
