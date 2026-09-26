package com.trevorism.service

import com.trevorism.data.Repository
import com.trevorism.model.PaymentMethod
import com.trevorism.model.PaymentRequest
import com.trevorism.model.PaymentResult
import com.trevorism.model.SendPrepareRequest
import com.trevorism.model.SignedSendRequest
import com.trevorism.model.Transaction
import com.trevorism.model.UnsignedTransaction
import com.trevorism.payment.ConfirmableProvider
import com.trevorism.payment.DepositVerifyingProvider
import com.trevorism.payment.LedgerSubmission
import com.trevorism.payment.PaymentProvider
import com.trevorism.payment.PaymentProviderRegistry
import com.trevorism.model.WitnessSubmitRequest
import com.trevorism.payment.PreparedTransfer
import com.trevorism.payment.SignableTransferProvider
import com.trevorism.payment.TransferContext
import com.trevorism.payment.WitnessAssemblingProvider
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertThrows

class PaymentServiceTest {

    /** A crypto-style provider that can both receive and participate in non-custodial sends. */
    private PaymentProvider signableProvider(String name, PreparedTransfer prepared, LedgerSubmission submission, PaymentResult received) {
        return new SignableProvider(name: name, prepared: prepared, submission: submission, received: received)
    }

    static class SignableProvider implements PaymentProvider, SignableTransferProvider, WitnessAssemblingProvider, ConfirmableProvider, DepositVerifyingProvider {
        String name
        PreparedTransfer prepared
        LedgerSubmission submission
        PaymentResult received
        String checkStatusResult
        boolean verifyDepositResult
        boolean requiresDestinationTag
        TransferContext capturedContext
        String capturedSigned
        String capturedUnsigned
        String capturedWitness
        String capturedReference
        String capturedDepositReference
        Date capturedNotBefore

        @Override String getName() { return name }
        @Override PaymentResult receiveMoney(PaymentRequest request, PaymentMethod method) { return received }
        @Override PreparedTransfer prepareTransfer(TransferContext context) { capturedContext = context; return prepared }
        @Override LedgerSubmission submitSignedTransfer(String unsignedPayload, String signedTransaction) { capturedUnsigned = unsignedPayload; capturedSigned = signedTransaction; return submission }
        @Override LedgerSubmission submitSignedWitness(String unsignedPayload, String witness) { capturedUnsigned = unsignedPayload; capturedWitness = witness; return submission }
        @Override String checkStatus(String reference) { capturedReference = reference; return checkStatusResult }
        @Override boolean verifyDeposit(String reference, String expectedAddress, BigDecimal expectedAmount, Long expectedDestinationTag, Date notBefore) { capturedDepositReference = reference; capturedNotBefore = notBefore; return verifyDepositResult }
        @Override boolean requiresDestinationTag() { return requiresDestinationTag }
    }

    private Repository<PaymentMethod> methodRepo(PaymentMethod method) {
        return [get: { String id -> method }] as Repository
    }

    @Test
    void testPrepareSendBuildsUnsignedAndRecordsPrepared() {
        Transaction created = null
        Repository<Transaction> txRepo = [create: { Transaction t -> created = t; t.id = "tx1"; return t }] as Repository

        PreparedTransfer prepared = new PreparedTransfer("UNSIGNED_PAYLOAD", "XRPL_JSON", "sign me")
        SignableProvider provider = signableProvider("xrp", prepared, null, null)
        Repository<PaymentMethod> methodMethods = methodRepo(new PaymentMethod(provider: "xrp", id: "pm1", ownerId: "v1", address: "rDest"))
        PaymentService service = new PaymentService(new PaymentProviderRegistry([provider]), methodMethods, txRepo)

        UnsignedTransaction result = service.prepareSend(new SendPrepareRequest(senderAddress: "rSender", paymentMethodId: "pm1", amount: 5, currency: "XRP"), "tenant-x")

        assert provider.capturedContext.senderAddress == "rSender"
        assert provider.capturedContext.destinationAddress == "rDest"
        assert result.transactionId == "tx1"
        assert result.provider == "xrp"
        assert result.unsignedPayload == "UNSIGNED_PAYLOAD"
        assert created.type == "SEND"
        assert created.status == "PREPARED"
        assert created.senderAddress == "rSender"
        assert created.ownerId == "v1"
        assert created.tenant == "tenant-x"
        assert created.expiresAt != null && created.expiresAt.after(new Date())
    }

