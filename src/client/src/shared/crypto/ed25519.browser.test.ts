import { describe, expect, it } from 'vitest'
import { commands } from 'vitest/browser'
import { isWeakOrNonCanonical, verifyEd25519 } from './ed25519'
import { base64url, hex, record, records, string } from './testing/vectorReader'

// QA-01, QA-04 : vecteurs produits par src/protocol/tools/gen_vectors.py, relus aussi par les tests Java. Ces tests
// tournent dans Chromium, où le client vérifie : son WebCrypto accepte seul des signatures d'ordre faible que celui de
// Node refuse déjà ; sous Node, retirer le contrôle d'ordre faible ne ferait échouer aucun test. Les vecteurs sont lus
// par Vitest côté Node : le client n'importe rien de src/protocol (ADR 0024, point 6 bis).
// Chemin relatif à la racine du client (src/client), comme le résout commands.readFile.
const VECTORS = '../protocol/vectors/'
const identity = record(JSON.parse(await commands.readFile(`${VECTORS}identity.json`)))
const rfc8032 = record(JSON.parse(await commands.readFile(`${VECTORS}ed25519-rfc8032.json`)))
const smallOrder = record(identity['smallOrder'])

/** Les 8 points d'ordre faible et 6 autres encodages des mêmes points (PROTOCOL.md, section 4). */
function weakEncodings(): Uint8Array<ArrayBuffer>[] {
  return [...encodings('points'), ...encodings('otherEncodings')]
}

function encodings(field: string): Uint8Array<ArrayBuffer>[] {
  const list: unknown = smallOrder[field]
  if (!Array.isArray(list)) throw new Error(`smallOrder.${field} : liste attendue`)
  return list.map((value: unknown) => base64url({ value }, 'value'))
}

function concat(...parts: Uint8Array[]): Uint8Array<ArrayBuffer> {
  const out = new Uint8Array(parts.reduce((length, part) => length + part.length, 0))
  let offset = 0
  for (const part of parts) {
    out.set(part, offset)
    offset += part.length
  }
  return out
}

const L = 2n ** 252n + 27742317777372353535851937790883648493n

function littleEndian(bytes: Uint8Array): bigint {
  return bytes.reduceRight((value, byte) => (value << 8n) | BigInt(byte), 0n)
}

function littleEndian32(value: bigint): Uint8Array<ArrayBuffer> {
  return Uint8Array.from({ length: 32 }, (_, index) => Number((value >> BigInt(8 * index)) & 0xffn))
}

async function sha512(...parts: Uint8Array[]): Promise<Uint8Array> {
  return new Uint8Array(await crypto.subtle.digest('SHA-512', concat(...parts)))
}

async function webCryptoAlone(
  key: Uint8Array<ArrayBuffer>,
  message: Uint8Array<ArrayBuffer>,
  signature: Uint8Array<ArrayBuffer>,
) {
  const imported = await crypto.subtle.importKey('raw', key, { name: 'Ed25519' }, false, ['verify'])
  return crypto.subtle.verify({ name: 'Ed25519' }, imported, signature, message)
}

