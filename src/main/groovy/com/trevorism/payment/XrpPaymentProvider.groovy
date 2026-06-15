package com.trevorism.payment

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.primitives.UnsignedInteger
import com.trevorism.model.PaymentMethod
import com.trevorism.model.PaymentRequest
import com.trevorism.model.PaymentResult
import com.trevorism.model.TransactionStatus
import groovy.transform.Memoized
import okhttp3.HttpUrl
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.xrpl.xrpl4j.client.JsonRpcClient
import org.xrpl.xrpl4j.client.JsonRpcRequest
import org.xrpl.xrpl4j.client.XrplClient
import org.xrpl.xrpl4j.codec.binary.XrplBinaryCodec
import org.xrpl.xrpl4j.model.client.accounts.AccountInfoRequestParams
import org.xrpl.xrpl4j.model.client.accounts.AccountInfoResult
import org.xrpl.xrpl4j.model.client.transactions.SubmitRequestParams
import org.xrpl.xrpl4j.model.client.transactions.TransactionRequestParams
import org.xrpl.xrpl4j.model.client.transactions.TransactionResult
import org.xrpl.xrpl4j.model.jackson.ObjectMapperFactory
import org.xrpl.xrpl4j.model.transactions.Address
import org.xrpl.xrpl4j.model.transactions.Hash256
import org.xrpl.xrpl4j.model.transactions.Payment
import org.xrpl.xrpl4j.model.transactions.Transaction
import org.xrpl.xrpl4j.model.transactions.XrpCurrencyAmount

/**
 * Non-custodial XRP Ledger provider. The API never holds a key:
 *
 *  - {@code prepareTransfer} builds an unsigned Payment from the client's address (fetching sequence + fee)
 *    and returns it as JSON for the client's wallet to sign.
 *  - {@code submitSignedTransfer} broadcasts the client-signed {@code tx_blob} via rippled.
 *  - {@code receiveMoney} returns the recipient's own address (and destination tag) as deposit instructions.
 *
 * XRPL receive uses one account disambiguated by a destination tag — the cross-chain contrast with Cardano,
 * which uses a distinct address per owner.
 *
 * One instance is created per network (xrp-testnet, xrp-mainnet); the network-qualified name is the registry
 * key, and rpcUrl selects the ledger. Instances are produced by {@code BeanFactory}, not component-scanned.
 */
class XrpPaymentProvider implements PaymentProvider, SignableTransferProvider, ConfirmableProvider, DepositVerifyingProvider, NetworkAwareProvider {

    static final String CHAIN = "xrp"
    static final int XRP_DECIMALS = 6
    static final int LAST_LEDGER_BUFFER = 75   // ~5 minutes at ~4s/ledger; tx is rejected after this ledger
    private static final Logger log = LoggerFactory.getLogger(XrpPaymentProvider)

    private final String name
    private final String walletNetwork
    private final String rpcUrl
    private final ObjectMapper xrplMapper = ObjectMapperFactory.create()

    XrpPaymentProvider(String name, String walletNetwork, String rpcUrl) {
        this.name = name
        this.walletNetwork = walletNetwork
        this.rpcUrl = rpcUrl
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
        return walletNetwork
    }

    @Override
    String getLabel() {
        return "XRP ${walletNetwork?.capitalize()}"
    }

    @Override
    boolean requiresDestinationTag() {
        return true
    }

    @Override
    PaymentResult receiveMoney(PaymentRequest request, PaymentMethod method) {
        requireProvider(method)
        if (!method.address) {
            throw new IllegalArgumentException("XRP payment method must have an address to receive funds")
        }
        validateXrpAddress(method.address)
        PaymentResult result = new PaymentResult(name, TransactionStatus.PENDING)
        result.externalReference = method.address
        result.instructions = "Send ${request.amount} XRP to ${method.address}".toString()
        if (method.destinationTag != null) {
            result.instructions += " with destination tag ${method.destinationTag}"
        }
        return result
    }

    @Override
    PreparedTransfer prepareTransfer(TransferContext context) {
        if (!context.senderAddress || !context.destinationAddress) {
            throw new IllegalArgumentException("Both senderAddress and destinationAddress are required")
        }
        validateXrpAddress(context.senderAddress)
        validateXrpAddress(context.destinationAddress)
        XrplClient xrplClient = createXrplClient()
        Address account = Address.of(context.senderAddress)
        AccountInfoResult accountInfo = xrplClient.accountInfo(AccountInfoRequestParams.of(account))
        UnsignedInteger sequence = accountInfo.accountData().sequence()
        XrpCurrencyAmount fee = xrplClient.fee().drops().openLedgerFee()

        // Unsigned: no signingPublicKey/signature - the client's wallet fills those when it signs.
        def builder = Payment.builder()
                .account(account)
                .destination(Address.of(context.destinationAddress))
                .amount(toDrops(context.amount))
                .fee(fee)
                .sequence(sequence)
        // Chain-level expiry: the ledger rejects the tx after this index (defends against stale submits).
        accountInfo.ledgerCurrentIndex().ifPresent { li ->
            builder.lastLedgerSequence(li.unsignedIntegerValue().plus(UnsignedInteger.valueOf(LAST_LEDGER_BUFFER)))
        }
        Payment payment = builder.build()

        String json = xrplMapper.writeValueAsString(payment)
        return new PreparedTransfer(json, "XRPL_JSON",
                "Sign this XRPL transaction with your wallet, then submit the resulting signed tx_blob (hex) to /api/payment/send/submit")
    }