    @Test
    void testSubmitIsIdempotentForAlreadySubmittedTransaction() {
        // A re-submit of an already-broadcast transaction returns the recorded result and does NOT re-broadcast.
        Transaction alreadySubmitted = new Transaction(id: "tx1", provider: "xrp", status: "SUBMITTED", externalReference: "HASH1")
        Repository<Transaction> txRepo = [get: { String id -> alreadySubmitted }] as Repository
        SignableProvider provider = signableProvider("xrp", null, new LedgerSubmission("HASH2", "SUBMITTED"), null)
        PaymentService service = new PaymentService(new PaymentProviderRegistry([provider]), methodRepo(null), txRepo)

        PaymentResult result = service.submitSend(new SignedSendRequest(transactionId: "tx1", signedTransaction: "BLOB"))

        assert result.externalReference == "HASH1"     // recorded result, not a new broadcast
        assert provider.capturedSigned == null         // provider was never called
    }

    @Test
    void testSubmitRejectsExpiredPreparedTransaction() {
        Transaction expired = new Transaction(id: "tx1", provider: "xrp", status: "PREPARED",
                expiresAt: new Date(System.currentTimeMillis() - 60_000L))
        Transaction updated = null
        Repository<Transaction> txRepo = [
                get   : { String id -> expired },
                update: { String id, Transaction t -> updated = t; return t }
        ] as Repository
        PaymentService service = new PaymentService(new PaymentProviderRegistry([signableProvider("xrp", null, null, null)]), methodRepo(null), txRepo)

        assertThrows(IllegalStateException) {
            service.submitSend(new SignedSendRequest(transactionId: "tx1", signedTransaction: "BLOB"))
        }
        assert updated.status == "EXPIRED"
    }

    @Test
    void testSubmitSendBroadcastsAndFinalizes() {
        Transaction stored = new Transaction(id: "tx1", provider: "cardano", status: "PREPARED")
        Transaction updated = null
        Repository<Transaction> txRepo = [
                get   : { String id -> stored },
                update: { String id, Transaction t -> updated = t; return t }
        ] as Repository

        LedgerSubmission submission = new LedgerSubmission("CARDANO_TX_HASH", "SUBMITTED")
        SignableProvider provider = signableProvider("cardano", null, submission, null)
        PaymentService service = new PaymentService(new PaymentProviderRegistry([provider]), methodRepo(null), txRepo)

        PaymentResult result = service.submitSend(new SignedSendRequest(transactionId: "tx1", signedTransaction: "SIGNED_CBOR"))

        assert provider.capturedSigned == "SIGNED_CBOR"
        assert result.status == "SUBMITTED"
        assert result.externalReference == "CARDANO_TX_HASH"
        assert updated.status == "SUBMITTED"
        assert updated.externalReference == "CARDANO_TX_HASH"
    }

    @Test
    void testSubmitSendWitnessAssemblesAndFinalizes() {
        Transaction stored = new Transaction(id: "tx1", provider: "cardano", status: "PREPARED", unsignedPayload: "UNSIGNED_CBOR")
        Transaction updated = null
        Repository<Transaction> txRepo = [
                get   : { String id -> stored },
                update: { String id, Transaction t -> updated = t; return t }
        ] as Repository

        LedgerSubmission submission = new LedgerSubmission("CARDANO_TX_HASH", "SUBMITTED")
        SignableProvider provider = signableProvider("cardano", null, submission, null)
        PaymentService service = new PaymentService(new PaymentProviderRegistry([provider]), methodRepo(null), txRepo)

        PaymentResult result = service.submitSendWitness(new WitnessSubmitRequest(transactionId: "tx1", witness: "WITNESS_CBOR"))

        assert provider.capturedUnsigned == "UNSIGNED_CBOR"
        assert provider.capturedWitness == "WITNESS_CBOR"
        assert result.status == "SUBMITTED"
        assert result.externalReference == "CARDANO_TX_HASH"
        assert updated.externalReference == "CARDANO_TX_HASH"
    }

    @Test
    void testPrepareSendEnforcesTransactionLimit() {
        Repository<PaymentMethod> methodMethods = methodRepo(new PaymentMethod(provider: "xrp", id: "pm1", ownerId: "v1", address: "rDest"))
        ComplianceService compliance = new ComplianceService([xrp: new BigDecimal("100")], [] as Set)
        PaymentService service = new PaymentService(new PaymentProviderRegistry([signableProvider("xrp", new PreparedTransfer("U", "F", "i"), null, null)]),
                methodMethods, [create: { it }] as Repository, 15, compliance)

        assertThrows(IllegalArgumentException) {
            service.prepareSend(new SendPrepareRequest(senderAddress: "rSender", paymentMethodId: "pm1", amount: new BigDecimal("500")), "")
        }
    }

