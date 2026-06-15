<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import axios from 'axios'
import { fetchNetworks, pollStatus } from '../session'
import { availableWallets } from '../wallets'

// One non-custodial send form for any supported network/wallet. The user picks a network (cardano-preprod,
// cardano-mainnet, xrp-testnet, xrp-mainnet); that determines the chain, which filters the wallet list and
// the sign path. The wallet signs in its own UI; the API never holds a key.

const networks = ref([])
const networkKey = ref(null)
const wallets = ref([])
const walletId = ref(null)
const recipientAddress = ref('')
const destinationTag = ref('')             // XRP only
const senderAddress = ref('')
const amount = ref(1)

const methodId = ref('')
const transactionId = ref('')
const unsignedPayload = ref('')
const result = ref(null)
const confirmation = ref('')

const busy = ref(false)
const error = ref('')
const warning = ref('')
const connected = ref(false)

// Owner is defaulted until there's a vendor/customer UI to choose one.
const DEFAULT_OWNER_ID = 'default'

const selectedNetwork = computed(() => networks.value.find((n) => n.key === networkKey.value))
const chain = computed(() => selectedNetwork.value?.chain)
const expectedWalletNetwork = computed(() => selectedNetwork.value?.walletNetwork)
const isXrp = computed(() => chain.value === 'xrp')

const adapter = computed(() => wallets.value.find((w) => w.id === walletId.value))
const walletsForChain = computed(() => wallets.value.filter((w) => w.chain === chain.value))
const networkOptions = computed(() => networks.value.map((n) => ({ text: n.label, value: n.key })))
const walletOptions = computed(() => walletsForChain.value.map((w) => ({ text: w.label, value: w.id })))
// Only ask the user to choose when more than one wallet matches the chain; with a single wallet we
// auto-select it (pickWalletForChain) and just show which one will be used.
const hasWallets = computed(() => walletsForChain.value.length > 0)
const multipleWallets = computed(() => walletsForChain.value.length > 1)

function pickWalletForChain() {
  const match = walletsForChain.value
  walletId.value = match.length ? match[0].id : null
}

// Re-scan the browser for injected wallets. Cardano extensions inject window.cardano.* asynchronously,
// often after this component mounts, so a one-shot scan can miss them. We rescan on mount with a few
// delayed retries and expose a manual button for late installs.
function rescan() {
  const current = walletId.value
  wallets.value = availableWallets()
  if (walletsForChain.value.some((w) => w.id === current)) return  // keep the selection if still valid
  pickWalletForChain()
}

onMounted(async () => {
  networks.value = await fetchNetworks()
  if (networks.value.length) networkKey.value = networks.value[0].key
  rescan()
  // Catch wallet extensions that inject after first paint.
  for (const delay of [400, 1200]) setTimeout(rescan, delay)
})

function resetFlow() {
  connected.value = false; senderAddress.value = ''
  methodId.value = ''; transactionId.value = ''; unsignedPayload.value = ''
  result.value = null; confirmation.value = ''; error.value = ''; warning.value = ''
}

// Changing the network changes the chain: reset the flow and re-pick a wallet for the new chain.
watch(networkKey, () => { resetFlow(); pickWalletForChain() })
// Changing the wallet resets the flow (a new wallet means a new connection/signature).
watch(walletId, () => { resetFlow() })

function fail(message) { error.value = message; busy.value = false }

async function connect() {
  error.value = ''
  warning.value = ''
  busy.value = true
  try {
    const info = await adapter.value.connect()
    // Network guard (when the wallet reports one): block before any signing on a mismatch.
    if (expectedWalletNetwork.value && info.network && info.network !== expectedWalletNetwork.value) {
      return fail(`Wallet is on the wrong network. Switch it to ${expectedWalletNetwork.value} and reconnect.`)
    }
    // Wallet couldn't tell us its network (e.g. Crossmark): can't guard, so warn instead of silently passing.
    if (expectedWalletNetwork.value && info.networkUnverified) {
      warning.value = `Could not verify the wallet's network. Make sure it is set to ${expectedWalletNetwork.value} before signing.`
    }
    if (info.address) senderAddress.value = info.address   // XRP wallets report the address; Cardano is pasted
    connected.value = true
  } catch (e) {
    return fail('Connect failed: ' + (e.message || e))
  }
  busy.value = false
}

async function createMethod() {
  error.value = ''
  busy.value = true
  try {
    // The network-qualified key is the provider discriminator the API routes on (e.g. "xrp-testnet").
    const body = { provider: networkKey.value, ownerId: DEFAULT_OWNER_ID, address: recipientAddress.value }
    if (isXrp.value) {
      // The backend requires a destination tag to attribute an XRP receive (PaymentService.confirmReceive),
      // so block here rather than letting the method strand downstream.
      if (destinationTag.value === '' || destinationTag.value == null) {
        return fail('Destination tag is required for an XRP payment method.')
      }
      body.destinationTag = Number(destinationTag.value)
    }
    const { data } = await axios.post('/api/payment-method', body)
    methodId.value = data.id
  } catch (e) {
    return fail('Create method failed: ' + (e.response?.status || '') + ' ' + (e.message || e))
  }
  busy.value = false
}

