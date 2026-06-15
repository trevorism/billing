package com.trevorism.payment

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.trevorism.model.PaymentMethod
import com.trevorism.model.PaymentRequest
import com.trevorism.model.PaymentResult
import org.junit.jupiter.api.Test
import org.xrpl.xrpl4j.client.JsonRpcClient
import org.xrpl.xrpl4j.model.transactions.XrpCurrencyAmount

import static org.junit.jupiter.api.Assertions.assertThrows

class XrpPaymentProviderTest {

    @Test
    void testNameIsXrp() {
        XrpPaymentProvider provider = new XrpPaymentProvider("xrp-testnet", "testnet", "https://rpc")
        assert provider.name == "xrp-testnet"
        assert provider.chain == "xrp"
        assert provider.walletNetwork == "testnet"
        assert provider.requiresDestinationTag()
    }

    @Test
    void testReceiveMoneyReturnsAddressAndDestinationTag() {
        XrpPaymentProvider provider = new XrpPaymentProvider("xrp-testnet", "testnet", "https://rpc")
        PaymentRequest request = new PaymentRequest(paymentMethodId: "pm1", amount: 10)
        PaymentMethod method = new PaymentMethod(provider: "xrp", ownerId: "c1", address: "rRecipient", destinationTag: 42L)

        PaymentResult result = provider.receiveMoney(request, method)

        assert result.provider == "xrp-testnet"
        assert result.status == "PENDING"
        assert result.externalReference == "rRecipient"
        assert result.instructions.contains("rRecipient")
        assert result.instructions.contains("42")
    }

    @Test
    void testReceiveMoneyWithoutAddressThrows() {
        XrpPaymentProvider provider = new XrpPaymentProvider("xrp-testnet", "testnet", "https://rpc")
        assertThrows(IllegalArgumentException) {
            provider.receiveMoney(new PaymentRequest(amount: 1), new PaymentMethod(provider: "xrp"))
        }
    }

    @Test
    void testPrepareTransferRequiresAddresses() {
        XrpPaymentProvider provider = new XrpPaymentProvider("xrp-testnet", "testnet", "https://rpc")
        assertThrows(IllegalArgumentException) {
            provider.prepareTransfer(new TransferContext(null, "rDest", 5))
        }
    }

    @Test
    void testSubmitSignedTransferRequiresBlob() {
        XrpPaymentProvider provider = new XrpPaymentProvider("xrp-testnet", "testnet", "https://rpc")
        assertThrows(IllegalArgumentException) {
            provider.submitSignedTransfer("{}", null)
        }
    }

    @Test
    void testSubmitSignedTransferDemonstrationWithMockedRpc() {
        // Demonstrates verify + broadcast with no network: the decode seam returns a tx matching the prepared
        // one (so the integrity check passes) and the JSON-RPC client is faked to return a canned response.
        String prepared = '{"Account":"rSender","Destination":"rDest","Amount":"1000000"}'
        JsonNode canned = new ObjectMapper().readTree('{"result":{"engine_result":"tesSUCCESS","tx_json":{"hash":"XRP_TX_HASH"}}}')
        JsonRpcClient fakeRpc = [postRpcRequest: { req -> canned }] as JsonRpcClient
        XrpPaymentProvider provider = new XrpPaymentProvider("xrp-testnet", "testnet", "https://rpc") {
            @Override protected JsonRpcClient createJsonRpcClient() { return fakeRpc }
            @Override protected String decodeSignedTransaction(String blob) { return prepared }
        }

        LedgerSubmission submission = provider.submitSignedTransfer(prepared, "anyblob")

        assert submission.reference == "XRP_TX_HASH"
        assert submission.status == "SUBMITTED"   // tesSUCCESS engine result normalized to canonical status
    }

    @Test
    void testVerifyMatchesPreparedFailsClosedWithoutPreparedPayload() {
        XrpPaymentProvider provider = new XrpPaymentProvider("xrp-testnet", "testnet", "https://rpc")
        assertThrows(IllegalStateException) {
            provider.verifyMatchesPrepared(null, "blob")
        }
    }

    @Test
    void testSubmitRejectsTamperedTransaction() {
        // The client prepared a 1 XRP payment but submitted a signed tx paying 1000 XRP elsewhere.
        String prepared = '{"Account":"rSender","Destination":"rDest","Amount":"1000000"}'
        String tampered = '{"Account":"rSender","Destination":"rAttacker","Amount":"1000000000"}'
        XrpPaymentProvider provider = new XrpPaymentProvider("xrp-testnet", "testnet", "https://rpc") {
            @Override protected String decodeSignedTransaction(String blob) { return tampered }
        }

        assertThrows(IllegalArgumentException) {
            provider.submitSignedTransfer(prepared, "anyblob")
        }
    }

    @Test
    void testWrongMethodTypeThrows() {
        XrpPaymentProvider provider = new XrpPaymentProvider("xrp-testnet", "testnet", "https://rpc")
        assertThrows(IllegalArgumentException) {
            provider.receiveMoney(new PaymentRequest(amount: 5), new PaymentMethod(provider: "stripe"))
        }
    }

    @Test
    void testCheckStatusPendingForBlankReference() {
        XrpPaymentProvider provider = new XrpPaymentProvider("xrp-testnet", "testnet", "https://rpc")
        assert provider instanceof com.trevorism.payment.ConfirmableProvider
        assert provider.checkStatus(null) == "PENDING"
        assert provider.checkStatus("") == "PENDING"
    }

    @Test
    void testToDropsExactConversion() {
        assert XrpPaymentProvider.toDrops(new BigDecimal("1.5")) == XrpCurrencyAmount.ofDrops(1_500_000L)
        assert XrpPaymentProvider.toDrops(new BigDecimal("0.000001")) == XrpCurrencyAmount.ofDrops(1L)
    }

    @Test
    void testToDropsRejectsSubDropPrecision() {
        assertThrows(ArithmeticException) {
            XrpPaymentProvider.toDrops(new BigDecimal("0.0000001")) // 7 decimals
        }
    }
}