    @Test
    void testPrepareSendScreensDestinationAddress() {
        Repository<PaymentMethod> methodMethods = methodRepo(new PaymentMethod(provider: "xrp", id: "pm1", ownerId: "v1", address: "rBlocked"))
        ComplianceService compliance = new ComplianceService([:], ["rblocked"] as Set)
        PaymentService service = new PaymentService(new PaymentProviderRegistry([signableProvider("xrp", new PreparedTransfer("U", "F", "i"), null, null)]),
                methodMethods, [create: { it }] as Repository, 15, compliance)

        assertThrows(IllegalArgumentException) {
            service.prepareSend(new SendPrepareRequest(senderAddress: "rSender", paymentMethodId: "pm1", amount: "5"), "")
        }
    }

    @Test
    void testPrepareSendForNonSignableProviderThrowsUnsupported() {
        // Stripe is not a SignableTransferProvider -> non-custodial send is unsupported.
        PaymentProvider stripe = new PaymentProvider() {
            @Override String getName() { return "stripe" }
            @Override PaymentResult receiveMoney(PaymentRequest request, PaymentMethod method) { return null }
        }
        Repository<PaymentMethod> methodMethods = methodRepo(new PaymentMethod(provider: "stripe", id: "pm1", ownerId: "v1"))
        PaymentService service = new PaymentService(new PaymentProviderRegistry([stripe]), methodMethods, [:] as Repository)

        assertThrows(UnsupportedOperationException) {
            service.prepareSend(new SendPrepareRequest(senderAddress: "x", paymentMethodId: "pm1", amount: 1), "")
        }
    }

    @Test
    void testPrepareSendWithoutSenderThrows() {
        PaymentService service = new PaymentService(new PaymentProviderRegistry([signableProvider("xrp", null, null, null)]), methodRepo(null), [:] as Repository)
        assertThrows(IllegalArgumentException) { service.prepareSend(new SendPrepareRequest(paymentMethodId: "pm1", amount: 1), "") }
    }

    @Test
    void testPrepareSendRejectsNonPositiveAmount() {
        PaymentService service = new PaymentService(new PaymentProviderRegistry([signableProvider("xrp", null, null, null)]), methodRepo(null), [:] as Repository)
        assertThrows(IllegalArgumentException) {
            service.prepareSend(new SendPrepareRequest(senderAddress: "rSender", paymentMethodId: "pm1", amount: 0), "")
        }
        assertThrows(IllegalArgumentException) {
            service.prepareSend(new SendPrepareRequest(senderAddress: "rSender", paymentMethodId: "pm1", amount: new BigDecimal("-1")), "")
        }
    }

    @Test
    void testReceiveResolvesMethodAndRecordsTransaction() {
        Transaction created = null
        Repository<Transaction> txRepo = [create: { Transaction t -> created = t; return t }] as Repository

        PaymentResult received = new PaymentResult("cardano", "PENDING")
        SignableProvider provider = signableProvider("cardano", null, null, received)
        Repository<PaymentMethod> methodMethods = methodRepo(new PaymentMethod(provider: "cardano", id: "pm1", ownerId: "c1", address: "addr_test1"))
        PaymentService service = new PaymentService(new PaymentProviderRegistry([provider]), methodMethods, txRepo)

        PaymentResult result = service.receive(new PaymentRequest(paymentMethodId: "pm1", amount: 5, currency: "ADA"), "tenant-y")

        assert result.status == "PENDING"
        assert created.type == "RECEIVE"
        assert created.ownerId == "c1"
        assert created.tenant == "tenant-y"
    }

    @Test
    void testRefreshUpdatesStatusFromChain() {
        Transaction stored = new Transaction(id: "tx1", provider: "xrp", status: "SUBMITTED", externalReference: "HASH1")
        Transaction updated = null
        Repository<Transaction> txRepo = [
                get   : { String id -> stored },
                update: { String id, Transaction t -> updated = t; return t }
        ] as Repository
        SignableProvider provider = signableProvider("xrp", null, null, null)
        provider.checkStatusResult = "CONFIRMED"
        PaymentService service = new PaymentService(new PaymentProviderRegistry([provider]), methodRepo(null), txRepo)

        PaymentResult result = service.refresh("tx1")

        assert provider.capturedReference == "HASH1"
        assert result.status == "CONFIRMED"
        assert updated.status == "CONFIRMED"
    }

