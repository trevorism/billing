package com.trevorism.controller

import com.trevorism.data.Repository
import com.trevorism.model.PaymentMethod
import com.trevorism.model.PaymentRequest
import com.trevorism.model.PaymentResult
import com.trevorism.model.SendPrepareRequest
import com.trevorism.model.SignedSendRequest
import com.trevorism.model.Transaction
import com.trevorism.model.UnsignedTransaction
import com.trevorism.payment.LedgerSubmission
import com.trevorism.payment.PaymentProvider
import com.trevorism.payment.PaymentProviderRegistry
import com.trevorism.payment.PreparedTransfer
import com.trevorism.payment.SignableTransferProvider
import com.trevorism.payment.TransferContext
import com.trevorism.service.PaymentService
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import org.junit.jupiter.api.Test

class PaymentControllerTest {

    static class CryptoProvider implements PaymentProvider, SignableTransferProvider {
        @Override String getName() { return "xrp" }
        @Override PaymentResult receiveMoney(PaymentRequest r, PaymentMethod m) { return null }
        @Override PreparedTransfer prepareTransfer(TransferContext c) { return new PreparedTransfer("UNSIGNED", "XRPL_JSON", "sign") }
        @Override LedgerSubmission submitSignedTransfer(String u, String s) { return new LedgerSubmission("HASH", "SUBMITTED") }
    }

    private PaymentService serviceWith(PaymentProvider provider, PaymentMethod method, Transaction preparedTx) {
        Repository<PaymentMethod> methodRepo = [get: { String id -> method }] as Repository
        Repository<Transaction> txRepo = [
                create: { Transaction t -> t.id = "tx1"; return t },
                get   : { String id -> preparedTx },
                update: { String id, Transaction t -> t }
        ] as Repository
        return new PaymentService(new PaymentProviderRegistry([provider]), methodRepo, txRepo)
    }

    @Test
    void testPrepareSendReturnsUnsignedTransaction() {
        PaymentController controller = new PaymentController(
                serviceWith(new CryptoProvider(), new PaymentMethod(provider: "xrp", id: "pm1", ownerId: "v1", address: "rDest"), null))

        HttpResponse<UnsignedTransaction> response = controller.prepareSend(
                new SendPrepareRequest(senderAddress: "rSender", paymentMethodId: "pm1", amount: 5), null)

        assert response.status == HttpStatus.OK
        assert response.body().transactionId == "tx1"
        assert response.body().unsignedPayload == "UNSIGNED"
    }

    @Test
    void testPrepareSendUnsupportedReturns501() {
        PaymentProvider stripe = new PaymentProvider() {
            @Override String getName() { return "stripe" }
            @Override PaymentResult receiveMoney(PaymentRequest r, PaymentMethod m) { return null }
        }
        PaymentController controller = new PaymentController(
                serviceWith(stripe, new PaymentMethod(provider: "stripe", id: "pm1", ownerId: "v1"), null))

        HttpResponse<UnsignedTransaction> response = controller.prepareSend(
                new SendPrepareRequest(senderAddress: "x", paymentMethodId: "pm1", amount: 5), null)

        assert response.status == HttpStatus.NOT_IMPLEMENTED
    }

    @Test
    void testSubmitSendBroadcasts() {
        Transaction prepared = new Transaction(id: "tx1", provider: "xrp", status: "PREPARED")
        PaymentController controller = new PaymentController(serviceWith(new CryptoProvider(), null, prepared))

        PaymentResult result = controller.submitSend(new SignedSendRequest(transactionId: "tx1", signedTransaction: "BLOB"))

        assert result.status == "SUBMITTED"
        assert result.externalReference == "HASH"
    }
}
