/**
 * QA-01 : vecteurs officiels de HKDF-SHA256 (RFC 5869, annexe A) et de X25519 (RFC 7748, section 6.1), rejoués par le
 * WebCrypto de Node et par celui de Chromium, sur les opérations dont le client se sert : HKDF de la KEK (6.4), clé
 * publique relue dans le JWK d'une clé privée (ADR 0027, point 3) et échange X25519. Réservé aux tests.
 */
import { describe, expect, it } from 'vitest'
import { hex, type Json, number, record, records, string } from './vectorReader'

/** En-tête PKCS#8 d'une clé privée X25519 (RFC 8410), suivi des 32 octets de la clé. */
const X25519_PKCS8_PREFIX = Uint8Array.from([
  0x30, 0x2e, 0x02, 0x01, 0x00, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x6e, 0x04, 0x22, 0x04, 0x20,
])

function x25519Pkcs8(privateKey: Uint8Array): Uint8Array<ArrayBuffer> {
  const pkcs8 = new Uint8Array(X25519_PKCS8_PREFIX.length + privateKey.length)
  pkcs8.set(X25519_PKCS8_PREFIX)
  pkcs8.set(privateKey, X25519_PKCS8_PREFIX.length)
  return pkcs8
}

function fromBase64UrlText(text: string): Uint8Array {
  return Uint8Array.from(atob(text.replaceAll('-', '+').replaceAll('_', '/')), (char) =>
    char.charCodeAt(0),
  )
}

export function describeOfficialVectors(hkdf: Json, x25519: Json, engine: string): void {
  describe(`QA-01 : vecteurs officiels de HKDF-SHA256 et de X25519 (${engine})`, () => {
    const tests = records(hkdf['tests'])
    it('QA-01 : les trois cas de la RFC 5869 sont présents', () => {
      expect(tests).toHaveLength(3)
    })
    for (const test of tests) {
      it(`QA-01 : ${string(test, 'name')}`, async () => {
        const ikm = await crypto.subtle.importKey('raw', hex(test, 'ikm'), 'HKDF', false, [
          'deriveBits',
        ])
        const okm = await crypto.subtle.deriveBits(
          { name: 'HKDF', hash: 'SHA-256', salt: hex(test, 'salt'), info: hex(test, 'info') },
          ikm,
          number(test, 'length') * 8,
        )

        expect(new Uint8Array(okm)).toEqual(hex(test, 'okm'))
      })
    }

    const alice = record(x25519['alice'])
    const bob = record(x25519['bob'])

    it('QA-01 : RFC 7748, 6.1 : la clé publique se relit dans le JWK de la clé privée', async () => {
      for (const party of [alice, bob]) {
        const key = await crypto.subtle.importKey(
          'pkcs8',
          x25519Pkcs8(hex(party, 'privateKey')),
          'X25519',
          true,
          ['deriveBits'],
        )
        const { x } = await crypto.subtle.exportKey('jwk', key)
        if (x === undefined) throw new Error('JWK sans x')

        expect(fromBase64UrlText(x)).toEqual(hex(party, 'publicKey'))
      }
    })

    it('QA-01 : RFC 7748, 6.1 : Alice et Bob dérivent le même secret K', async () => {
      for (const [own, peer] of [
        [alice, bob],
        [bob, alice],
      ] as const) {
        const privateKey = await crypto.subtle.importKey(
          'pkcs8',
          x25519Pkcs8(hex(own, 'privateKey')),
          'X25519',
          false,
          ['deriveBits'],
        )
        const publicKey = await crypto.subtle.importKey(
          'raw',
          hex(peer, 'publicKey'),
          'X25519',
          true,
          [],
        )
        const shared = await crypto.subtle.deriveBits(
          { name: 'X25519', public: publicKey },
          privateKey,
          256,
        )

        expect(new Uint8Array(shared)).toEqual(hex(x25519, 'sharedSecret'))
      }
    })
  })
}
