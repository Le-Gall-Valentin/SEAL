import { afterEach, describe, expect, it, vi } from 'vitest'
import { isWeakOrNonCanonical, verifyEd25519 } from './ed25519'

afterEach(() => {
  vi.restoreAllMocks()
})

// Les vecteurs et l'ordre faible se testent dans Chromium (ed25519.browser.test.ts). Ici, le seul chemin qu'aucun des
// deux moteurs ne prend : un navigateur qui refuse la clé dès l'import, comme Firefox (DataError).
describe('QA-01 : vérification Ed25519, refus de WebCrypto', () => {
  it('ADR 0025 : une clé refusée par WebCrypto à l’import donne faux, sans exception', async () => {
    const importKey = vi
      .spyOn(crypto.subtle, 'importKey')
      .mockRejectedValueOnce(new DOMException('point invalide', 'DataError'))
    // y = 2 pour la clé et pour R : ni d'ordre faible ni non canoniques, ils atteignent WebCrypto.
    const key = new Uint8Array(32)
    key[0] = 2
    const signature = new Uint8Array(64)
    signature[0] = 2

    await expect(verifyEd25519(key, Uint8Array.of(1), signature)).resolves.toBe(false)
    expect(importKey).toHaveBeenCalledOnce()
  })

  it('ADR 0025, point 14 : moins de 32 octets ne forment aucun point, et sont refusés', () => {
    expect(isWeakOrNonCanonical(new Uint8Array(31).fill(2))).toBe(true)
    expect(isWeakOrNonCanonical(new Uint8Array())).toBe(true)
  })
})