    @Test
    void testRefreshIsNoOpForTerminalTransaction() {
        Transaction confirmed = new Transaction(id: "tx1", provider: "xrp", status: "CONFIRMED", externalReference: "HASH1")
        SignableProvider provider = signableProvider("xrp", null, null, null)
        provider.checkStatusResult = "FAILED"   // must NOT be consulted
        Repository<Transaction> txRepo = [get: { String id -> confirmed }] as Repository
        PaymentService service = new PaymentService(new PaymentProviderRegistry([provider]), methodRepo(null), txRepo)

        PaymentResult result = service.refresh("tx1")

        assert result.status == "CONFIRMED"
        assert provider.capturedReference == null   // terminal -> chain not queried
    }

    @Test
    void testConfirmReceiveMarksConfirmedWhenDepositVerified() {
        Transaction pending = new Transaction(id: "tx1", provider: "xrp", type: "RECEIVE", status: "PENDING", paymentMethodId: "pm1", amount: "5", recipientAddress: "rDest", recipientTag: 7L, dateCreated: new Date())
        Transaction updated = null
        Repository<Transaction> txRepo = [get: { String id -> pending }, filter: { f -> [] }, update: { String id, Transaction t -> updated = t; return t }] as Repository
        SignableProvider provider = signableProvider("xrp", null, null, null)
        provider.verifyDepositResult = true
        Repository<PaymentMethod> methodMethods = methodRepo(new PaymentMethod(provider: "xrp", id: "pm1", address: "rDest", destinationTag: 7L))
        PaymentService service = new PaymentService(new PaymentProviderRegistry([provider]), methodMethods, txRepo)

        PaymentResult result = service.confirmReceive("tx1", " DEPOSIT_HASH ")

        assert provider.capturedDepositReference == "DEPOSIT_HASH"
        assert result.status == "CONFIRMED"
        assert updated.status == "CONFIRMED"
        assert updated.externalReference == "deposit_hash"
    }

    @Test
    void testConfirmReceiveRejectsDepositAlreadyAppliedToAnotherTransaction() {
        Transaction pending = new Transaction(id: "tx2", provider: "xrp", type: "RECEIVE", status: "PENDING", paymentMethodId: "pm1", amount: "5", recipientAddress: "rDest", recipientTag: 7L, dateCreated: new Date())
        Transaction alreadyConfirmed = new Transaction(id: "tx1", provider: "xrp", type: "RECEIVE", status: "CONFIRMED", externalReference: "abc123")
        List<String> queriedReferences = []
        Repository<Transaction> txRepo = [
                get   : { String id -> pending },
                filter: { f -> queriedReferences << f.value; f.value == "abc123" ? [alreadyConfirmed] : [] }
        ] as Repository
        SignableProvider provider = signableProvider("xrp", null, null, null)
        provider.verifyDepositResult = true
        PaymentService service = new PaymentService(new PaymentProviderRegistry([provider]), methodRepo(null), txRepo)

        assertThrows(IllegalArgumentException) {
            service.confirmReceive("tx2", "ABC123")
        }
        assert queriedReferences.contains("abc123")
        assert provider.capturedDepositReference == null
    }

    @Test
    void testConfirmReceiveRejectsDepositStoredInUppercaseByAnotherTransaction() {
        Transaction pending = new Transaction(id: "tx2", provider: "xrp", type: "RECEIVE", status: "PENDING", paymentMethodId: "pm1", amount: "5", recipientAddress: "rDest", recipientTag: 7L, dateCreated: new Date())
        Transaction priorSend = new Transaction(id: "tx1", provider: "xrp", type: "SEND", status: "CONFIRMED", externalReference: "ABC123")
        Repository<Transaction> txRepo = [
                get   : { String id -> pending },
                filter: { f -> f.value == "ABC123" ? [priorSend] : [] }
        ] as Repository
        SignableProvider provider = signableProvider("xrp", null, null, null)
        provider.verifyDepositResult = true
        PaymentService service = new PaymentService(new PaymentProviderRegistry([provider]), methodRepo(null), txRepo)

        assertThrows(IllegalArgumentException) {
            service.confirmReceive("tx2", "abc123")
        }
        assert provider.capturedDepositReference == null
    }

