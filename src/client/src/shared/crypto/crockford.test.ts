import { describe, expect, it } from 'vitest'
import { CrockfordFormatError, decodeCrockford, encodeCrockford } from './crockford'

function fromHex(hex: string): Uint8Array {
  return Uint8Array.from({ length: hex.length / 2 }, (_, index) =>
    Number.parseInt(hex.slice(2 * index, 2 * index + 2), 16),
  )
}

// Références calculées par une implémentation indépendante (Python), alphabet de Douglas Crockford.
const VECTORS = [
  ['0000000000000000000000000000000000000000', '00000000000000000000000000000000'],
  ['ffffffffffffffffffffffffffffffffffffffff', 'ZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ'],
  ['000102030405060708090a0b0c0d0e0f10111213', '000G40R40M30E209185GR38E1W8124GK'],
  ['8f3a0c11d2e45b6799aa01fe2b3c4d5e6f708192', 'HWX0R4EJWHDPF6DA07Z2PF2DBSQQ10CJ'],
] as const

describe('KEY-30 : base32 Crockford', () => {
  for (const [hex, text] of VECTORS) {
    it(`KEY-30 : ${hex} s'écrit ${text}, et se relit à l'identique`, () => {
      expect(encodeCrockford(fromHex(hex))).toBe(text)
      expect(decodeCrockford(text)).toEqual(fromHex(hex))
    })
  }

  it('KEY-30 : la saisie ignore la casse, les espaces et les tirets, et lit O comme 0, I et L comme 1', () => {
    expect(decodeCrockford('hwx0-r4ej whdp-f6da 07z2-pf2d-bsqq-10cj')).toEqual(
      fromHex('8f3a0c11d2e45b6799aa01fe2b3c4d5e6f708192'),
    )
    expect(decodeCrockford('HWXOR4EJWHDPF6DAO7Z2PF2DBSQQ1OCJ')).toEqual(
      decodeCrockford('HWX0R4EJWHDPF6DA07Z2PF2DBSQQ10CJ'),
    )
    expect(decodeCrockford('IiLlIiLl')).toEqual(decodeCrockford('11111111'))
  })

  it('KEY-30 : U et tout caractère hors de l’alphabet sont refusés', () => {
    for (const text of ['U000', '000!', '000é', '00_0', '00.0']) {
      expect(() => decodeCrockford(text), text).toThrow(CrockfordFormatError)
    }
  })

  it('KEY-30 : une longueur qui ne tombe pas sur des octets entiers est refusée', () => {
    expect(() => encodeCrockford(new Uint8Array(3))).toThrow(CrockfordFormatError)
    expect(() => decodeCrockford('000')).toThrow(CrockfordFormatError)
  })
})
