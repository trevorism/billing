package com.trevorism.controller

import com.trevorism.data.Repository
import com.trevorism.model.Vendor
import org.junit.jupiter.api.Test

class VendorControllerTest {

    @Test
    void testCreateAssignsDateCreated() {
        Vendor captured = null
        Repository<Vendor> repo = [create: { Vendor v -> captured = v; v.id = "1"; return v }] as Repository
        VendorController controller = new VendorController(repo)

        Vendor result = controller.create(new Vendor(name: "Globex", email: "ap@globex.com"), null)

        assert result.id == "1"
        assert captured.dateCreated != null
        assert captured.email == "ap@globex.com"
    }

    @Test
    void testListDelegatesToRepository() {
        Repository<Vendor> repo = [list: { -> [new Vendor(id: "1")] }] as Repository
        VendorController controller = new VendorController(repo)
        assert controller.list().size() == 1
    }

    @Test
    void testUpdateDelegatesToRepository() {
        Repository<Vendor> repo = [update: { String id, Vendor v -> v.id = id; return v }] as Repository
        VendorController controller = new VendorController(repo)
        assert controller.update("9", new Vendor(name: "Initech")).id == "9"
    }
}
