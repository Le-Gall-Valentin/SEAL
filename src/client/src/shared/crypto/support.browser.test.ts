import { describe, expect, it } from 'vitest'
import { checkCryptoSupport } from './support'

describe('KEY-01 : contrôle des algorithmes dans Chromium', () => {
  it('KEY-01 : le Chromium des tests propose Ed25519 et X25519', async () => {
    await expect(checkCryptoSupport()).resolves.toBe(true)
  })
})
