import { describe, it, expect, vi, beforeEach } from 'vitest'
import axios from 'axios'
import { isLoggedIn, fetchNetworks, sleep, pollStatus } from '../src/session.js'

vi.mock('axios', () => ({
  default: { get: vi.fn(), post: vi.fn() }
}))

describe('session.js', () => {
  beforeEach(() => {
    axios.get.mockReset()
    axios.post.mockReset()
    document.cookie = 'user_name=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
  })

  describe('isLoggedIn', () => {
    it('is false when no user_name cookie is present', () => {
      expect(isLoggedIn()).toBe(false)
    })

    it('is true when a user_name cookie is present', () => {
      document.cookie = 'user_name=alice'
      expect(isLoggedIn()).toBe(true)
    })
  })

  describe('fetchNetworks', () => {
    it('returns the networks array from the config endpoint', async () => {
      const networks = [{ key: 'xrp-testnet', chain: 'xrp', walletNetwork: 'testnet' }]
      axios.get.mockResolvedValueOnce({ data: { networks } })
      expect(await fetchNetworks()).toEqual(networks)
      expect(axios.get).toHaveBeenCalledWith('/api/config')
    })

    it('returns an empty array when the request fails', async () => {
      axios.get.mockRejectedValueOnce(new Error('boom'))
      expect(await fetchNetworks()).toEqual([])
    })

    it('returns an empty array when the payload has no networks', async () => {
      axios.get.mockResolvedValueOnce({ data: {} })
      expect(await fetchNetworks()).toEqual([])
    })
  })

  describe('sleep', () => {
    it('resolves after the given delay', async () => {
      await expect(sleep(1)).resolves.toBeUndefined()
    })
  })

  describe('pollStatus', () => {
    it('stops and returns once a terminal status is reached', async () => {
      axios.post
        .mockResolvedValueOnce({ data: { status: 'PENDING' } })
        .mockResolvedValueOnce({ data: { status: 'CONFIRMED' } })
      const seen = []
      const result = await pollStatus('tx-1', (s) => seen.push(s), 5, 1)
      expect(result).toBe('CONFIRMED')
      expect(seen).toEqual(['PENDING', 'CONFIRMED'])
      expect(axios.post).toHaveBeenCalledWith('/api/payment/tx-1/refresh')
    })

    it('returns null when the status never becomes terminal within the attempt budget', async () => {
      axios.post.mockResolvedValue({ data: { status: 'PENDING' } })
      const result = await pollStatus('tx-2', () => {}, 3, 1)
      expect(result).toBeNull()
      expect(axios.post).toHaveBeenCalledTimes(3)
    })

    it('keeps polling through transient request errors', async () => {
      axios.post
        .mockRejectedValueOnce(new Error('network blip'))
        .mockResolvedValueOnce({ data: { status: 'FAILED' } })
      const result = await pollStatus('tx-3', () => {}, 5, 1)
      expect(result).toBe('FAILED')
    })
  })
})
