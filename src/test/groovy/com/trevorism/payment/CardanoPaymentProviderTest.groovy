package com.trevorism.payment

import com.bloxbean.cardano.client.api.model.Result
import com.bloxbean.cardano.client.backend.api.BackendService
import com.bloxbean.cardano.client.backend.api.TransactionService
import com.trevorism.PropertiesProvider
import com.trevorism.model.PaymentMethod
import com.trevorism.model.PaymentRequest
import com.trevorism.model.PaymentResult
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertThrows

class CardanoPaymentProviderTest {

    private PropertiesProvider props(Map values) {
        return [getProperty: { String key -> values.get(key) }] as PropertiesProvider
    }

    @Test
    void testNameIsCardano() {
        CardanoPaymentProvider provider = new CardanoPaymentProvider(props([:]), "cardano-preprod", "preprod")
        assert provider.name == "cardano-preprod"
        assert provider.chain == "cardano"
        assert provider.walletNetwork == "testnet"
        assert !provider.requiresDestinationTag()
    }

    @Test
    void testReceiveMoneyReturnsDepositAddress() {
        CardanoPaymentProvider provider = new CardanoPaymentProvider(props([:]), "cardano-preprod", "preprod")
        PaymentRequest request = new PaymentRequest(paymentMethodId: "pm1", amount: 12)
        PaymentMethod method = new PaymentMethod(provider: "cardano", ownerId: "c1", address: "addr_test1xyz")

        PaymentResult result = provider.receiveMoney(request, method)

        assert result.provider == "cardano-preprod"
        assert result.status == "PENDING"
        assert result.externalReference == "addr_test1xyz"
        assert result.instructions.contains("addr_test1xyz")
        assert result.instructions.contains("12")
    }

    @Test
    void testReceiveMoneyWithoutAddressThrows() {
        CardanoPaymentProvider provider = new CardanoPaymentProvider(props([:]), "cardano-preprod", "preprod")
        assertThrows(IllegalArgumentException) {
            provider.receiveMoney(new PaymentRequest(amount: 1), new PaymentMethod(provider: "cardano"))
        }
    }

    @Test
    void testPrepareTransferRequiresAddresses() {
        CardanoPaymentProvider provider = new CardanoPaymentProvider(props([:]), "cardano-preprod", "preprod")
        assertThrows(IllegalArgumentException) {
            provider.prepareTransfer(new TransferContext("addr_test1from", null, 5))
        }
    }

    @Test
    void testSubmitSignedTransferRequiresPayload() {
        CardanoPaymentProvider provider = new CardanoPaymentProvider(props([:]), "cardano-preprod", "preprod")
        assertThrows(IllegalArgumentException) {
            provider.submitSignedTransfer("unsigned", "")
        }
    }

    @Test
    void testSubmitSignedTransferDemonstrationWithMockedBackend() {
        // Demonstrates broadcasting a client-signed CBOR with no network: the integrity check is overridden
        // (exercised separately) and the backend's transaction service is faked to accept the bytes.
        TransactionService txService = [submitTransaction: { byte[] cbor -> Result.success("ok").withValue("CARDANO_TX_HASH") }] as TransactionService
        BackendService fakeBackend = [getTransactionService: { -> txService }] as BackendService
        CardanoPaymentProvider provider = new CardanoPaymentProvider(props([:]), "cardano-preprod", "preprod", "koios") {
            @Override protected BackendService createBackendService() { return fakeBackend }
            @Override protected void verifyMatchesPrepared(String u, String s) { /* exercised in dedicated tests */ }
        }

        LedgerSubmission submission = provider.submitSignedTransfer("unsigned", "deadbeef")

        assert submission.reference == "CARDANO_TX_HASH"
        assert submission.status == "SUBMITTED"
    }

    @Test
    void testVerifyMatchesPreparedFailsClosedWithoutPreparedPayload() {
        CardanoPaymentProvider provider = new CardanoPaymentProvider(props([:]), "cardano-preprod", "preprod")
        assertThrows(IllegalStateException) {
            provider.verifyMatchesPrepared("", "deadbeef")   // no prepared tx -> refuse to broadcast
        }
    }

    @Test
    void testVerifyMatchesPreparedRejectsInvalidCbor() {
        CardanoPaymentProvider provider = new CardanoPaymentProvider(props([:]), "cardano-preprod", "preprod")
        assertThrows(IllegalArgumentException) {
            provider.verifyMatchesPrepared("nothex", "alsonothex")
        }
    }

    @Test
    void testSubmitSignedWitnessRequiresBothInputs() {
        CardanoPaymentProvider provider = new CardanoPaymentProvider(props([:]), "cardano-preprod", "preprod")
        assertThrows(IllegalArgumentException) {
            provider.submitSignedWitness("UNSIGNED", null)
        }
        assertThrows(IllegalArgumentException) {
            provider.submitSignedWitness(null, "WITNESS")
        }
    }

    @Test
    void testIsWitnessAssemblingProvider() {
        CardanoPaymentProvider provider = new CardanoPaymentProvider(props([:]), "cardano-preprod", "preprod")
        assert provider instanceof WitnessAssemblingProvider
    }

    @Test
    void testCheckStatusPendingForBlankReference() {
        CardanoPaymentProvider provider = new CardanoPaymentProvider(props([:]), "cardano-preprod", "preprod")
        assert provider instanceof com.trevorism.payment.ConfirmableProvider
        assert provider.checkStatus(null) == "PENDING"
        assert provider.checkStatus("") == "PENDING"
    }

    @Test
    void testToLovelaceExactConversion() {
        assert CardanoPaymentProvider.toLovelace(new BigDecimal("2.5")) == 2_500_000G
        assert CardanoPaymentProvider.toLovelace(new BigDecimal("0.000001")) == 1G
    }

    @Test
    void testToLovelaceRejectsSubLovelacePrecision() {
        assertThrows(ArithmeticException) {
            CardanoPaymentProvider.toLovelace(new BigDecimal("1.0000001")) // 7 decimals
        }
    }

    @Test
    void testReceiveRejectsWrongNetworkAddress() {
        // A preprod provider must reject a mainnet (addr1...) address.
        CardanoPaymentProvider provider = new CardanoPaymentProvider(props([:]), "cardano-preprod", "preprod")
        assertThrows(IllegalArgumentException) {
            provider.receiveMoney(new PaymentRequest(amount: 1), new PaymentMethod(provider: "cardano", address: "addr1qxyzmainnet"))
        }
    }

    @Test
    void testWrongMethodTypeThrows() {
        CardanoPaymentProvider provider = new CardanoPaymentProvider(props([:]), "cardano-preprod", "preprod")
        assertThrows(IllegalArgumentException) {
            provider.receiveMoney(new PaymentRequest(amount: 5), new PaymentMethod(provider: "xrp"))
        }
    }
}