describe('QA-01 : vérification Ed25519 du client', () => {
  it('QA-01 : chaque test de la RFC 8032, section 7.1, est vérifié avec sa clé publique brute', async () => {
    const tests = records(rfc8032['tests'])
    expect(tests.length).toBeGreaterThan(0)
    for (const test of tests) {
      await expect(
        verifyEd25519(hex(test, 'publicKey'), hex(test, 'message'), hex(test, 'signature')),
        string(test, 'name'),
      ).resolves.toBe(true)
    }
  })

  it('QA-04 : un octet changé dans le message ou la signature fait échouer la vérification', async () => {
    const test = records(rfc8032['tests'])[1]
    if (test === undefined) throw new Error('test 2 de la RFC 8032 attendu')
    const message = hex(test, 'message')
    const signature = hex(test, 'signature')
    const otherMessage = Uint8Array.from(message)
    otherMessage[0] = (otherMessage[0] ?? 0) ^ 1
    const otherSignature = Uint8Array.from(signature)
    otherSignature[40] = (otherSignature[40] ?? 0) ^ 1

    await expect(verifyEd25519(hex(test, 'publicKey'), otherMessage, signature)).resolves.toBe(
      false,
    )
    await expect(verifyEd25519(hex(test, 'publicKey'), message, otherSignature)).resolves.toBe(
      false,
    )
  })

  it('ADR 0025 : une clé ou une signature de mauvaise longueur, ou une clé hors de la courbe, est refusée sans exception', async () => {
    const test = records(rfc8032['tests'])[0]
    if (test === undefined) throw new Error('test 1 de la RFC 8032 attendu')
    const key = hex(test, 'publicKey')
    const message = hex(test, 'message')
    const signature = hex(test, 'signature')
    // y = 2 : encodage canonique, hors des points d'ordre faible, d'aucun point de la courbe : WebCrypto le refuse.
    const offCurve = concat(Uint8Array.of(2), new Uint8Array(31))

    expect(isWeakOrNonCanonical(offCurve)).toBe(false)
    await expect(verifyEd25519(offCurve, message, signature)).resolves.toBe(false)
    await expect(verifyEd25519(key.subarray(0, 31), message, signature)).resolves.toBe(false)
    await expect(verifyEd25519(concat(key, Uint8Array.of(0)), message, signature)).resolves.toBe(
      false,
    )
    await expect(verifyEd25519(key, message, signature.subarray(0, 63))).resolves.toBe(false)
    await expect(verifyEd25519(key, message, new Uint8Array())).resolves.toBe(false)
  })

  it('ADR 0025, point 14 : un encodage dont y ≥ p est non canonique, quel que soit le bit de signe', () => {
    expect(isWeakOrNonCanonical(new Uint8Array(32).fill(0xff))).toBe(true)
    expect(
      isWeakOrNonCanonical(
        concat(Uint8Array.of(0xed), new Uint8Array(30).fill(0xff), Uint8Array.of(0x7f)),
      ),
    ).toBe(true)
    expect(
      isWeakOrNonCanonical(
        concat(Uint8Array.of(0xeb), new Uint8Array(30).fill(0xff), Uint8Array.of(0x7f)),
      ),
    ).toBe(false)
  })

  it('QA-04, ADR 0025 : aucune clé ni aucun R d’ordre faible ne vérifie', async () => {
    const weak = weakEncodings()
    expect(weak).toHaveLength(14)
    const message = base64url(smallOrder, 'message')
    const genuine = hex(records(rfc8032['tests'])[0] ?? {}, 'publicKey')

    for (const point of weak) {
      expect(isWeakOrNonCanonical(point)).toBe(true)
      for (const r of weak) {
        await expect(verifyEd25519(point, message, concat(r, new Uint8Array(32)))).resolves.toBe(
          false,
        )
      }
      await expect(
        verifyEd25519(genuine, message, concat(point, new Uint8Array(32))),
      ).resolves.toBe(false)
    }
  })

  it('ADR 0025, point 14 : un R neutre, que WebCrypto seul accepte du titulaire de la clé, est refusé', async () => {
    // Clé du test 1 de la RFC 8032 : son titulaire calcule a, puis S = k·a mod L pour R = point neutre (r = 0).
    const test = records(rfc8032['tests'])[0]
    if (test === undefined) throw new Error('test 1 de la RFC 8032 attendu')
    const publicKey = hex(test, 'publicKey')
    const message = new TextEncoder().encode('SEAL')
    const scalar = (await sha512(hex(test, 'secretKey'))).slice(0, 32)
    scalar[0] = (scalar[0] ?? 0) & 0xf8
    scalar[31] = ((scalar[31] ?? 0) & 0x7f) | 0x40
    const neutral = concat(Uint8Array.of(1), new Uint8Array(31))
    const k = littleEndian(await sha512(neutral, publicKey, message)) % L
    const signature = concat(neutral, littleEndian32((k * littleEndian(scalar)) % L))

    await expect(
      webCryptoAlone(publicKey, message, signature),
      'WebCrypto seul l’accepte',
    ).resolves.toBe(true)
    await expect(verifyEd25519(publicKey, message, signature)).resolves.toBe(false)
  })
})
