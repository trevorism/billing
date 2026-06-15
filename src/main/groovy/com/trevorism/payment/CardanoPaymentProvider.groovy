package com.trevorism.payment

import com.bloxbean.cardano.client.api.model.Amount
import com.bloxbean.cardano.client.api.model.Result
import com.bloxbean.cardano.client.backend.api.BackendService
import com.bloxbean.cardano.client.backend.blockfrost.common.Constants as BlockfrostConstants
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService
import com.bloxbean.cardano.client.backend.koios.Constants as KoiosConstants
import com.bloxbean.cardano.client.backend.koios.KoiosBackendService
import com.bloxbean.cardano.client.backend.model.TransactionContent
import co.nstant.in.cbor.CborDecoder
import co.nstant.in.cbor.model.DataItem
import co.nstant.in.cbor.model.Map as CborMap
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder
import com.bloxbean.cardano.client.quicktx.Tx
import com.bloxbean.cardano.client.transaction.spec.Transaction
import com.bloxbean.cardano.client.transaction.spec.TransactionWitnessSet
import com.bloxbean.cardano.client.util.HexUtil
import groovy.transform.Memoized
import com.trevorism.PropertiesProvider
import com.trevorism.model.PaymentMethod
import com.trevorism.model.PaymentRequest
import com.trevorism.model.PaymentResult
import com.trevorism.model.TransactionStatus
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Non-custodial Cardano provider (QuickTx + a selectable backend: keyless Koios, or Blockfrost). The API
 * never holds a key:
 *
 *  - {@code prepareTransfer} builds and balances an unsigned transaction from the client's address and
 *    returns its CBOR for the client's wallet to sign.
 *  - {@code submitSignedTransfer} broadcasts the client-signed CBOR via the backend.
 *  - {@code receiveMoney} returns the recipient's own deposit address.
 *
 * Cardano's UTXO model uses a distinct address per owner (no destination tag) — the cross-chain contrast
 * with XRP that this second provider exercises.
 *
 * One instance is created per network (cardano-preprod, cardano-mainnet); the network-qualified name is the
 * registry key, while {@code network} (preprod/mainnet) selects the backend URL and address validation.
 * Instances are produced by {@code BeanFactory}, not component-scanned.
 */
class CardanoPaymentProvider implements PaymentProvider, SignableTransferProvider, WitnessAssemblingProvider, ConfirmableProvider, DepositVerifyingProvider, NetworkAwareProvider {

    static final String CHAIN = "cardano"
    static final int ADA_DECIMALS = 6
    static final long TTL_SLOT_BUFFER = 600   // ~10 minutes at ~1s/slot; tx is rejected (ttl) after this slot
    private static final Logger log = LoggerFactory.getLogger(CardanoPaymentProvider)

    private final PropertiesProvider propertiesProvider
    private final String name
    private final String network
    private final String backend

    CardanoPaymentProvider(PropertiesProvider propertiesProvider, String name, String network, String backend) {
        this.propertiesProvider = propertiesProvider
        this.name = name
        this.network = network
        this.backend = backend
    }

    /** Convenience constructor (defaults the backend to Koios); used by tests. */
    CardanoPaymentProvider(PropertiesProvider propertiesProvider, String name, String network) {
        this(propertiesProvider, name, network, "koios")
    }

    @Override
    String getName() {
        return name
    }

    @Override
    String getChain() {
        return CHAIN
    }

    @Override
    String getWalletNetwork() {
        return "mainnet".equalsIgnoreCase(network) ? "mainnet" : "testnet"
    }

    @Override
    String getLabel() {
        return "Cardano ${network?.capitalize()}"
    }

    @Override
    PaymentResult receiveMoney(PaymentRequest request, PaymentMethod method) {
        requireProvider(method)
        if (!method.address) {
            throw new IllegalArgumentException("Cardano payment method must have a deposit address to receive funds")
        }
        validateCardanoAddress(method.address)
        PaymentResult result = new PaymentResult(name, TransactionStatus.PENDING)
        result.externalReference = method.address
        result.instructions = "Send ${request.amount} ADA to ${method.address}".toString()
        return result
    }

