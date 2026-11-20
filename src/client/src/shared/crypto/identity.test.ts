import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { CanonicalEncodingError } from './canonical'
import { verifyEd25519 } from './ed25519'
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
import {
  base64url,
  decimal,
  hex,
  type Json,
  number,
  record,
  records,
  string,
} from './testing/vectorReader'
import { ed25519Pkcs8 } from './testing/pkcs8'

// QA-01, QA-02 : vecteurs produits par src/protocol/tools/gen_vectors.py, relus aussi par les tests Java.
const IDENTITY = new URL('../../../../protocol/vectors/identity.json', import.meta.url)
const RFC8032 = new URL('../../../../protocol/vectors/ed25519-rfc8032.json', import.meta.url)

function kem(json: Json, field: string): Kem {
  const value = string(json, field)
  if (value !== KEM) throw new Error(`${field} : ${KEM} attendu`)
  return value
}

const identity = record(JSON.parse(readFileSync(IDENTITY, 'utf8')))
const rfc8032 = record(JSON.parse(readFileSync(RFC8032, 'utf8')))
const keys = record(identity['keys'])

function certificateFields(fields: Json): CertificateFields {
  return {
    serial: base64url(fields, 'serial'),
    issuerKeyId: base64url(fields, 'issuerKeyId'),
    subjectId: string(fields, 'subjectId'),
    username: string(fields, 'username'),
    ikPublic: base64url(fields, 'ikPublic'),
    notBefore: decimal(fields, 'notBefore'),
    notAfter: decimal(fields, 'notAfter'),
    usage: number(fields, 'usage'),
  }
}

function proofFields(fields: Json): ProofOfPossessionFields {
  return {
    userId: string(fields, 'userId'),
    ikPublic: base64url(fields, 'ikPublic'),
    challenge: base64url(fields, 'challenge'),
  }
}

function bindingFields(fields: Json): SubkeyBindingFields {
  return {
    userId: string(fields, 'userId'),
    certSerial: base64url(fields, 'certSerial'),
    ekId: base64url(fields, 'ekId'),
    kem: kem(fields, 'kem'),
    ekPublic: base64url(fields, 'ekPublic'),
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
  return base64url(record(keys[role]), 'publicKey')
}

/** QA-01 : la vérification du client, avec le refus des points d'ordre faible (ed25519.ts). */
function verifies(key: Uint8Array, signature: Uint8Array, data: Uint8Array): Promise<boolean> {
  return verifyEd25519(key, data, signature)
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

        expect(encoded).toEqual(base64url(vector, 'encoded'))
        await expect(
          verifies(publicKey(string(vector, 'signer')), base64url(vector, 'signature'), encoded),
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
        verifies(publicKey(string(forgery, 'verifier')), base64url(forgery, 'signature'), encoded),
      ).resolves.toBe(false)
    })
  }
})

describe('QA-01 : Ed25519 de WebCrypto sur la RFC 8032, section 7.1', () => {
  for (const test of records(rfc8032['tests'])) {
    it(`QA-01 : ${string(test, 'name')}, signature reproduite et vérifiée`, async () => {
      const pkcs8 = ed25519Pkcs8(hex(test, 'secretKey'))
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