    @Test
    void testConfirmReceiveRequiresDepositToSettleAfterReceiveWasCreated() {
        Date created = new Date(1_700_000_000_000L)
        Transaction pending = new Transaction(id: "tx1", provider: "xrp", type: "RECEIVE", status: "PENDING", paymentMethodId: "pm1", amount: "5", recipientAddress: "rDest", recipientTag: 7L, dateCreated: created)
        Repository<Transaction> txRepo = [get: { String id -> pending }, filter: { f -> [] }, update: { String id, Transaction t -> t }] as Repository
        SignableProvider provider = signableProvider("xrp", null, null, null)
        provider.verifyDepositResult = true
        PaymentService service = new PaymentService(new PaymentProviderRegistry([provider]), methodRepo(null), txRepo)

        service.confirmReceive("tx1", "DEPOSIT_HASH")

        assert provider.capturedNotBefore == new Date(created.time - 60_000L)
    }

    @Test
    void testConfirmReceiveRejectsReceiveWithoutCreationDate() {
        Transaction pending = new Transaction(id: "tx1", provider: "xrp", type: "RECEIVE", status: "PENDING", paymentMethodId: "pm1", amount: "5", recipientAddress: "rDest", recipientTag: 7L)
        Repository<Transaction> txRepo = [get: { String id -> pending }, filter: { f -> [] }, update: { String id, Transaction t -> t }] as Repository
        SignableProvider provider = signableProvider("xrp", null, null, null)
        provider.verifyDepositResult = true
        PaymentService service = new PaymentService(new PaymentProviderRegistry([provider]), methodRepo(null), txRepo)

        assertThrows(IllegalStateException) {
            service.confirmReceive("tx1", "DEPOSIT_HASH")
        }
        assert provider.capturedDepositReference == null
    }

    @Test
    void testConfirmReceiveRejectsWhitespaceOnlyReference() {
        Repository<Transaction> txRepo = [get: { String id -> throw new AssertionError("should not load the transaction") }] as Repository
        PaymentService service = new PaymentService(new PaymentProviderRegistry([signableProvider("xrp", null, null, null)]), methodRepo(null), txRepo)

        IllegalArgumentException error = assertThrows(IllegalArgumentException) {
            service.confirmReceive("tx1", "   ")
        }
        assert error.message == "depositTransaction is required"
    }

    @Test
    void testConfirmReceiveRollsBackWhenDepositClaimedConcurrently() {
        Transaction pending = new Transaction(id: "tx2", provider: "xrp", type: "RECEIVE", status: "PENDING", paymentMethodId: "pm1", amount: "5", recipientAddress: "rDest", recipientTag: 7L, externalReference: "rDest", dateCreated: new Date())
        Transaction concurrentWinner = new Transaction(id: "tx1", provider: "xrp", type: "RECEIVE", status: "CONFIRMED", externalReference: "abc123")
        List<Map> savedStates = []
        boolean confirmationSaved = false
        Repository<Transaction> txRepo = [
                get   : { String id -> pending },
                filter: { f -> confirmationSaved && f.value == "abc123" ? [concurrentWinner, pending] : [] },
                update: { String id, Transaction t -> savedStates << [status: t.status, externalReference: t.externalReference]; confirmationSaved = true; return t }
        ] as Repository
        SignableProvider provider = signableProvider("xrp", null, null, null)
        provider.verifyDepositResult = true
        PaymentService service = new PaymentService(new PaymentProviderRegistry([provider]), methodRepo(null), txRepo)

        assertThrows(IllegalStateException) {
            service.confirmReceive("tx2", "ABC123")
        }
        assert savedStates == [
                [status: "CONFIRMED", externalReference: "abc123"],
                [status: "PENDING", externalReference: "rDest"]
        ]
        assert pending.status == "PENDING"
    }

    @Test
    void testConfirmReceiveKeepsConfirmationWhenOnlyItsOwnRecordHoldsTheDeposit() {
        Transaction pending = new Transaction(id: "tx1", provider: "xrp", type: "RECEIVE", status: "PENDING", paymentMethodId: "pm1", amount: "5", recipientAddress: "rDest", recipientTag: 7L, dateCreated: new Date())
        int updates = 0
        boolean confirmationSaved = false
        Repository<Transaction> txRepo = [
                get   : { String id -> pending },
                filter: { f -> confirmationSaved && f.value == "abc123" ? [pending] : [] },
                update: { String id, Transaction t -> updates++; confirmationSaved = true; return t }
        ] as Repository
        SignableProvider provider = signableProvider("xrp", null, null, null)
        provider.verifyDepositResult = true
        PaymentService service = new PaymentService(new PaymentProviderRegistry([provider]), methodRepo(null), txRepo)

        PaymentResult result = service.confirmReceive("tx1", "ABC123")

        assert result.status == "CONFIRMED"
        assert updates == 1
    }

