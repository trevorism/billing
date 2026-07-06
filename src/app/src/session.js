import axios from 'axios'

export function isLoggedIn() {
  return document.cookie.split(';').some((c) => c.trim().startsWith('session='))
}

export async function fetchNetworks() {
  try {
    const { data } = await axios.get('/api/config')
    return data.networks || []
  } catch (e) {
    return []
  }
}

export function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

// Poll the chain-confirmation endpoint until the transaction reaches a terminal state (or attempts run out).
export async function pollStatus(transactionId, onStatus, attempts = 10, intervalMs = 4000) {
  for (let i = 0; i < attempts; i++) {
    await sleep(intervalMs)
    try {
      const { data } = await axios.post(`/api/payment/${transactionId}/refresh`)
      onStatus(data.status)
      if (['CONFIRMED', 'FAILED', 'EXPIRED'].includes(data.status)) {
        return data.status
      }
    } catch (e) {
      // transient; keep polling
    }
  }
  return null
}