    @Override
    PreparedTransfer prepareTransfer(TransferContext context) {
        if (!context.senderAddress || !context.destinationAddress) {
            throw new IllegalArgumentException("Both senderAddress and destinationAddress are required")
        }
        validateCardanoAddress(context.senderAddress)
        validateCardanoAddress(context.destinationAddress)
        BackendService backendService = createBackendService()
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService)
        Tx tx = new Tx()
                .payToAddress(context.destinationAddress, Amount.lovelace(toLovelace(context.amount)))
                .from(context.senderAddress)
        // Chain-level expiry: set ttl so the network rejects the tx after this slot (defends against stale submits).
        long ttlSlot = backendService.getBlockService().getLatestBlock().getValue().getSlot() + TTL_SLOT_BUFFER
        Transaction unsigned = quickTxBuilder.compose(tx).validTo(ttlSlot).build()

        String cborHex = unsigned.serializeToHex()
        return new PreparedTransfer(cborHex, "CARDANO_CBOR",
                "Sign this transaction CBOR with your wallet, then submit the signed CBOR (hex) to /api/payment/send/submit")
    }

    @Override
    LedgerSubmission submitSignedTransfer(String unsignedPayload, String signedTransaction) {
        if (!signedTransaction) {
            throw new IllegalArgumentException("signedTransaction (signed CBOR hex) is required")
        }
        verifyMatchesPrepared(unsignedPayload, signedTransaction)
        return broadcast(HexUtil.decodeHexString(signedTransaction))
    }

    /**
     * Defends against a client preparing one payment but submitting a different signed transaction: the
     * signed CBOR's body must equal the prepared transaction's body (signing only adds witnesses, never
     * changes the body). Fails closed if the prepared payload is missing.
     */
    protected void verifyMatchesPrepared(String unsignedPayload, String signedTransaction) {
        if (!unsignedPayload) {
            throw new IllegalStateException("Missing prepared transaction; cannot verify the signed transaction")
        }
        Transaction prepared
        Transaction signed
        try {
            prepared = Transaction.deserialize(HexUtil.decodeHexString(unsignedPayload))
            signed = Transaction.deserialize(HexUtil.decodeHexString(signedTransaction))
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid transaction CBOR: ${e.message}", e)
        }
        if (prepared.getBody() != signed.getBody()) {
            throw new IllegalArgumentException("Signed transaction body does not match the prepared transaction")
        }
    }

    @Override
    LedgerSubmission submitSignedWitness(String unsignedPayload, String witness) {
        if (!unsignedPayload || !witness) {
            throw new IllegalArgumentException("Both unsignedPayload and witness are required")
        }
        try {
            // Merge the wallet's vkey witness (CIP-30 signTx output) into the prepared unsigned transaction.
            // A freshly built/round-tripped tx can have a null witness set (Lombok @Builder.Default + empty
            // lists deserialize to null), so guard each step.
            Transaction transaction = Transaction.deserialize(HexUtil.decodeHexString(unsignedPayload))
            List<DataItem> items = CborDecoder.decode(HexUtil.decodeHexString(witness))
            TransactionWitnessSet clientWitness = TransactionWitnessSet.deserialize((CborMap) items.get(0))

            TransactionWitnessSet witnessSet = transaction.getWitnessSet()
            if (witnessSet == null) {
                transaction.setWitnessSet(clientWitness)
            } else if (witnessSet.getVkeyWitnesses() == null) {
                witnessSet.setVkeyWitnesses(clientWitness.getVkeyWitnesses())
            } else {
                witnessSet.getVkeyWitnesses().addAll(clientWitness.getVkeyWitnesses() ?: [])
            }
            return broadcast(transaction.serialize())
        } catch (Exception e) {
            throw new RuntimeException("Failed to assemble Cardano transaction from witness: ${e.message}", e)
        }
    }

    private LedgerSubmission broadcast(byte[] signedCbor) {
        Result<String> result = createBackendService().getTransactionService().submitTransaction(signedCbor)
        log.info("Broadcast Cardano tx via {}: success={} response={}", backend, result.isSuccessful(), result.getResponse())
        return new LedgerSubmission(result.getValue(), result.isSuccessful() ? TransactionStatus.SUBMITTED : TransactionStatus.FAILED)
    }

    @Override
    String checkStatus(String reference) {
        if (!reference) {
            return TransactionStatus.PENDING
        }
        try {
            Result<TransactionContent> result = createBackendService().getTransactionService().getTransaction(reference)
            // Present in a block (successful lookup with a value) means it settled; otherwise still pending.
            return (result.isSuccessful() && result.getValue() != null) ? TransactionStatus.CONFIRMED : TransactionStatus.PENDING
        } catch (Exception e) {
            log.debug("Cardano tx {} not yet confirmable: {}", reference, e.message)
            return TransactionStatus.PENDING
        }
    }

    @Override
    boolean verifyDeposit(String reference, String expectedAddress, BigDecimal expectedAmount, Long expectedDestinationTag) {
        if (!reference) {
            return false
        }
        return DepositMatcher.matches(fetchDeposit(reference, expectedAddress), expectedAddress, expectedAmount, expectedDestinationTag)
    }

    protected DepositDetails fetchDeposit(String reference, String expectedAddress) {
        try {
            def utxo = createBackendService().getTransactionService().getTransactionUtxos(reference).getValue()
            if (utxo == null) {
                return null
            }
            BigDecimal lovelace = BigDecimal.ZERO
            utxo.getOutputs().each { out ->
                if (out.getAddress() == expectedAddress) {
                    out.getAmount().each { amt ->
                        if (amt.getUnit() == "lovelace") {
                            lovelace = lovelace.add(new BigDecimal(amt.getQuantity()))
                        }
                    }
                }
            }
            // Present in a block (utxos returned) means settled; no destination-tag concept on Cardano.
            return new DepositDetails(true, expectedAddress, null, lovelace.movePointLeft(ADA_DECIMALS))
        } catch (Exception e) {
            log.debug("Cardano deposit {} not found: {}", reference, e.message)
            return null
        }
    }

    @Memoized
    protected BackendService createBackendService() {
        if (isKoios()) {
            return new KoiosBackendService(resolveKoiosUrl())
        }
        String projectId = propertiesProvider.getProperty("blockfrostProjectId")
        if (!projectId) {
            throw new IllegalStateException("blockfrostProjectId is not configured; cannot use the Blockfrost backend")
        }
        return new BFBackendService(resolveBlockfrostUrl(), projectId)
    }

    private boolean isKoios() {
        return "koios".equalsIgnoreCase(backend)
    }

    private String resolveBlockfrostUrl() {
        switch (network?.toLowerCase()) {
            case "mainnet": return BlockfrostConstants.BLOCKFROST_MAINNET_URL
            case "preview": return BlockfrostConstants.BLOCKFROST_PREVIEW_URL
            default: return BlockfrostConstants.BLOCKFROST_PREPROD_URL
        }
    }

    private String resolveKoiosUrl() {
        switch (network?.toLowerCase()) {
            case "mainnet": return KoiosConstants.KOIOS_MAINNET_URL
            case "preview": return KoiosConstants.KOIOS_PREVIEW_URL
            default: return KoiosConstants.KOIOS_PREPROD_URL
        }
    }

    static BigInteger toLovelace(BigDecimal ada) {
        // Exact ADA -> lovelace; rejects more than 6 decimal places (sub-lovelace precision).
        return ada.movePointRight(ADA_DECIMALS).toBigIntegerExact()
    }

    private void validateCardanoAddress(String address) {
        // Cardano addresses are network-tagged: mainnet starts with "addr1", testnets with "addr_test".
        // Rejecting a network mismatch here prevents sending funds to an address on the wrong network.
        boolean mainnet = "mainnet".equalsIgnoreCase(network)
        if (mainnet && !address.startsWith("addr1")) {
            throw new IllegalArgumentException("Expected a mainnet Cardano address (addr1...) but got: ${address}")
        }
        if (!mainnet && !address.startsWith("addr_test")) {
            throw new IllegalArgumentException("Expected a ${network} testnet Cardano address (addr_test...) but got: ${address}")
        }
    }

    private static void requireProvider(PaymentMethod method) {
        // The registry already routed by exact key; this is a loose chain-level self-check (cardano-preprod,
        // cardano-mainnet, ...) so a cardano provider never operates on a non-cardano method.
        if (!method?.provider?.toLowerCase()?.startsWith(CHAIN)) {
            throw new IllegalArgumentException("CardanoPaymentProvider requires a cardano payment method")
        }
    }
}
