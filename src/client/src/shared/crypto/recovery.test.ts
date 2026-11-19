import { inspect } from 'node:util'
import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  generateRecoveryKey,
  parseRecoveryKey,
  pickRecoveryGroups,
  RecoveryKey,
  RecoveryKeyFormatError,
  recoveryKeyBytes,
} from './recovery'

afterEach(() => {
  vi.restoreAllMocks()
})

const TEXT = 'HWX0R4EJWHDPF6DA07Z2PF2DBSQQ10CJ'

/** crypto.getRandomValues doublé : chaque appel reçoit l'octet suivant ; rend la liste, vidée au fil des tirages. */
function stubRandomBytes(bytes: number[]): number[] {
  const draws = [...bytes]
  vi.spyOn(crypto, 'getRandomValues').mockImplementation(
    <T extends ArrayBufferView | null>(array: T): T => {
      if (array instanceof Uint8Array) array[0] = draws.shift() ?? 0
      return array
    },
  )
  return draws
}

describe('KEY-30, KEY-31 : clé de récupération', () => {
  it('KEY-30 : 20 octets aléatoires, montrés en 8 groupes de 4 caractères', () => {
    const key = generateRecoveryKey()

    expect(recoveryKeyBytes(key)).toHaveLength(20)
    expect(key.groups()).toHaveLength(8)
    for (const group of key.groups()) {
      expect(group).toMatch(/^[0-9A-HJKMNP-TV-Z]{4}$/)
    }
    expect(recoveryKeyBytes(generateRecoveryKey())).not.toEqual(recoveryKeyBytes(key))
  })

  it('KEY-30 : une clé saisie se relit en ses groupes, quelle que soit la façon de la taper', () => {
    const key = parseRecoveryKey('hwx0-r4ej-whdp-f6da-07z2-pf2d-bsqq-1ocj')

    expect(key.groups()).toEqual(['HWX0', 'R4EJ', 'WHDP', 'F6DA', '07Z2', 'PF2D', 'BSQQ', '10CJ'])
    expect(parseRecoveryKey(key.groups().join(' ')).groups()).toEqual(key.groups())
  })

  it('KEY-30 : 31 ou 33 caractères utiles, ou un caractère hors de l’alphabet, sont refusés', () => {
    for (const text of [TEXT.slice(1), `${TEXT}0`, `U${TEXT.slice(1)}`, '']) {
      expect(() => parseRecoveryKey(text), text).toThrow(RecoveryKeyFormatError)
    }
  })

  it('ADR 0027, point 15 : une saisie avec ı, ſ ou ß n’est pas lue comme une clé', () => {
    for (const text of [`${TEXT.slice(0, 31)}ı`, `ſ${TEXT.slice(1)}`, `${TEXT.slice(0, 30)}ß`]) {
      expect(() => parseRecoveryKey(text), text).toThrow(RecoveryKeyFormatError)
    }
  })

  it('KEY-30 : une clé de récupération fait 20 octets, même construite dans le segment', () => {
    expect(() => RecoveryKey.of(new Uint8Array(19))).toThrow(RangeError)
    expect(() => RecoveryKey.of(new Uint8Array(21))).toThrow(RangeError)
  })

  it('KEY-31 : la clé ne se sérialise pas en JSON, et ne s’affiche pas en texte', () => {
    const key = parseRecoveryKey(TEXT)

    expect(() => JSON.stringify({ key })).toThrow(TypeError)
    expect(String(key)).toBe('[RecoveryKey]')
    expect(Object.keys(key)).toEqual([])
  })

  it('KEY-31 : seule une clé construite par ce module a des octets', () => {
    const forged: unknown = Object.create(RecoveryKey.prototype)
    if (!(forged instanceof RecoveryKey)) throw new Error('prototype attendu')

    expect(() => recoveryKeyBytes(forged)).toThrow(TypeError)
  })

  it('KEY-31 : les octets rendus sont une copie : les modifier ne change pas la clé', () => {
    const key = parseRecoveryKey(TEXT)
    const bytes = recoveryKeyBytes(key)
    bytes.fill(0)

    expect(key.groups()[0]).toBe('HWX0')
  })
})

describe('KEY-30 : deux groupes à ressaisir, tirés au hasard', () => {
  it('KEY-30 : deux index distincts entre 0 et 7, dans l’ordre, chacun tiré à peu près aussi souvent', () => {
    const counts = new Array<number>(8).fill(0)
    for (let draw = 0; draw < 10_000; draw++) {
      const [first, second] = pickRecoveryGroups()
      expect(first).toBeGreaterThanOrEqual(0)
      expect(second).toBeLessThanOrEqual(7)
      expect(first).toBeLessThan(second)
      counts[first] = (counts[first] ?? 0) + 1
      counts[second] = (counts[second] ?? 0) + 1
    }
    // 20 000 index sur 8 valeurs : 2 500 chacun en moyenne, écart type d'environ 47.
    for (const count of counts) {
      expect(count).toBeGreaterThan(2_200)
      expect(count).toBeLessThan(2_800)
    }
  })

  it('KEY-30 : un octet qui biaiserait le tirage parmi les 7 index restants est rejeté puis retiré', () => {
    const draws = stubRandomBytes([5, 253, 3])

    // 5 donne le premier index ; 253 est rejeté (≥ 252, 36 × 7) ; 3 donne l'index 3, avant 5.
    expect(pickRecoveryGroups()).toEqual([3, 5])
    expect(draws).toEqual([])
  })

  it('KEY-30 : le second index saute le premier, pour rester distinct', () => {
    const draws = stubRandomBytes([2, 4])

    // 2 donne le premier index ; 4 désigne le cinquième des index restants (0, 1, 3, 4, 5…), soit 5.
    expect(pickRecoveryGroups()).toEqual([2, 5])
    expect(draws).toEqual([])
  })
})

describe('KEY-31 : aucune fuite de la clé de récupération, repris de la revue de sécurité', () => {
  it('KEY-31 : ni inspect, ni structuredClone, ni Reflect.ownKeys ne montrent ses octets', () => {
    const key = parseRecoveryKey(TEXT)

    expect(inspect(key, { showHidden: true, depth: 5 })).not.toContain('HWX0')
    expect(Reflect.ownKeys(key)).toEqual([])
    expect(() => recoveryKeyBytes(structuredClone(key))).toThrow(TypeError)
  })
})
