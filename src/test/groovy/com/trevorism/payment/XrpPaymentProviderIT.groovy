package com.trevorism.payment

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.xrpl.xrpl4j.crypto.keys.Base58EncodedSecret
import org.xrpl.xrpl4j.crypto.keys.KeyPair
import org.xrpl.xrpl4j.crypto.keys.Seed
import org.xrpl.xrpl4j.crypto.signing.SignatureService
import org.xrpl.xrpl4j.crypto.signing.SingleSignedTransaction
import org.xrpl.xrpl4j.crypto.signing.bc.BcSignatureService
import org.xrpl.xrpl4j.crypto.keys.PrivateKey
import org.xrpl.xrpl4j.model.jackson.ObjectMapperFactory
import org.xrpl.xrpl4j.model.transactions.Payment

import static org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * Real, end-to-end NON-CUSTODIAL flow against the XRP Ledger TESTNET (altnet). Faucet XRP, never real money.
 * Tagged "integration" and excluded from the normal build; run with: gradle integrationTest
 *
 * The test plays BOTH roles to prove the production flow:
 *   1. provider.prepareTransfer(...)  -> the API builds the unsigned tx JSON (no key)
 *   2. wallet signs the JSON locally  -> the CLIENT signs with their own key, producing a tx_blob
 *   3. provider.submitSignedTransfer  -> the API broadcasts the tx_blob (no key)
 *
 * Requires env:
 *  - XRP_TEST_SEED          a funded testnet account seed (sEd...), from https://xrpl.org/xrp-testnet-faucet.html
 *  - XRP_TEST_DESTINATION   a destination classic address (r...), e.g. a second faucet account
 */
@Tag("integration")
class XrpPaymentProviderIT {

    @Test
    void testNonCustodialTestnetPayout() {
        String seed = System.getenv("XRP_TEST_SEED")
        String destination = System.getenv("XRP_TEST_DESTINATION")
        assumeTrue(seed && destination, "Set XRP_TEST_SEED and XRP_TEST_DESTINATION to run the XRP integration test")

        XrpPaymentProvider provider = new XrpPaymentProvider("xrp-testnet", "testnet", "https://s.altnet.rippletest.net:51234/")
        KeyPair keyPair = Seed.fromBase58EncodedSecret(Base58EncodedSecret.of(seed)).deriveKeyPair()
        String senderAddress = keyPair.publicKey().deriveAddress().value()

        // 1. API builds the unsigned transaction (no key).
        PreparedTransfer prepared = provider.prepareTransfer(new TransferContext(senderAddress, destination, 1.0))
        assert prepared.signingFormat == "XRPL_JSON"

        // 2. CLIENT signs locally: parse the unsigned tx, set the signing key, sign -> tx_blob.
        ObjectMapper mapper = ObjectMapperFactory.create()
        Payment unsigned = mapper.readValue(prepared.unsignedPayload, Payment)
        Payment toSign = Payment.builder().from(unsigned).signingPublicKey(keyPair.publicKey()).build()
        SignatureService<PrivateKey> signatureService = new BcSignatureService()
        SingleSignedTransaction<Payment> signed = signatureService.sign(keyPair.privateKey(), toSign)
        String txBlob = signed.signedTransactionBytes().hexValue()

        // 3. API verifies the signed tx matches the prepared one, then broadcasts (no key).
        LedgerSubmission submission = provider.submitSignedTransfer(prepared.unsignedPayload, txBlob)

        println "XRP testnet non-custodial payout: hash=${submission.reference}, engineResult=${submission.status}"
        assert submission.reference != null
        assert submission.status == "tesSUCCESS"
    }
}
