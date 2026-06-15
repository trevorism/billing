package com.trevorism.service

import com.trevorism.data.Repository
import com.trevorism.data.model.filtering.FilterConstants
import com.trevorism.data.model.filtering.SimpleFilter
import com.trevorism.model.PaymentMethod
import com.trevorism.model.PaymentRequest
import com.trevorism.model.PaymentResult
import com.trevorism.model.SendPrepareRequest
import com.trevorism.model.SignedSendRequest
import com.trevorism.model.Transaction
import com.trevorism.model.TransactionStatus
import com.trevorism.model.TransactionType
import com.trevorism.model.UnsignedTransaction
import com.trevorism.model.WitnessSubmitRequest
import com.trevorism.payment.ConfirmableProvider
import com.trevorism.payment.StripePaymentProvider
import com.trevorism.payment.DepositVerifyingProvider
import com.trevorism.payment.LedgerSubmission
import com.trevorism.payment.PaymentProvider
import com.trevorism.payment.PaymentProviderRegistry
import com.trevorism.payment.PreparedTransfer
import com.trevorism.payment.SignableTransferProvider
import com.trevorism.payment.TransferContext
import com.trevorism.payment.WitnessAssemblingProvider
import io.micronaut.context.annotation.Value
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Orchestrates money movement.
 *
 * Sending is non-custodial and two-phase: {@link #prepareSend} builds an unsigned transaction (recording a
 * PREPARED {@link Transaction}); the client signs it with their own wallet; {@link #submitSend} broadcasts
 * the signed result and finalizes the record. The API never holds a private key.
 *
 * Receiving needs no key and is a single call.
 */
@Singleton
class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService)

    private final PaymentProviderRegistry registry
    private final Repository<PaymentMethod> paymentMethodRepository
    private final Repository<Transaction> transactionRepository
    private final int preparedTtlMinutes
    private final ComplianceService complianceService

    @Inject
    PaymentService(PaymentProviderRegistry registry,
                   Repository<PaymentMethod> paymentMethodRepository,
                   Repository<Transaction> transactionRepository,
                   @Value('${payment.preparedTtlMinutes:15}') int preparedTtlMinutes,
                   ComplianceService complianceService) {
        this.registry = registry
        this.paymentMethodRepository = paymentMethodRepository
        this.transactionRepository = transactionRepository
        this.preparedTtlMinutes = preparedTtlMinutes
        this.complianceService = complianceService
    }

    /** Convenience constructor (15 minute TTL, permissive compliance); used by tests. */
    PaymentService(PaymentProviderRegistry registry,
                   Repository<PaymentMethod> paymentMethodRepository,
                   Repository<Transaction> transactionRepository) {
        this(registry, paymentMethodRepository, transactionRepository, 15, ComplianceService.permissive())
    }

    /** Phase 1 of send: build an unsigned transaction for the client to sign, and record it as PREPARED. */
    UnsignedTransaction prepareSend(SendPrepareRequest request, String tenant) {
        if (!request.senderAddress) {
            throw new IllegalArgumentException("senderAddress is required")
        }
        requirePositiveAmount(request.amount)
        PaymentMethod method = loadMethod(request.paymentMethodId)
        PaymentProvider provider = registry.get(method.provider)
        SignableTransferProvider signable = asSignable(provider)
        if (!method.address) {
            throw new IllegalArgumentException("Payment method ${method.id} does not have an on-chain address")
        }

        String destinationAddress = method.address
        complianceService.enforceLimit(provider.name, request.amount)
        complianceService.screen(destinationAddress)
        log.info("Preparing {} send of {} from {} to {}", provider.name, request.amount, request.senderAddress, destinationAddress)
        PreparedTransfer prepared = signable.prepareTransfer(new TransferContext(request.senderAddress, destinationAddress, request.amount))

        Transaction transaction = new Transaction(
                type: TransactionType.SEND,
                provider: provider.name,
                paymentMethodId: method.id,
                ownerId: method.ownerId,
                tenant: tenant,
                senderAddress: request.senderAddress,
                amount: request.amount?.toPlainString(),
                currency: request.currency,
                status: TransactionStatus.PREPARED,
                unsignedPayload: prepared.unsignedPayload,
                expiresAt: new Date(System.currentTimeMillis() + preparedTtlMinutes * 60_000L),
                dateCreated: new Date()
        )
        Transaction saved = transactionRepository.create(transaction)

        UnsignedTransaction result = new UnsignedTransaction(provider.name, prepared.unsignedPayload, prepared.signingFormat, prepared.instructions)
        result.transactionId = saved.id
        return result
    }

    /** Phase 2 of send: broadcast the client-signed transaction and finalize the record. */
    PaymentResult submitSend(SignedSendRequest request) {
        if (!request.signedTransaction) {
            throw new IllegalArgumentException("signedTransaction is required")
        }
        Transaction transaction = transactionRepository.get(request.transactionId)
        if (!transaction) {
            throw new IllegalArgumentException("No prepared transaction found: ${request.transactionId}")
        }
        PaymentResult replay = replayIfAlreadyProcessed(transaction)
        if (replay != null) {
            return replay
        }
        ensureNotExpired(transaction)
        SignableTransferProvider signable = asSignable(registry.get(transaction.provider))
        LedgerSubmission submission = signable.submitSignedTransfer(transaction.unsignedPayload, request.signedTransaction)

        transaction.status = submission.status
        transaction.externalReference = submission.reference
        saveUpdate(transaction)
        log.info("Submitted {} send {}: {} ({})", transaction.provider, transaction.id, submission.reference, submission.status)

        PaymentResult result = new PaymentResult(transaction.provider, submission.status)
        result.externalReference = submission.reference
        return result
    }

    /** Phase 2 variant for CIP-30 wallets: assemble the prepared tx with the client's witness, then broadcast. */
    PaymentResult submitSendWitness(WitnessSubmitRequest request) {
        if (!request.witness) {
            throw new IllegalArgumentException("witness is required")
        }
        Transaction transaction = transactionRepository.get(request.transactionId)
        if (!transaction) {
            throw new IllegalArgumentException("No prepared transaction found: ${request.transactionId}")
        }
        PaymentResult replay = replayIfAlreadyProcessed(transaction)
        if (replay != null) {
            return replay
        }
        ensureNotExpired(transaction)
        PaymentProvider provider = registry.get(transaction.provider)
        if (!(provider instanceof WitnessAssemblingProvider)) {
            throw new UnsupportedOperationException("Provider ${provider.name} does not support witness assembly")
        }
        LedgerSubmission submission = ((WitnessAssemblingProvider) provider).submitSignedWitness(transaction.unsignedPayload, request.witness)

        transaction.status = submission.status
        transaction.externalReference = submission.reference
        saveUpdate(transaction)
        log.info("Submitted (witness) {} send {}: {} ({})", transaction.provider, transaction.id, submission.reference, submission.status)

        PaymentResult result = new PaymentResult(transaction.provider, submission.status)
        result.externalReference = submission.reference
        return result
    }

    PaymentResult receive(PaymentRequest request, String tenant) {
        requirePositiveAmount(request.amount)
        PaymentMethod method = loadMethod(request.paymentMethodId)
        PaymentProvider provider = registry.get(method.provider)
        complianceService.enforceLimit(provider.name, request.amount)
        log.info("Receiving {} {} via {}", request.amount, request.currency, provider.name)
        PaymentResult result = provider.receiveMoney(request, method)
        recordReceive(request, method, result, tenant)
        return result
    }

    /**
     * Refreshes a transaction's status from the chain: a broadcast tx that reached a validated ledger/block
     * moves to CONFIRMED (or FAILED). Terminal transactions are returned unchanged (idempotent).
     */
    PaymentResult refresh(String transactionId) {
        Transaction transaction = transactionRepository.get(transactionId)
        if (!transaction) {
            throw new IllegalArgumentException("Transaction not found: ${transactionId}")
        }
        // Receives are confirmed via confirm-receive / stripe/confirm (their externalReference is an address
        // or session id, not a chain reference), so chain refresh applies only to sends.
        if (transaction.type == TransactionType.RECEIVE) {
            return resultOf(transaction)
        }
        if (!TransactionStatus.terminal(transaction.status) && transaction.externalReference) {
            PaymentProvider provider = registry.get(transaction.provider)
            if (provider instanceof ConfirmableProvider) {
                String status = ((ConfirmableProvider) provider).checkStatus(transaction.externalReference)
                if (status && status != transaction.status) {
                    transaction.status = status
                    saveUpdate(transaction)
                    log.info("Refreshed {} {} -> {}", transaction.provider, transaction.id, status)
                }
            }
        }
        return resultOf(transaction)
    }

    /**
     * Confirms a receive by verifying a client-reported deposit on-chain: that it settled and paid at least
     * the expected amount to the owner's address (and destination tag for XRP). Marks the receive CONFIRMED.
     */
    PaymentResult confirmReceive(String transactionId, String depositReference) {
        if (!depositReference) {
            throw new IllegalArgumentException("depositTransaction is required")
        }
        Transaction transaction = transactionRepository.get(transactionId)
        if (!transaction) {
            throw new IllegalArgumentException("Transaction not found: ${transactionId}")
        }
        if (transaction.type != TransactionType.RECEIVE) {
            throw new IllegalArgumentException("Transaction ${transactionId} is not a receive")
        }
        if (TransactionStatus.terminal(transaction.status)) {
            return resultOf(transaction)
        }
        PaymentProvider provider = registry.get(transaction.provider)
        if (!(provider instanceof DepositVerifyingProvider)) {
            throw new UnsupportedOperationException("Provider ${provider.name} does not support deposit verification")
        }
        DepositVerifyingProvider verifying = (DepositVerifyingProvider) provider
        // Verify against the recipient snapshotted at receive time, NOT the (mutable) payment method, so an
        // edit to the method between receive and confirm cannot change what counts as a valid deposit.
        String address = transaction.recipientAddress
        Long destinationTag = transaction.recipientTag
        if (!address) {
            throw new IllegalStateException("Receive ${transactionId} has no recipient address to verify against")
        }
        // Tag-based rails (XRP) share one account disambiguated by destination tag; without a tag, a deposit
        // cannot be safely attributed to this owner (it would match any deposit to the address).
        if (verifying.requiresDestinationTag() && destinationTag == null) {
            throw new IllegalArgumentException("This receive requires a destination tag to attribute the deposit")
        }

        BigDecimal expectedAmount = transaction.amount != null ? new BigDecimal(transaction.amount) : null
        boolean verified = verifying
                .verifyDeposit(depositReference, address, expectedAmount, destinationTag)
        if (!verified) {
            throw new IllegalArgumentException("Deposit ${depositReference} does not match the expected payment for ${transactionId}")
        }
        transaction.status = TransactionStatus.CONFIRMED
        transaction.externalReference = depositReference
        saveUpdate(transaction)
        log.info("Confirmed receive {} from deposit {}", transactionId, depositReference)
        return resultOf(transaction)
    }

    /**
     * Confirms a Stripe receive when its Checkout session completes. Called (via the controller) by the
     * trevorism Stripe service after it has verified the Stripe webhook signature. Matches the receive by the
     * session id it recorded as externalReference.
     */
    PaymentResult confirmStripeReceive(String sessionId) {
        if (!sessionId) {
            throw new IllegalArgumentException("sessionId is required")
        }
        List<Transaction> matches = transactionRepository.filter(new SimpleFilter("externalReference", FilterConstants.OPERATOR_EQUAL, sessionId))
        Transaction transaction = matches?.find { it.type == TransactionType.RECEIVE && it.provider == StripePaymentProvider.NAME }
        if (!transaction) {
            throw new IllegalArgumentException("No Stripe receive found for session ${sessionId}")
        }
        if (TransactionStatus.terminal(transaction.status)) {
            return resultOf(transaction)
        }
        transaction.status = TransactionStatus.CONFIRMED
        saveUpdate(transaction)
        log.info("Confirmed Stripe receive {} for session {}", transaction.id, sessionId)
        return resultOf(transaction)
    }

    List<Transaction> getAllTransactions() {
        return transactionRepository.list()
    }

    Transaction getTransaction(String id) {
        return transactionRepository.get(id)
    }

    private PaymentMethod loadMethod(String paymentMethodId) {
        if (!paymentMethodId) {
            throw new IllegalArgumentException("paymentMethodId is required")
        }
        PaymentMethod method = null
        try {
            method = paymentMethodRepository.get(paymentMethodId)
        } catch (Exception e) {
            // datastore-client cannot distinguish not-found from an outage here; log so an incident is not
            // silently misreported as a 400 "not found".
            log.warn("Failed to load payment method {}: {}", paymentMethodId, e.message)
        }
        if (!method || !method.id) {
            throw new IllegalArgumentException("Payment method not found: ${paymentMethodId}")
        }
        return method
    }

    private void saveUpdate(Transaction transaction) {
        transaction.dateUpdated = new Date()
        transactionRepository.update(transaction.id, transaction)
    }

    private static PaymentResult resultOf(Transaction transaction) {
        PaymentResult result = new PaymentResult(transaction.provider, transaction.status)
        result.externalReference = transaction.externalReference
        return result
    }

    private static void requirePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero")
        }
    }

    /**
     * Idempotency for submit: a transaction that was already broadcast (SUBMITTED) or settled (CONFIRMED)
     * returns its recorded outcome instead of broadcasting again (prevents double money movement on retries).
     * A FAILED transaction is allowed to be re-submitted (the signed tx is still valid until it expires);
     * an EXPIRED one is rejected.
     */
    private PaymentResult replayIfAlreadyProcessed(Transaction transaction) {
        if (transaction.status == TransactionStatus.EXPIRED) {
            throw new IllegalStateException("Prepared transaction ${transaction.id} has expired; prepare a new one")
        }
        if (transaction.status == TransactionStatus.SUBMITTED || transaction.status == TransactionStatus.CONFIRMED) {
            log.info("Idempotent replay of already-submitted transaction {} ({})", transaction.id, transaction.status)
            return resultOf(transaction)
        }
        // PREPARED, FAILED, or null -> allow (re)submission.
        return null
    }

    private void ensureNotExpired(Transaction transaction) {
        if (transaction.expiresAt != null && new Date().after(transaction.expiresAt)) {
            transaction.status = TransactionStatus.EXPIRED
            saveUpdate(transaction)
            throw new IllegalStateException("Prepared transaction ${transaction.id} has expired; prepare a new one")
        }
    }

    private static SignableTransferProvider asSignable(PaymentProvider provider) {
        if (!(provider instanceof SignableTransferProvider)) {
            throw new UnsupportedOperationException("Provider ${provider.name} does not support non-custodial send")
        }
        return (SignableTransferProvider) provider
    }

    private void recordReceive(PaymentRequest request, PaymentMethod method, PaymentResult result, String tenant) {
        Transaction transaction = new Transaction(
                type: TransactionType.RECEIVE,
                provider: result.provider,
                paymentMethodId: method.id,
                ownerId: method.ownerId,
                tenant: tenant,
                amount: request.amount?.toPlainString(),
                currency: request.currency,
                status: result.status,
                externalReference: result.externalReference,
                recipientAddress: method.address,
                recipientTag: method.destinationTag,
                dateCreated: new Date()
        )
        transactionRepository.create(transaction)
    }
}
