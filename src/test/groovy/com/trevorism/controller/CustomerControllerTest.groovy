package com.trevorism.controller

import com.trevorism.data.Repository
import com.trevorism.model.Customer
import org.junit.jupiter.api.Test

class CustomerControllerTest {

    @Test
    void testCreateAssignsDateCreated() {
        Customer captured = null
        Repository<Customer> repo = [create: { Customer c -> captured = c; c.id = "1"; return c }] as Repository
        CustomerController controller = new CustomerController(repo)

        Customer result = controller.create(new Customer(name: "Acme"), null)

        assert result.id == "1"
        assert captured.dateCreated != null
        assert captured.tenant == ""   // single-tenant: empty tenant guid when unauthenticated
    }

    @Test
    void testListDelegatesToRepository() {
        Repository<Customer> repo = [list: { -> [new Customer(id: "1"), new Customer(id: "2")] }] as Repository
        CustomerController controller = new CustomerController(repo)
        assert controller.list().size() == 2
    }

    @Test
    void testGetAndDeleteDelegateToRepository() {
        Repository<Customer> repo = [
                get   : { String id -> new Customer(id: id) },
                delete: { String id -> new Customer(id: id) }
        ] as Repository
        CustomerController controller = new CustomerController(repo)
        assert controller.get("42").id == "42"
        assert controller.delete("42").id == "42"
    }
}
