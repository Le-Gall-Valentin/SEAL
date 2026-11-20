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
import { CanonicalReader } from './canonicalReader'

const ID = '0192f7a1-ffff-7000-8000-00000000a11c'
const LABEL = 'SEAL-test-v1'
const UTF8 = new TextEncoder()

function sample(): Uint8Array {
  return struct(
    LABEL,
    bytes(Uint8Array.of(1, 2)),
    uuid(ID.toUpperCase()),
    text('éa'),
    u64(1_794_009_600_000),
    u8(3),
    list(uuid, [ID, ID]),
  )
}

/** Lit l'exemple entier, fin comprise. */
function readSample(encoded: Uint8Array): void {
  const reader = CanonicalReader.of(encoded, LABEL)
  reader.bytes()
  reader.uuid()
  reader.text()
  reader.u64()
  reader.u8()
  reader.list(() => reader.uuid())
  reader.end()
}

describe('MSG-01 : relecture de l’encodage canonique (PROTOCOL.md, section 2), symétrique de canonical.ts', () => {
  it('MSG-01 : chaque primitive se relit à l’identique, puis la fin est exigée', () => {
    const reader = CanonicalReader.of(sample(), LABEL)

    expect(reader.bytes()).toEqual(Uint8Array.of(1, 2))
    expect(reader.uuid()).toBe(ID)
    expect(reader.text()).toBe('éa')
    expect(reader.u64()).toBe(1_794_009_600_000n)
    expect(reader.u8()).toBe(3)
    expect(reader.list(() => reader.uuid())).toEqual([ID, ID])
    reader.end()
  })

  it('MSG-01 : une autre étiquette est refusée', () => {
    expect(() => CanonicalReader.of(sample(), 'SEAL-autre-v1')).toThrow(CanonicalEncodingError)
  })

  it('MSG-01 : chaque troncature et un octet en trop sont refusés', () => {
    const encoded = sample()
    for (let length = 0; length < encoded.length; length += 1) {
      expect(
        () => {
          readSample(encoded.subarray(0, length))
        },
        `troncature à ${String(length)} octets`,
      ).toThrow(CanonicalEncodingError)
    }
    expect(() => {
      readSample(concat(encoded, Uint8Array.of(0)))
    }).toThrow(CanonicalEncodingError)
  })

  it('ADR 0006 : un u64 au-delà de 2^63 - 1, ou un nombre au-delà de 2^53 - 1, est refusé', () => {
    const huge = concat(text(LABEL), Uint8Array.of(0x80, 0, 0, 0, 0, 0, 0, 0))
    const unsafe = concat(text(LABEL), u64(2n ** 53n))

    expect(() => CanonicalReader.of(huge, LABEL).u64()).toThrow(CanonicalEncodingError)
    expect(CanonicalReader.of(unsafe, LABEL).u64()).toBe(2n ** 53n)
    expect(() => CanonicalReader.of(unsafe, LABEL).safeU64()).toThrow(CanonicalEncodingError)
    expect(CanonicalReader.of(concat(text(LABEL), u64(42)), LABEL).safeU64()).toBe(42)
  })

  it('ADR 0006 : un texte en UTF-8 invalide, ou valide mais hors de la forme NFC, est refusé', () => {
    const invalid = concat(text(LABEL), bytes(Uint8Array.of(0xc3)))
    const decomposed = concat(text(LABEL), bytes(UTF8.encode('é')))

    expect(() => CanonicalReader.of(invalid, LABEL).text()).toThrow(CanonicalEncodingError)
    expect(() => CanonicalReader.of(decomposed, LABEL).text()).toThrow(CanonicalEncodingError)
  })

  it('MSG-01 : une liste annonçant plus d’éléments que d’octets restants est refusée sans les parcourir', () => {
    const encoded = concat(text(LABEL), u32(0xffff_ffff))

    expect(() => {
      const reader = CanonicalReader.of(encoded, LABEL)
      reader.list(() => reader.u8())
    }).toThrow(CanonicalEncodingError)
  })
})
