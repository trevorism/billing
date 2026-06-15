package com.trevorism.payment

import com.bloxbean.cardano.client.account.Account
import com.bloxbean.cardano.client.common.model.Networks
import com.trevorism.PropertiesProvider
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * Real, end-to-end NON-CUSTODIAL flow against Cardano PREPROD. Uses faucet funded test ADA, never real money.
 * Tagged "integration" and excluded from the normal build; run with: gradle integrationTest
 *
 * The test plays BOTH roles to prove the production flow:
 *   1. provider.prepareTransfer(...)  -> the API builds the unsigned tx (no key)
 *   2. account.sign(unsignedHex)      -> the CLIENT signs locally with their own wallet
 *   3. provider.submitSignedTransfer  -> the API broadcasts the signed tx (no key)
 *
 * Defaults to the keyless Koios backend (no Blockfrost project needed).
 *
 * Requires env:
 *  - CARDANO_TEST_MNEMONIC     a 24-word mnemonic for a preprod account funded from the Cardano faucet
 *  - CARDANO_TEST_DESTINATION  a destination preprod address (addr_test1...); may be the sender's own address
 *  - BLOCKFROST_PROJECT_ID     (optional) a preprod Blockfrost project id; if set, uses Blockfrost instead of Koios
 */
@Tag("integration")
class CardanoPaymentProviderIT {

    @Test
    void testNonCustodialPreprodPayout() {
        String mnemonic = System.getenv("CARDANO_TEST_MNEMONIC")
        String destination = System.getenv("CARDANO_TEST_DESTINATION")
        String projectId = System.getenv("BLOCKFROST_PROJECT_ID")
        assumeTrue(mnemonic && destination,
                "Set CARDANO_TEST_MNEMONIC and CARDANO_TEST_DESTINATION to run the Cardano integration test")

        String backend = projectId ? "blockfrost" : "koios"
        PropertiesProvider props = [getProperty: { String key -> key == "blockfrostProjectId" ? projectId : null }] as PropertiesProvider
        CardanoPaymentProvider provider = new CardanoPaymentProvider(props, "cardano-preprod", "preprod", backend)

        Account clientWallet = new Account(Networks.preprod(), mnemonic)

        // 1. API builds the unsigned transaction (no key).
        PreparedTransfer prepared = provider.prepareTransfer(new TransferContext(clientWallet.baseAddress(), destination, 1.0))
        assert prepared.signingFormat == "CARDANO_CBOR"

        // 2. CLIENT signs locally with their own wallet.
        String signedCbor = clientWallet.sign(prepared.unsignedPayload)

        // 3. API broadcasts the signed transaction (no key).
        LedgerSubmission submission = provider.submitSignedTransfer(prepared.unsignedPayload, signedCbor)

        println "Cardano preprod non-custodial payout via ${backend}: txHash=${submission.reference}, status=${submission.status}"
        assert submission.reference != null
        assert submission.status == "SUBMITTED"
    }
}
