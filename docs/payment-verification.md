# Payment provider verification

> **Custody model:** crypto sends are **non-custodial**. The API never holds a private key or mnemonic. A
> send is two phases: `POST /api/payment/send/prepare` (the API builds an *unsigned* transaction from public
> data) → the client signs it in their own wallet → `POST /api/payment/send/submit` (the API broadcasts the
> signed blob). Receiving needs no key on any rail. Stripe *payouts* (sending USD) are inherently custodial
> and are not part of this flow — `prepare` on a Stripe method returns 501. See "Stripe send" below.

How to prove each payment provider works, from zero-cost mocked tests all the way to a real-money
production check. There are three layers; climb them in order.

| Layer | What it proves | Money | Network | When it runs |
|-------|----------------|-------|---------|--------------|
| 1. Demonstration unit tests | Provider wiring, validation, result mapping, transaction recording | None | None (mocked) | Every `gradle build` |
| 2. Testnet integration tests | Real transaction building, signing, submission on a test network | None (faucet) | Real testnet / test mode | Opt-in `gradle integrationTest` |
| 3. Production smoke | The live mainnet path moves real funds | **Real, tiny** | Real mainnet | Manual, gated runbook |

The seam that makes this possible: each crypto provider implements
[`SignableTransferProvider`](../src/main/groovy/com/trevorism/payment/SignableTransferProvider.groovy) with
`prepareTransfer(...)` (build unsigned) and `submitSignedTransfer(...)` (broadcast signed), plus
`createXrplClient()` / `createJsonRpcClient()` / `createBackendService()` factory methods. Layer 1 overrides
those to fake the network; layers 2 and 3 run the real thing against different networks. No method on any
provider ever takes a private key.

---

## Layer 1 — Demonstration unit tests (no money, no network)

These run in the normal build and demonstrate each provider with mocked wallets/backends:

- **Stripe** — `StripePaymentProviderTest` mocks the `SecureHttpClient` and shows a checkout session being
  created from a receive request; also asserts Stripe is **not** a `SignableTransferProvider`.
- **XRP** — `XrpPaymentProviderTest#testSubmitSignedTransferDemonstrationWithMockedRpc` fakes the JSON-RPC
  client to return a canned rippled `submit` response and asserts the parsed hash + engine result.
- **Cardano** — `CardanoPaymentProviderTest#testSubmitSignedTransferDemonstrationWithMockedBackend` fakes the
  backend's transaction service and asserts the parsed tx hash + status.
- **Cross-provider** — `PaymentServiceTest` / `PaymentControllerTest` demonstrate the two-phase send
  (prepare records a `PREPARED` transaction; submit broadcasts and finalizes it) and that a Stripe send
  yields 501.

Run:

```bash
gradle build        # or: gradle test
```

What this layer does NOT prove: that the real xrpl4j / cardano-client-lib transaction building, client
signing, and broadcast produce a valid, accepted transaction. That is Layer 2's job.

---

## Layer 2 — Testnet integration tests (faucet money, real network)

Tagged `@Tag("integration")`, excluded from the normal build, run with a dedicated task:

```bash
gradle integrationTest
```

With no environment variables set, every integration test **skips** (so the task is always safe to run).
Provide the env vars below to actually exercise a provider. No real money is involved — testnets use faucet
funds and Stripe runs in test mode.

