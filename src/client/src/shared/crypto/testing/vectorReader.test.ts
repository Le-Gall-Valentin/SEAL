import { describe, expect, it } from 'vitest'
import { base64url, decimal, hex, number, record, records, string } from './vectorReader'

describe('QA-02 : lecteur commun des vecteurs du protocole', () => {
  it('QA-02 : lit un objet, une liste d’objets et leurs champs typés', () => {
    const json = record({ name: 'a', count: 3, list: [{}, { x: 1 }] })

    expect(string(json, 'name')).toBe('a')
    expect(number(json, 'count')).toBe(3)
    expect(records(json['list'])).toHaveLength(2)
  })

  it('QA-02 : refuse un objet absent, une liste ou une valeur nulle à la place d’un objet', () => {
    expect(() => record(null)).toThrow('objet attendu')
    expect(() => record([])).toThrow('objet attendu')
    expect(() => record('x')).toThrow('objet attendu')
    expect(() => records({})).toThrow('liste attendue')
    expect(() => records([1])).toThrow('objet attendu')
  })

  it('QA-02 : refuse un champ absent ou d’un autre type', () => {
    const json = record({ name: 1, count: '3' })

    expect(() => string(json, 'name')).toThrow('name : chaîne attendue')
    expect(() => string(json, 'missing')).toThrow('missing : chaîne attendue')
    expect(() => number(json, 'count')).toThrow('count : nombre attendu')
  })

  it('ADR 0006 : un u64 est une chaîne décimale sans zéro de tête, entière et sûre en JavaScript', () => {
    const json = record({
      zero: '0',
      big: '9007199254740991',
      lead: '01',
      neg: '-1',
      huge: '9007199254740992',
    })

    expect(decimal(json, 'zero')).toBe(0)
    expect(decimal(json, 'big')).toBe(Number.MAX_SAFE_INTEGER)
    expect(() => decimal(json, 'lead')).toThrow('lead : u64 décimal attendu')
    expect(() => decimal(json, 'neg')).toThrow('neg : u64 décimal attendu')
    expect(() => decimal(json, 'huge')).toThrow('huge : u64 hors des entiers sûrs')
  })

  it('PROTOCOL.md 1 : octets en base64url sans remplissage, refusés sinon', () => {
    const json = record({
      ok: 'AP8',
      url: '-_-_',
      padded: 'AP8=',
      std: 'A+/A',
      short: 'AAAAA',
      empty: '',
    })

    expect(base64url(json, 'ok')).toEqual(Uint8Array.of(0x00, 0xff))
    expect(base64url(json, 'url')).toEqual(Uint8Array.of(0xfb, 0xff, 0xbf))
    expect(base64url(json, 'empty')).toEqual(new Uint8Array())
    expect(() => base64url(json, 'short')).toThrow('short : base64url sans remplissage attendu')
    expect(() => base64url(json, 'padded')).toThrow('padded : base64url sans remplissage attendu')
    expect(() => base64url(json, 'std')).toThrow('std : base64url sans remplissage attendu')
    expect(() => base64url(record({ loose: 'AB' }), 'loose')).toThrow(
      'loose : base64url sans remplissage attendu',
    )
  })

  it('QA-01 : octets en hexadécimal, de longueur paire', () => {
    const json = record({ ok: '00ff', odd: 'abc', bad: 'zz' })

    expect(hex(json, 'ok')).toEqual(Uint8Array.of(0x00, 0xff))
    expect(() => hex(json, 'odd')).toThrow('odd : hexadécimal attendu')
    expect(() => hex(json, 'bad')).toThrow('bad : hexadécimal attendu')
  })
})
