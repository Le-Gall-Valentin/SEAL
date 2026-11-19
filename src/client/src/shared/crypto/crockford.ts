/**
 * Base32 de Douglas Crockford (KEY-30), interne à shared/crypto : 5 bits par caractère, poids fort d'abord, sans
 * remplissage. À la saisie, la casse, les espaces et les tirets sont ignorés, O se lit 0, I et L se lisent 1 ; U et tout
 * autre caractère sont refusés.
 */
const ALPHABET = '0123456789ABCDEFGHJKMNPQRSTVWXYZ'
const CONFUSABLE: Readonly<Record<string, string>> = { O: '0', I: '1', L: '1' }
const SEPARATORS = /[\s-]/g
/** ADR 0027, point 15 : chiffres et lettres ASCII seulement, avant toute mise en majuscules. */
const ASCII_ALPHANUMERIC = /^[0-9A-Za-z]*$/

export class CrockfordFormatError extends Error {
  override readonly name = 'CrockfordFormatError'
}

export function encodeCrockford(bytes: Uint8Array): string {
  if ((bytes.length * 8) % 5 !== 0) {
    throw new CrockfordFormatError(
      'base32 Crockford : la taille doit tomber sur des caractères entiers',
    )
  }
  let text = ''
  let buffer = 0
  let bits = 0
  for (const byte of bytes) {
    buffer = (buffer << 8) | byte
    bits += 8
    while (bits >= 5) {
      bits -= 5
      text += ALPHABET.charAt((buffer >> bits) & 31)
    }
    buffer &= (1 << bits) - 1
  }
  return text
}

export function decodeCrockford(input: string): Uint8Array<ArrayBuffer> {
  const compact = input.replace(SEPARATORS, '')
  // toUpperCase() changerait ı en I, ſ en S, ß en SS : tout caractère non ASCII est refusé avant.
  if (!ASCII_ALPHANUMERIC.test(compact)) {
    throw new CrockfordFormatError('base32 Crockford : caractère hors de l’alphabet')
  }
  const text = compact.toUpperCase()
  if ((text.length * 5) % 8 !== 0) {
    throw new CrockfordFormatError(
      'base32 Crockford : la taille doit tomber sur des octets entiers',
    )
  }
  const bytes = new Uint8Array((text.length * 5) / 8)
  let buffer = 0
  let bits = 0
  let index = 0
  for (const char of text) {
    const value = ALPHABET.indexOf(CONFUSABLE[char] ?? char)
    if (value < 0) {
      throw new CrockfordFormatError('base32 Crockford : caractère hors de l’alphabet')
    }
    buffer = (buffer << 5) | value
    bits += 5
    if (bits >= 8) {
      bits -= 8
      bytes[index++] = (buffer >> bits) & 0xff
    }
    buffer &= (1 << bits) - 1
  }
  return bytes
}