    @Test
    void testConfirmReceiveRejectsUnverifiedDeposit() {
        Transaction pending = new Transaction(id: "tx1", provider: "xrp", type: "RECEIVE", status: "PENDING", paymentMethodId: "pm1", amount: "5", recipientAddress: "rDest", recipientTag: 7L, dateCreated: new Date())
        Repository<Transaction> txRepo = [get: { String id -> pending }, filter: { f -> [] }, update: { String id, Transaction t -> t }] as Repository
        SignableProvider provider = signableProvider("xrp", null, null, null)
        provider.verifyDepositResult = false
        Repository<PaymentMethod> methodMethods = methodRepo(new PaymentMethod(provider: "xrp", id: "pm1", address: "rDest", destinationTag: 7L))
        PaymentService service = new PaymentService(new PaymentProviderRegistry([provider]), methodMethods, txRepo)

        assertThrows(IllegalArgumentException) {
            service.confirmReceive("tx1", "WRONG_HASH")
        }
    }

    @Test
    void testConfirmStripeReceiveBySession() {
        Transaction pending = new Transaction(id: "tx1", provider: "stripe", type: "RECEIVE", status: "PENDING", externalReference: "cs_123")
        Transaction updated = null
        Repository<Transaction> txRepo = [
                filter: { f -> [pending] },
                update: { String id, Transaction t -> updated = t; return t }
        ] as Repository
        PaymentService service = new PaymentService(new PaymentProviderRegistry([signableProvider("xrp", null, null, null)]), methodRepo(null), txRepo)

        PaymentResult result = service.confirmStripeReceive("cs_123")

        assert result.status == "CONFIRMED"
        assert updated.status == "CONFIRMED"
    }

    @Test
    void testConfirmStripeReceiveNotFoundThrows() {
        Repository<Transaction> txRepo = [filter: { f -> [] }] as Repository
        PaymentService service = new PaymentService(new PaymentProviderRegistry([signableProvider("xrp", null, null, null)]), methodRepo(null), txRepo)
        assertThrows(IllegalArgumentException) { service.confirmStripeReceive("cs_missing") }
    }

    @Test
    void testSubmitAllowsRetryAfterFailedAttempt() {
        // A previously FAILED submit must be re-submittable (the signed tx is still valid until it expires).
        Transaction failed = new Transaction(id: "tx1", provider: "xrp", status: "FAILED", unsignedPayload: "U")
        Repository<Transaction> txRepo = [get: { String id -> failed }, update: { String id, Transaction t -> t }] as Repository
        SignableProvider provider = signableProvider("xrp", null, new LedgerSubmission("HASH2", "SUBMITTED"), null)
        PaymentService service = new PaymentService(new PaymentProviderRegistry([provider]), methodRepo(null), txRepo)

        PaymentResult result = service.submitSend(new SignedSendRequest(transactionId: "tx1", signedTransaction: "BLOB"))

        assert provider.capturedSigned == "BLOB"   // re-broadcast happened
        assert result.status == "SUBMITTED"
    }

    @Test
    void testConfirmReceiveRequiresXrpDestinationTag() {
        // Receive snapshotted WITHOUT a destination tag -> cannot safely attribute a deposit on the shared account.
        Transaction pending = new Transaction(id: "tx1", provider: "xrp", type: "RECEIVE", status: "PENDING", paymentMethodId: "pm1", amount: "5", recipientAddress: "rDest", recipientTag: null, dateCreated: new Date())
        Repository<Transaction> txRepo = [get: { String id -> pending }] as Repository
        SignableProvider provider = signableProvider("xrp", null, null, null)
        provider.verifyDepositResult = true
        provider.requiresDestinationTag = true
        PaymentService service = new PaymentService(new PaymentProviderRegistry([provider]), methodRepo(null), txRepo)

        assertThrows(IllegalArgumentException) {
            service.confirmReceive("tx1", "DEPOSIT_HASH")
        }
    }

    @Test
    void testListAndGetTransactions() {
        Transaction tx = new Transaction(id: "t1")
        Repository<Transaction> txRepo = [list: { -> [tx] }, get: { String id -> tx }] as Repository
        PaymentService service = new PaymentService(new PaymentProviderRegistry([signableProvider("xrp", null, null, null)]), methodRepo(null), txRepo)

        assert service.allTransactions.size() == 1
        assert service.getTransaction("t1").id == "t1"
    }
}
