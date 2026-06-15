// Wallet adapter seam. Each adapter normalizes a wallet to the same shape so the form (and the API) stay
// wallet-agnostic:
//   { id, chain, label, connect() -> { address?, network? }, sign(unsignedPayload) -> { kind, value } }
// kind 'witness' -> POST /api/payment/send/submit-witness ; kind 'blob' -> POST /api/payment/send/submit.
//
// Cardano support is universal via CIP-30 (any installed wallet is discovered). XRP support is per-connector
// (GemWallet, Crossmark) since XRPL has no single injected standard.
import { isInstalled, getAddress, getNetwork, signTransaction } from '@gemwallet/api'
import crossmark from '@crossmarkio/sdk'

// Normalize a wallet-reported network to the same vocabulary the API uses ('mainnet'/'testnet'/...).
// Anything we don't recognize is passed through lowercased rather than defaulted to 'testnet', so a
// wallet on an unexpected network (Devnet, custom) fails the guard instead of silently passing it.
function normalizeNetwork(raw) {
  const label = (typeof raw === 'string' ? raw : raw?.type || raw?.label || raw?.protocol || '')
      .toString().toLowerCase()
  if (!label) return null
  if (label.includes('main')) return 'mainnet'
  if (label.includes('test')) return 'testnet'
  if (label.includes('dev')) return 'devnet'
  return label
}

// --- Cardano: generic CIP-30 (Lace, Eternl, Nami, Typhon, Yoroi, ...) ---
function cardanoCip30Adapters() {
  const cardano = (typeof window !== 'undefined' && window.cardano) ? window.cardano : {}
  return Object.keys(cardano)
    .filter((key) => {
      const w = cardano[key]
      return w && typeof w.enable === 'function' && w.apiVersion
    })
    .map((key) => {
      const w = cardano[key]
      let api = null
      return {
        id: 'cardano:' + key,
        chain: 'cardano',
        label: (w.name || key) + ' (Cardano)',
        async connect() {
          api = await w.enable()
          const networkId = await api.getNetworkId() // 0 = testnet, 1 = mainnet
          return { network: networkId === 1 ? 'mainnet' : 'testnet' }
        },
        async sign(unsignedPayload) {
          // CIP-30 returns a witness set; the API assembles it with the prepared tx server-side.
          const witness = await api.signTx(unsignedPayload, true)
          return { kind: 'witness', value: witness }
        }
      }
    })
}

// --- XRP: GemWallet ---
function gemWalletAdapter() {
  return {
    id: 'xrp:gemwallet',
    chain: 'xrp',
    label: 'GemWallet (XRP)',
    async connect() {
      const installed = await isInstalled()
      if (!installed.result?.isInstalled) throw new Error('GemWallet is not installed')
      const net = await getNetwork()
      const network = normalizeNetwork(net.result?.network)
      const addr = await getAddress()
      if (!addr.result?.address) throw new Error('GemWallet connection rejected')
      return { address: addr.result.address, network }
    },
    async sign(unsignedPayload) {
      const resp = await signTransaction({ transaction: JSON.parse(unsignedPayload) })
      if (!resp.result?.signature) throw new Error('GemWallet signing rejected')
      return { kind: 'blob', value: resp.result.signature }
    }
  }
}

// --- XRP: Crossmark ---
function crossmarkAdapter() {
  return {
    id: 'xrp:crossmark',
    chain: 'xrp',
    label: 'Crossmark (XRP)',
    async connect() {
      const signIn = await crossmark.methods.signInAndWait()
      const data = signIn?.response?.data
      const address = data?.address || signIn?.response?.address
      if (!address) throw new Error('Crossmark connection rejected')
      // Read the network when Crossmark reports it; if it doesn't, flag it unverified so the form warns
      // the user to check the network manually (rather than silently skipping the guard).
      const network = normalizeNetwork(data?.network ?? signIn?.response?.network)
      return network ? { address, network } : { address, networkUnverified: true }
    },
    async sign(unsignedPayload) {
      const { response } = await crossmark.async.signAndWait(JSON.parse(unsignedPayload))
      const blob = response?.data?.txBlob || response?.data?.tx_blob || response?.data?.signed
      if (!blob) throw new Error('Crossmark did not return a signed tx_blob: ' + JSON.stringify(response?.data ?? {}))
      return { kind: 'blob', value: blob }
    }
  }
}

// All adapters currently available in the browser. Cardano wallets are discovered dynamically; XRP connectors
// are listed unconditionally and validate availability on connect().
export function availableWallets() {
  return [...cardanoCip30Adapters(), gemWalletAdapter(), crossmarkAdapter()]
}
