package com.trevorism.controller

import com.trevorism.data.Repository
import com.trevorism.model.PaymentMethod
import com.trevorism.model.PaymentRequest
import com.trevorism.model.PaymentResult
import com.trevorism.payment.PaymentProvider
import com.trevorism.payment.PaymentProviderRegistry
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertThrows

class PaymentMethodControllerTest {

    private PaymentProviderRegistry registryWith(String... names) {
        return new PaymentProviderRegistry(names.collect { n ->
            [getName: { -> n }, receiveMoney: { PaymentRequest r, PaymentMethod m -> null }] as PaymentProvider
        })
    }

    @Test
    void testCreateStampsTenantDateAndPersists() {
        PaymentMethod captured = null
        Repository<PaymentMethod> repo = [create: { PaymentMethod m -> captured = m; m.id = "pm1"; return m }] as Repository
        PaymentMethodController controller = new PaymentMethodController(repo, registryWith("xrp"))

        PaymentMethod result = controller.create(new PaymentMethod(provider: "xrp", ownerId: "v1", address: "rDest"), null)

        assert result.id == "pm1"
        assert captured.dateCreated != null
        assert captured.tenant == ""       // single-tenant default when unauthenticated
        assert captured.provider == "xrp"
    }

    @Test
    void testCreateCanonicalizesProviderToLowercase() {
        PaymentMethod captured = null
        Repository<PaymentMethod> repo = [create: { PaymentMethod m -> captured = m; return m }] as Repository
        PaymentMethodController controller = new PaymentMethodController(repo, registryWith("xrp"))

        controller.create(new PaymentMethod(provider: "XRP", ownerId: "v1", address: "rDest"), null)

        assert captured.provider == "xrp"   // stored canonical so routing + requireProvider always agree
    }

    @Test
    void testCreateRejectsUnknownProvider() {
        Repository<PaymentMethod> repo = [create: { PaymentMethod m -> m }] as Repository
        PaymentMethodController controller = new PaymentMethodController(repo, registryWith("xrp", "cardano", "stripe"))

        assertThrows(IllegalArgumentException) {
            controller.create(new PaymentMethod(provider: "dogecoin", ownerId: "v1"), null)
        }
    }

    @Test
    void testUpdatePreservesImmutableIdentityFields() {
        PaymentMethod stored = new PaymentMethod(id: "pm1", provider: "xrp", ownerId: "v1", tenant: "tenant-a", address: "rOld", dateCreated: new Date(0))
        PaymentMethod saved = null
        Repository<PaymentMethod> repo = [
                get   : { String id -> stored },
                update: { String id, PaymentMethod m -> saved = m; return m }
        ] as Repository
        PaymentMethodController controller = new PaymentMethodController(repo, registryWith("xrp", "cardano"))

        // Malicious/buggy body tries to flip provider, owner, tenant; only the routing address should change.
        controller.update("pm1", new PaymentMethod(provider: "cardano", ownerId: "attacker", tenant: "tenant-b", address: "rNew"))

        assert saved.address == "rNew"          // routing field updated
        assert saved.provider == "xrp"          // immutable
        assert saved.ownerId == "v1"            // immutable
        assert saved.tenant == "tenant-a"       // immutable
        assert saved.dateUpdated != null
    }

    @Test
    void testListAndGetDelegateToRepository() {
        Repository<PaymentMethod> repo = [
                list: { -> [new PaymentMethod(id: "pm1", provider: "xrp")] },
                get : { String id -> new PaymentMethod(id: id, provider: "cardano") }
        ] as Repository
        PaymentMethodController controller = new PaymentMethodController(repo, registryWith("xrp"))

        assert controller.list().size() == 1
        assert controller.get("pm9").id == "pm9"
    }
}