For the crypto ITs, the test plays **both roles** to prove the production flow end to end:
`provider.prepareTransfer` (API builds unsigned) → the test signs locally with the test wallet (this is the
**client's** key, used only inside the test — the API/provider never receives it) → `provider.submitSignedTransfer`
(API broadcasts). The seed/mnemonic env vars are the *client's* wallet, not the platform's.

### Stripe (test mode)
The provider calls the deployed `stripe.trevorism.com` service, which must be configured with a Stripe
**test** key. Authentication uses the trevorism `clientId`/`clientSecret` in
`src/main/resources/secrets.properties`.

```bash
export RUN_STRIPE_IT=true
gradle integrationTest --tests '*StripePaymentProviderIT'
```

### XRP (testnet / altnet)
1. Get a funded testnet account from the [XRP Testnet Faucet](https://xrpl.org/xrp-testnet-faucet.html).
   It gives you a seed (`sEd...`) and an address (`r...`). Generate a second one to use as the destination.

```bash
export XRP_TEST_SEED=sEd...funded...
export XRP_TEST_DESTINATION=r...second-account...
gradle integrationTest --tests '*XrpPaymentProviderIT'
```

Expect `engineResult=tesSUCCESS` and a transaction hash in the output.

### Cardano (preprod)
The integration test defaults to the **Koios** backend, which is **free and needs no API key** — so no
Blockfrost project is required for testnet. (Blockfrost project ids are per-network; a mainnet project
cannot be used against preprod.)

1. Fund a preprod address from the [Cardano Faucet](https://docs.cardano.org/cardano-testnets/tools/faucet)
   and have its 24-word mnemonic. The destination can be that same address (Cardano allows self-payment),
   so a single funded wallet is enough.

```bash
export CARDANO_TEST_MNEMONIC="word1 word2 ... word24"
export CARDANO_TEST_DESTINATION=addr_test1...        # may be the sender's own address
gradle integrationTest --tests '*CardanoPaymentProviderIT'
```

Expect `status=SUBMITTED` and a transaction hash in the output.

To use Blockfrost instead of Koios, also `export BLOCKFROST_PROJECT_ID=preprod...` (a **preprod** project);
the test switches to Blockfrost automatically when that variable is present. The provider itself selects the
backend via `cardano.backend` (`koios` or `blockfrost`) in `application.yml`.

---

## Clickable wallet demos (localhost)

Browser demos of the full non-custodial send, one per chain, served by the API at `/`
(`src/app/src/components/LacePayment.vue` and `XrpPayment.vue`). The browser never handles a key.

Run both:
```powershell
$env:MICRONAUT_SECURITY_ENABLED = "false"   # demo only; payment endpoints are @Secure otherwise
.\gradlew.bat run
# open http://localhost:8080
```
Each demo is the same four clicks: Connect wallet → Create method (recipient) → Prepare → Sign → Submit. The
result shows the tx hash; verify on the relevant explorer.

### Cardano — Lace (CIP-30)
Lace on the **Preprod** testnet. Flow: `prepare` (API builds the unsigned tx) → `lace.signTx(unsigned, true)`
(returns a **witness set**) → `send/submit-witness` (the API assembles the witness with the unsigned tx it
kept, server-side via `WitnessAssemblingProvider`, then broadcasts). No JS serialization library needed.
Verify on preprod.cexplorer.io.

### XRP — GemWallet
GemWallet (`@gemwallet/api`) on the **Testnet**; the connect step auto-fills your sender address. Flow:
`prepare` (API builds the unsigned XRPL tx JSON) → `gemWallet.signTransaction({ transaction })` (returns a
signed **tx_blob**) → `send/submit` (the API broadcasts the blob). Verify on testnet.xrpl.org.

Notes:
- A wallet only prompts when the page calls its sign method — a backend cannot push a transaction to a wallet,
  which is why these dApp pages are required.
- Security is disabled here only so the browser can call the endpoints without a Trevorism JWT. Don't do this
  in a deployed environment.
- The two chains differ at signing: Lace returns a witness set (assembled server-side); GemWallet returns a
  full signed blob (broadcast directly). Both ride the same `prepare` → sign → submit shape.

---

## Compliance & limits

Technical controls implemented in this repo (the legal/policy parts are external — see below):

- **Per-transaction limits** — `payment.limits.{xrp,cardano,stripe}` cap the amount per send/receive in native
  units (`0` = unlimited; set caps for production). Enforced in `ComplianceService.enforceLimit`.
- **Address screening** — `payment.screening.denylist` blocks payouts to listed addresses; checked on every
  send destination (`ComplianceService.screen`). This is a **seam** for a real sanctions/AML provider (OFAC
  list, Chainalysis, etc.) which would replace the denylist implementation.
- **Record-keeping / audit trail** — every operation persists a `Transaction` (type, provider, owner, tenant,
  sender, amount, currency, status, externalReference, `dateCreated`/`dateUpdated`) and status changes are
  recorded, never deleted.

External / out-of-code obligations to address before live operation (policy, not implemented here):
**KYC/identity verification**, a real **sanctions/AML screening provider**, **velocity limits** (per-tenant
per-period), **transaction reporting**, and any **money-transmission licensing**. The screening seam and the
audit trail are the integration points for these.

## Layer 3 — Production smoke with real money

Only after Layer 2 passes for a provider. This moves **real funds on mainnet**, so treat it as a controlled,
reversible-as-possible, minimal check rather than an automated test.

### Configuration (production) — no platform wallet keys
Because crypto sends are non-custodial, the API holds **no** wallet keys/mnemonics in production.

**Environments.** There is no per-environment profile — the app runs identically locally and in production.
All four crypto networks are served at once (`xrp-testnet`, `xrp-mainnet`, `cardano-preprod`, `cardano-mainnet`);
the client picks one per payment method via its network-qualified `provider`. `application.yml` is the only
config file.

- **XRP** — `xrp.testnet.rpcUrl` (altnet) and `xrp.mainnet.rpcUrl` (default `https://xrplcluster.com/`; prefer a
  dedicated node for production — override with the `XRP_MAINNET_RPCURL` env var). No seed, no platform address.
- **Cardano** — `cardano.backend: koios` (keyless) for both preprod and mainnet, or `blockfrost` with a
  `blockfrostProjectId` for a higher SLA. No mnemonic.
- **Mainnet caps** — set real per-transaction limits on the mainnet providers before moving real money:
  `payment.limits.xrp-mainnet` / `payment.limits.cardano-mainnet` (native units; `0` = unlimited).
- **Stripe (receive)** — point the trevorism Stripe service at a **live** Stripe key (its own deploy, out of scope here).

**Secrets.** Managed the trevorism way: the shared `trevorism/actions-workflows` deploy workflow injects
`secrets.properties` from GitHub repo secrets (`CLIENT_ID`, `CLIENT_SECRET`, `SIGNING_KEY`) at deploy time —
no secrets in the repo. Add `BLOCKFROST_PROJECT_ID` to the workflow secrets only if you switch Cardano to the
Blockfrost backend. (A Blockfrost project id is a read/submit API token, not a wallet key.)

### Guardrails for the first real transaction
1. **Smallest possible amount.** e.g. ~1 XRP, ~1 ADA, the Stripe minimum (~$0.50–$1.00).
2. **Send to an address you control** (an internal/treasury wallet), so funds are recoverable.
3. **One rail at a time**, via the live API. Crypto send is two phases; the client signs in between with
   their own wallet:
   ```
   POST /api/payment/send/prepare  { "senderAddress": "<client wallet>", "paymentMethodId": "<recipient>", "amount": 1 }
     -> { transactionId, unsignedPayload, signingFormat }
   # client signs unsignedPayload in their wallet ->  signedTransaction
   POST /api/payment/send/submit   { "transactionId": "<id>", "signedTransaction": "<signed blob/cbor>" }
   POST /api/payment/receive       { "paymentMethodId": "<a real method id>", "amount": 1 }
   ```
4. **Verify on a block explorer** using the returned `externalReference`:
   - XRP: livenet explorer (e.g. livenet.xrpl.org) → confirm `tesSUCCESS` and the destination/amount.
   - Cardano: a mainnet explorer (e.g. cexplorer.io) → confirm the tx and output.
   - Stripe: the Stripe Dashboard (live mode) → confirm the PaymentIntent/session.
5. **Confirm the audit record.** `GET /api/payment/{transactionId}` (or `GET /api/payment`) should show the
   `Transaction` going `PREPARED` → submitted status, with the matching provider, amount, and `externalReference`.
6. **Reconcile**, then scale amounts up only once the round trip is confirmed.

### Stripe receive confirmation (webhook)
Stripe receive is confirmed by webhook, but billing cannot verify Stripe signatures (it has no Stripe key —
the `trevorism/stripe` service does). So the flow is:

1. Billing `receive` (stripe) records a `RECEIVE` transaction with `externalReference` = the Checkout **session id**.
2. The customer pays; Stripe fires `checkout.session.completed` to the **stripe service** (`/api/billing/webhook`),
   which verifies the signature.
3. The stripe service then makes a trusted internal call to billing
   `POST /api/payment/stripe/confirm { "sessionId": "cs_..." }` (secured: `@Secure(SYSTEM, allowInternal=true)`).
4. Billing finds the receive by session id and marks it `CONFIRMED`.

**Required `trevorism/stripe` change (not in this repo):** on `checkout.session.completed`, forward the
session id to billing's `/api/payment/stripe/confirm` with an internal token. Until that forwarding is added,
Stripe receives stay `PENDING` (no funds are lost; confirmation just isn't recorded). The billing side is
complete and tested.

### Stripe send (USD payouts)
Stripe payouts do not fit the non-custodial model: there is no client wallet to sign — funds move from the
platform's own Stripe balance using the platform's Stripe secret key (a server-side credential, not an
end-user key). So `POST /api/payment/send/prepare` on a Stripe method returns **501**. If USD payouts are
needed later, add a separate, explicitly platform-custodial feature (e.g. Stripe Connect transfers) using the
platform's Stripe key — kept distinct from the non-custodial crypto path.

### Rollback / safety notes
- Crypto sends are irreversible — the "send to an address you control" rule is the safety net.
- The API custodies no crypto funds; clients control their own wallets and keys.
- Stripe charges (receive) can be refunded from the dashboard if needed.
