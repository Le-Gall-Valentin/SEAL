import { describe, expect, it } from 'vitest'
import { Base64UrlError, fromBase64Url, toBase64Url } from './base64url'

describe('PROTOCOL.md 1 : octets en base64url sans remplissage', () => {
  it('PROTOCOL.md 1 : encode et relit à l’identique, alphabet URL compris', () => {
    for (const bytes of [
      new Uint8Array(),
      Uint8Array.of(0),
      Uint8Array.of(0xfb, 0xff, 0xbf),
      Uint8Array.of(1, 2, 3, 4, 5),
    ]) {
      expect(fromBase64Url(toBase64Url(bytes))).toEqual(bytes)
    }
    expect(toBase64Url(Uint8Array.of(0xfb, 0xff, 0xbf))).toBe('-_-_')
    expect(toBase64Url(Uint8Array.of(0, 0xff))).toBe('AP8')
  })

  it('PROTOCOL.md 1 : le remplissage, l’alphabet standard et une longueur impossible sont refusés', () => {
    for (const text of ['AP8=', 'A+/A', 'AAAAA', 'A A', 'é']) {
      expect(() => fromBase64Url(text), text).toThrow(Base64UrlError)
    }
  })
})