    @Override
    LedgerSubmission submitSignedTransfer(String unsignedPayload, String signedTransaction) {
        if (!signedTransaction) {
            throw new IllegalArgumentException("signedTransaction (tx_blob) is required")
        }
        verifyMatchesPrepared(unsignedPayload, signedTransaction)
        JsonRpcClient client = createJsonRpcClient()
        JsonRpcRequest request = JsonRpcRequest.builder()
                .method("submit")
                .addParams(SubmitRequestParams.of(signedTransaction))
                .build()
        JsonNode response = client.postRpcRequest(request)
        JsonNode result = response.get("result")
        String engineResult = result?.get("engine_result")?.asText()
        String hash = result?.get("tx_json")?.get("hash")?.asText()
        // Normalize: only tes* codes mean the tx was accepted/queued; everything else (tec/tef/tem/ter) failed.
        String status = engineResult?.startsWith("tes") ? TransactionStatus.SUBMITTED : TransactionStatus.FAILED
        log.info("Broadcast XRP tx {}: {} -> {}", hash, engineResult, status)
        return new LedgerSubmission(hash, status)
    }

    /**
     * Defends against a client preparing one payment but submitting a different signed transaction: decode
     * the signed blob and confirm the money-critical fields match what we prepared.
     */
    protected void verifyMatchesPrepared(String unsignedPayload, String signedTransaction) {
        if (!unsignedPayload) {
            // Fail closed: never broadcast a signed blob we cannot check against a prepared transaction.
            throw new IllegalStateException("Missing prepared transaction; cannot verify the signed transaction")
        }
        JsonNode prepared = xrplMapper.readTree(unsignedPayload)
        JsonNode signed = xrplMapper.readTree(decodeSignedTransaction(signedTransaction))
        for (String field : ["Account", "Destination", "Amount"]) {
            String expected = prepared.path(field).asText()
            String actual = signed.path(field).asText()
            if (expected != actual) {
                throw new IllegalArgumentException("Signed transaction ${field} (${actual}) does not match the prepared transaction (${expected})")
            }
        }
    }

    protected String decodeSignedTransaction(String signedBlob) {
        return XrplBinaryCodec.getInstance().decode(signedBlob)
    }

    @Override
    String checkStatus(String reference) {
        if (!reference) {
            return TransactionStatus.PENDING
        }
        try {
            TransactionResult<Transaction> result = createXrplClient()
                    .transaction(TransactionRequestParams.of(Hash256.of(reference)), Transaction)
            if (!result.validated()) {
                return TransactionStatus.PENDING
            }
            String engineResult = result.metadata().map { it.transactionResult() }.orElse("")
            return engineResult == "tesSUCCESS" ? TransactionStatus.CONFIRMED : TransactionStatus.FAILED
        } catch (Exception e) {
            // Not yet visible on a validated ledger, or a transient lookup error.
            log.debug("XRP tx {} not yet confirmable: {}", reference, e.message)
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
            TransactionResult<Transaction> result = createXrplClient()
                    .transaction(TransactionRequestParams.of(Hash256.of(reference)), Transaction)
            boolean settled = result.validated() && result.metadata().map { it.transactionResult() }.orElse("") == "tesSUCCESS"
            // Re-serialize to rippled JSON to read fields without depending on typed amount accessors.
            JsonNode tx = xrplMapper.valueToTree(result.transaction())
            String destination = tx.path("Destination").asText(null)
            Long destinationTag = tx.hasNonNull("DestinationTag") ? tx.path("DestinationTag").asLong() : null
            String amountDrops = tx.path("Amount").asText(null)
            BigDecimal amount = amountDrops ? new BigDecimal(amountDrops).movePointLeft(XRP_DECIMALS) : null
            return new DepositDetails(settled, destination, destinationTag, amount)
        } catch (Exception e) {
            log.debug("XRP deposit {} not found: {}", reference, e.message)
            return null
        }
    }

    @Memoized
    protected XrplClient createXrplClient() {
        return new XrplClient(HttpUrl.get(rpcUrl))
    }

    @Memoized
    protected JsonRpcClient createJsonRpcClient() {
        return JsonRpcClient.construct(HttpUrl.get(rpcUrl))
    }

    private static void validateXrpAddress(String address) {
        // Classic XRPL addresses start with 'r'. Testnet and mainnet share the format, so the rpcUrl (not the
        // address) selects the network. The ledger performs full checksum validation at submit.
        if (!address || !address.startsWith("r")) {
            throw new IllegalArgumentException("Invalid XRP address: ${address}")
        }
    }

    private static void requireProvider(PaymentMethod method) {
        // The registry already routed by exact key; this is a loose chain-level self-check (xrp-testnet,
        // xrp-mainnet, ...) so an xrp provider never operates on a non-xrp method.
        if (!method?.provider?.toLowerCase()?.startsWith(CHAIN)) {
            throw new IllegalArgumentException("XrpPaymentProvider requires an xrp payment method")
        }
    }

    static XrpCurrencyAmount toDrops(BigDecimal xrp) {
        // Exact XRP -> drops; rejects more than 6 decimal places (sub-drop precision).
        return XrpCurrencyAmount.ofDrops(xrp.movePointRight(XRP_DECIMALS).toBigIntegerExact().longValueExact())
    }
}