async function prepare() {
  error.value = ''
  busy.value = true
  result.value = null
  confirmation.value = ''
  try {
    const { data } = await axios.post('/api/payment/send/prepare', {
      senderAddress: senderAddress.value,
      paymentMethodId: methodId.value,
      amount: String(amount.value)
    })
    transactionId.value = data.transactionId
    unsignedPayload.value = data.unsignedPayload
  } catch (e) {
    return fail('Prepare failed: ' + (e.response?.status || '') + ' ' + (e.message || e))
  }
  busy.value = false
}

// Sign with the chosen wallet, then submit. The adapter normalizes the wallet's output to a 'witness'
// (Cardano CIP-30) or 'blob' (XRP) which routes to the matching endpoint — the form stays wallet-agnostic.
async function signAndSubmit() {
  error.value = ''
  busy.value = true
  try {
    const signed = await adapter.value.sign(unsignedPayload.value)
    const endpoint = signed.kind === 'witness' ? '/api/payment/send/submit-witness' : '/api/payment/send/submit'
    const payload = signed.kind === 'witness'
        ? { transactionId: transactionId.value, witness: signed.value }
        : { transactionId: transactionId.value, signedTransaction: signed.value }
    const { data } = await axios.post(endpoint, payload)
    result.value = data
    confirmation.value = data.status
    pollStatus(transactionId.value, (s) => (confirmation.value = s))
  } catch (e) {
    return fail('Sign/submit failed: ' + (e.message || JSON.stringify(e)))
  }
  busy.value = false
}
</script>

<template>
  <va-card class="pay-form">
    <va-card-title>Send a payment</va-card-title>
    <va-card-content>
      <va-select
        v-model="networkKey"
        class="mb-3"
        label="Network"
        :options="networkOptions"
        value-by="value"
        text-by="text"
        no-options-text="No networks available"
      />
      <div class="wallet-row mb-3">
        <!-- Picker only when more than one wallet is available for the chain; otherwise auto-selected. -->
        <va-select
          v-if="multipleWallets"
          v-model="walletId"
          label="Wallet"
          :options="walletOptions"
          value-by="value"
          text-by="text"
          class="wallet-select"
        />
        <p v-else-if="hasWallets" class="wallet-auto">Wallet: <strong>{{ adapter?.label }}</strong></p>
        <p v-else class="wallet-auto wallet-none">No supported wallet detected for this network.</p>
        <va-button preset="secondary" icon="refresh" @click="rescan" title="Rescan for installed wallets">
          Rescan
        </va-button>
      </div>
      <va-alert color="info" class="mb-3" border="left">
        Network <strong>{{ selectedNetwork?.label || '...' }}</strong> — wallet must be on
        <strong>{{ expectedWalletNetwork || '...' }}</strong>. The wallet signs; the API never holds a key.
      </va-alert>
      <va-alert v-if="warning" color="warning" class="mb-3" border="left">{{ warning }}</va-alert>

      <va-button :disabled="connected || busy || !adapter" @click="connect" class="mb-3">
        {{ connected ? 'Wallet connected' : 'Connect wallet' }}
      </va-button>
      <p v-if="senderAddress" class="ok">sender: {{ senderAddress }}</p>

      <h4 class="step">1. Recipient payment method</h4>
      <va-input v-model="recipientAddress" label="Recipient address" class="mb-2" />
      <va-input v-if="isXrp" v-model="destinationTag" label="Destination tag" type="number" class="mb-2" />
      <va-button :disabled="busy || !recipientAddress" @click="createMethod" preset="secondary">Create method</va-button>
      <p v-if="methodId" class="ok">method id: {{ methodId }}</p>

      <h4 class="step">2. Prepare</h4>
      <va-input v-if="!isXrp" v-model="senderAddress" label="Your sender address" class="mb-2" />
      <va-input v-model="amount" :label="isXrp ? 'Amount (XRP)' : 'Amount (ADA)'" type="number" class="mb-2" />
      <va-button :disabled="busy || !methodId || !senderAddress" @click="prepare" preset="secondary">Prepare</va-button>
      <p v-if="transactionId" class="ok">transactionId: {{ transactionId }}</p>

      <h4 class="step">3. Sign &amp; submit</h4>
      <va-button :disabled="busy || !connected || !unsignedPayload" @click="signAndSubmit" color="success">
        Sign &amp; submit
      </va-button>

      <va-alert v-if="result" color="success" class="mt-3" border="left">
        status: {{ confirmation }} — txHash: {{ result.externalReference }}
      </va-alert>
      <va-alert v-if="error" color="danger" class="mt-3" border="left">{{ error }}</va-alert>
    </va-card-content>
  </va-card>
</template>

<style scoped>
.pay-form { max-width: 640px; margin: 1.5rem auto; }
.wallet-row { display: flex; gap: 0.5rem; align-items: flex-end; }
.wallet-select { flex: 1; }
.wallet-auto { flex: 1; margin: 0; align-self: center; }
.wallet-none { color: var(--va-warning); }
.step { margin-top: 1rem; margin-bottom: 0.4rem; }
.ok { color: var(--va-success); font-family: 'Source Code Pro', monospace; font-size: 0.8rem; word-break: break-all; }
.mb-2 { margin-bottom: 0.5rem; }
.mb-3 { margin-bottom: 0.75rem; }
.mt-3 { margin-top: 0.75rem; }
</style>
