import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { CanonicalEncodingError } from './canonical'
import {
  type CertificateFields,
  encodeCertificate,
  encodeProofOfPossession,
  encodeSubkeyBinding,
  KEM,
  type Kem,
  type ProofOfPossessionFields,
  type SubkeyBindingFields,
} from './identity'

// QA-01, QA-02 : vecteurs produits par src/protocol/tools/gen_vectors.py, relus aussi par les tests Java.
const IDENTITY = new URL('../../../../protocol/vectors/identity.json', import.meta.url)
const RFC8032 = new URL('../../../../protocol/vectors/ed25519-rfc8032.json', import.meta.url)

type Json = Record<string, unknown>

function isRecord(value: unknown): value is Json {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function record(value: unknown): Json {
  if (!isRecord(value)) throw new Error('objet attendu')
  return value
}

function records(value: unknown): Json[] {
  if (!Array.isArray(value)) throw new Error('liste attendue')
  return value.map(record)
}

function string(json: Json, field: string): string {
  const value = json[field]
  if (typeof value !== 'string') throw new Error(`${field} : chaîne attendue`)
  return value
}

function number(json: Json, field: string): number {
  const value = json[field]
  if (typeof value !== 'number') throw new Error(`${field} : nombre attendu`)
  return value
}

/** ADR 0006, PROTOCOL.md 2.5 : un u64 est une chaîne décimale, que JSON.parse ne peut arrondir. */
function decimal(json: Json, field: string): number {
  const value = string(json, field)
  if (!/^(0|[1-9][0-9]*)$/.test(value)) throw new Error(`${field} : u64 décimal attendu`)
  return Number(value)
}

function kem(json: Json, field: string): Kem {
  const value = string(json, field)
  if (value !== KEM) throw new Error(`${field} : ${KEM} attendu`)
  return value
}

function b64url(json: Json, field: string): Uint8Array<ArrayBuffer> {
  return Uint8Array.from(Buffer.from(string(json, field), 'base64url'))
}

function hex(json: Json, field: string): Uint8Array<ArrayBuffer> {
  return Uint8Array.from(Buffer.from(string(json, field), 'hex'))
}

const identity = record(JSON.parse(readFileSync(IDENTITY, 'utf8')))
const rfc8032 = record(JSON.parse(readFileSync(RFC8032, 'utf8')))
const keys = record(identity['keys'])

function certificateFields(fields: Json): CertificateFields {
  return {
    serial: b64url(fields, 'serial'),
    issuerKeyId: b64url(fields, 'issuerKeyId'),
    subjectId: string(fields, 'subjectId'),
    username: string(fields, 'username'),
    ikPublic: b64url(fields, 'ikPublic'),
    notBefore: decimal(fields, 'notBefore'),
    notAfter: decimal(fields, 'notAfter'),
    usage: number(fields, 'usage'),
  }
}

function proofFields(fields: Json): ProofOfPossessionFields {
  return {
    userId: string(fields, 'userId'),
    ikPublic: b64url(fields, 'ikPublic'),
    challenge: b64url(fields, 'challenge'),
  }
}

function bindingFields(fields: Json): SubkeyBindingFields {
  return {
    userId: string(fields, 'userId'),
    certSerial: b64url(fields, 'certSerial'),
    ekId: b64url(fields, 'ekId'),
    kem: kem(fields, 'kem'),
    ekPublic: b64url(fields, 'ekPublic'),
    createdAt: decimal(fields, 'createdAt'),
    expiresAt: decimal(fields, 'expiresAt'),
  }
}

const ENCODERS: Record<string, (fields: Json) => Uint8Array> = {
  certificate: (fields) => encodeCertificate(certificateFields(fields)),
  proof: (fields) => encodeProofOfPossession(proofFields(fields)),
  subkeyBinding: (fields) => encodeSubkeyBinding(bindingFields(fields)),
}

/** Section des vecteurs, structure encodée et exigence qu'elle sert. */
const SECTIONS = [
  { section: 'certificates', structure: 'certificate', requirement: 'KEY-04' },
  { section: 'proofs', structure: 'proof', requirement: 'KEY-03' },
  { section: 'subkeyBindings', structure: 'subkeyBinding', requirement: 'KEY-05' },
] as const

const REQUIREMENTS: Record<string, string> = Object.fromEntries(
  SECTIONS.map(({ structure, requirement }) => [structure, requirement]),
)

function publicKey(role: string): Uint8Array<ArrayBuffer> {
  return b64url(record(keys[role]), 'publicKey')
}

async function verifies(
  key: Uint8Array<ArrayBuffer>,
  signature: Uint8Array<ArrayBuffer>,
  data: Uint8Array,
): Promise<boolean> {
  const imported = await crypto.subtle.importKey('raw', key, { name: 'Ed25519' }, false, ['verify'])
  return crypto.subtle.verify({ name: 'Ed25519' }, imported, signature, Uint8Array.from(data))
}

describe('QA-02 : structures signées de l’identité (PROTOCOL.md, section 4)', () => {
  // Les tests naissent des vecteurs : une section vidée ferait disparaître ses tests sans échec.
  it('QA-02 : chaque section des vecteurs a au moins un cas, et chaque structure une falsification', () => {
    const forgeries = records(identity['forgeries'])
    for (const { section, structure } of SECTIONS) {
      expect(records(identity[section]).length, section).toBeGreaterThan(0)
      expect(
        forgeries.filter((forgery) => string(forgery, 'structure') === structure).length,
        structure,
      ).toBeGreaterThan(0)
    }
    expect(records(rfc8032['tests']).length).toBeGreaterThan(0)
  })

  for (const { section, structure, requirement } of SECTIONS) {
    for (const vector of records(identity[section])) {
      it(`${requirement}, QA-02 : ${string(vector, 'name')}, encodé à l’octet près et signé`, async () => {
        const encoded = ENCODERS[structure]?.(record(vector['fields']))
        if (encoded === undefined) throw new Error(`structure inconnue : ${structure}`)

        expect(encoded).toEqual(b64url(vector, 'encoded'))
        await expect(
          verifies(publicKey(string(vector, 'signer')), b64url(vector, 'signature'), encoded),
        ).resolves.toBe(true)
      })
    }
  }

  for (const forgery of records(identity['forgeries'])) {
    const structure = string(forgery, 'structure')
    it(`${REQUIREMENTS[structure] ?? structure}, QA-04 : ${string(forgery, 'name')}, refusé`, async () => {
      const encoder = ENCODERS[structure]
      if (encoder === undefined) throw new Error('structure inconnue')
      const encoded = encoder(record(forgery['fields']))

      await expect(
        verifies(publicKey(string(forgery, 'verifier')), b64url(forgery, 'signature'), encoded),
      ).resolves.toBe(false)
    })
  }
})

describe('QA-01 : Ed25519 de WebCrypto sur la RFC 8032, section 7.1', () => {
  // En-tête PKCS#8 d'une clé privée Ed25519 (RFC 8410), suivi de la graine de 32 octets.
  const PKCS8_PREFIX = Uint8Array.from(Buffer.from('302e020100300506032b657004220420', 'hex'))

  for (const test of records(rfc8032['tests'])) {
    it(`QA-01 : ${string(test, 'name')}, signature reproduite et vérifiée`, async () => {
      const pkcs8 = new Uint8Array([...PKCS8_PREFIX, ...hex(test, 'secretKey')])
      const privateKey = await crypto.subtle.importKey('pkcs8', pkcs8, { name: 'Ed25519' }, false, [
        'sign',
      ])
      const signature = new Uint8Array(
        await crypto.subtle.sign({ name: 'Ed25519' }, privateKey, hex(test, 'message')),
      )

      expect(signature).toEqual(hex(test, 'signature'))
      await expect(verifies(hex(test, 'publicKey'), signature, hex(test, 'message'))).resolves.toBe(
        true,
      )
    })
  }
})

describe('KEY-04 : bornes des champs', () => {
  const fields = certificateFields(record(records(identity['certificates'])[0]?.['fields']))

  it('KEY-04 : une date hors du domaine u64 est refusée', () => {
    expect(() => encodeCertificate({ ...fields, notBefore: -1 })).toThrow(CanonicalEncodingError)
    expect(() => encodeCertificate({ ...fields, notAfter: 1.5 })).toThrow(CanonicalEncodingError)
  })

  it('KEY-04 : un usage hors d’un octet est refusé', () => {
    expect(() => encodeCertificate({ ...fields, usage: 256 })).toThrow(CanonicalEncodingError)
  })
})
