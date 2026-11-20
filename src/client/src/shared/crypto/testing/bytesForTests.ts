/** Outils d'octets des tests. Réservé aux tests. */
import { fromBase64Url, toBase64Url } from '../base64url'

/** Les octets d'un texte hexadécimal. */
export function fromHex(value: string): Uint8Array<ArrayBuffer> {
  return Uint8Array.from(value.match(/../g) ?? [], (pair) => Number.parseInt(pair, 16))
}

/**
 * RFC 7748 et RFC 9180 (7.1.4) : coordonnées u d'ordre faible de X25519, d'après la liste de libsodium, plus p et
 * p + 1, et leurs variantes au bit de poids fort levé.
 */
export const SMALL_ORDER_X25519: readonly (readonly [string, string])[] = [
  ['0', '0000000000000000000000000000000000000000000000000000000000000000'],
  ['1', '0100000000000000000000000000000000000000000000000000000000000000'],
  ['ordre 8 (a)', 'e0eb7a7c3b41b8ae1656e3faf19fc46ada098deb9c32b1fd866205165f49b800'],
  ['ordre 8 (b)', '5f9c95bca3508c24b1d0b1559c83ef5b04445cc4581c8e86d8224eddd09f1157'],
  ['p - 1', 'ecffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f'],
  ['p', 'edffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f'],
  ['p + 1', 'eeffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f'],
  ['ordre 8 (a), bit haut', 'e0eb7a7c3b41b8ae1656e3faf19fc46ada098deb9c32b1fd866205165f49b880'],
  ['0, bit haut', '0000000000000000000000000000000000000000000000000000000000000080'],
  ['1, bit haut', '0100000000000000000000000000000000000000000000000000000000000080'],
]

/** Le texte base64url `text`, l'octet `index` inversé (bit de poids faible). */
export function flipBase64Url(text: string, index: number): string {
  const bytes = fromBase64Url(text)
  const view = new DataView(bytes.buffer)
  view.setUint8(index, view.getUint8(index) ^ 0x01)
  return toBase64Url(bytes)
}
