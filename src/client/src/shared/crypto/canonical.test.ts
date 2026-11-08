import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import {
  bytes,
  CanonicalEncodingError,
  concat,
  list,
  struct,
  text,
  u32,
  u64,
  u8,
  uuid,
} from './canonical'

// MSG-02 : vecteurs produits par src/protocol/tools/gen_vectors.py, relus aussi par les tests Java.
const VECTORS = new URL('../../../../protocol/vectors/canonical-encoding.json', import.meta.url)

type Descriptor =
  | { type: 'u8' | 'u32'; value: number }
  | { type: 'u64' | 'bytes' | 'text' | 'uuid'; value: string }
  | { type: 'list'; items: Descriptor[] }
  | { type: 'concat'; parts: Descriptor[] }
  | { type: 'struct'; label: string; fields: Descriptor[] }

interface Vector {
  name: string
  input: Descriptor
  expected?: string
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function children(value: unknown): Descriptor[] {
  if (!Array.isArray(value)) throw new Error('liste de descripteurs attendue')
  return value.map(parseDescriptor)
}

/** Valide la forme d'un descripteur du fichier de vecteurs, sans rien supposer de JSON.parse. */
function parseDescriptor(value: unknown): Descriptor {
  if (!isRecord(value)) throw new Error('descripteur attendu')
  const { type } = value
  switch (type) {
    case 'u8':
    case 'u32':
      if (typeof value['value'] !== 'number') throw new Error(`${type} : nombre attendu`)
      return { type, value: value['value'] }
    case 'u64':
    case 'bytes':
    case 'text':
    case 'uuid':
      if (typeof value['value'] !== 'string') throw new Error(`${type} : chaîne attendue`)
      return { type, value: value['value'] }
    case 'list':
      return { type, items: children(value['items']) }
    case 'concat':
      return { type, parts: children(value['parts']) }
    case 'struct':
      if (typeof value['label'] !== 'string') throw new Error('struct : étiquette attendue')
      return { type, label: value['label'], fields: children(value['fields']) }
    default:
      throw new Error(`type inconnu : ${String(type)}`)
  }
}

function parseVectors(section: 'valid' | 'invalid'): Vector[] {
  const document: unknown = JSON.parse(readFileSync(VECTORS, 'utf-8'))
  if (!isRecord(document) || !Array.isArray(document[section])) {
    throw new Error(`section ${section} absente`)
  }
  return document[section].map((entry: unknown) => {
    if (!isRecord(entry) || typeof entry['name'] !== 'string') throw new Error('cas mal formé')
    const expected = entry['expected']
    return {
      name: entry['name'],
      input: parseDescriptor(entry['input']),
      ...(typeof expected === 'string' ? { expected } : {}),
    }
  })
}

function fromBase64Url(value: string): Uint8Array<ArrayBuffer> {
  return Uint8Array.from(Buffer.from(value, 'base64url'))
}

function toBase64Url(value: Uint8Array): string {
  return Buffer.from(value).toString('base64url')
}

function hex(value: Uint8Array): string {
  return Buffer.from(value).toString('hex')
}

/** Traduit un descripteur en appels à l'encodeur. */
function encode(descriptor: Descriptor): Uint8Array {
  switch (descriptor.type) {
    case 'u8':
      return u8(descriptor.value)
    case 'u32':
      return u32(descriptor.value)
    case 'u64':
      return u64(BigInt(descriptor.value))
    case 'bytes':
      return bytes(fromBase64Url(descriptor.value))
    case 'text':
      return text(descriptor.value)
    case 'uuid':
      return uuid(descriptor.value)
    case 'list':
      return list(encode, descriptor.items)
    case 'concat':
      return concat(...descriptor.parts.map(encode))
    case 'struct':
      return struct(descriptor.label, ...descriptor.fields.map(encode))
  }
}

describe('MSG-02 : vecteurs de l’encodage canonique', () => {
  const valid = parseVectors('valid')
  const invalid = parseVectors('invalid')

  it('MSG-02 : le fichier de vecteurs contient des cas valides et des cas à rejeter', () => {
    expect(valid.length).toBeGreaterThan(0)
    expect(invalid.length).toBeGreaterThan(0)
  })

  it.each(valid)('MSG-01 : $name', ({ input, expected }) => {
    expect(toBase64Url(encode(input))).toBe(expected)
  })

  it.each(invalid)('ADR 0006, rejeté : $name', ({ input }) => {
    expect(() => encode(input)).toThrow(CanonicalEncodingError)
  })
})

describe('encodeur canonique, cas propres à TypeScript', () => {
  it('MSG-01 : l’étiquette ouvre la structure, puis les champs dans l’ordre donné', () => {
    expect(hex(struct('L', u8(1), u8(2)))).toBe('000000014c0102')
    expect(hex(struct('L', u8(2), u8(1)))).not.toBe('000000014c0102')
  })

  it('MSG-01 : un champ absent s’encode comme une chaîne d’octets vide', () => {
    expect(hex(bytes(new Uint8Array()))).toBe('00000000')
    expect(text('')).toEqual(bytes(new Uint8Array()))
  })

  it('ADR 0006 : u64 accepte un number entier sûr et le même entier en bigint', () => {
    expect(u64(1_794_009_600_000)).toEqual(u64(1_794_009_600_000n))
  })

  it('ADR 0006 : u64 rejette un number au-delà de 2^53-1, qui a perdu sa précision', () => {
    expect(() => u64(2 ** 53)).toThrow(CanonicalEncodingError)
  })

  it.each([1.5, Number.NaN, Number.POSITIVE_INFINITY])(
    'ADR 0006 : les entiers rejettent une valeur non entière (%s)',
    (value) => {
      expect(() => u8(value)).toThrow(CanonicalEncodingError)
      expect(() => u32(value)).toThrow(CanonicalEncodingError)
      expect(() => u64(value)).toThrow(CanonicalEncodingError)
    },
  )

  it('MSG-01 : bytes copie son entrée : la modifier ensuite ne change pas l’encodage', () => {
    const input = Uint8Array.of(1, 2, 3)
    const encoded = bytes(input)

    input[0] = 9

    expect(hex(encoded)).toBe('00000003010203')
  })

  it('MSG-01 : list encode le nombre d’éléments puis chaque élément avec l’encodeur donné', () => {
    expect(hex(list(u64, [1n, 2n]))).toBe('0000000200000000000000010000000000000002')
  })

  it('MSG-01 : list passe chaque élément seul à son encodeur, sans son index ni le tableau', () => {
    // map(struct) passerait l'index et le tableau comme champs de la structure.
    expect(list(struct, ['a', 'b'])).toEqual(concat(u32(2), struct('a'), struct('b')))
  })

  it('ADR 0006 : CanonicalEncodingError se reconnaît par son nom', () => {
    expect(() => u8(-1)).toThrow(expect.objectContaining({ name: 'CanonicalEncodingError' }))
  })
})
