/**
 * Octets en base64url sans remplissage (PROTOCOL.md, section 1), interne à shared/crypto. Le décodage est strict : ni
 * remplissage, ni alphabet standard, ni longueur impossible.
 */
const ALPHABET = /^[A-Za-z0-9_-]*$/

export class Base64UrlError extends Error {
  override readonly name = 'Base64UrlError'
}

export function toBase64Url(bytes: Uint8Array): string {
  let binary = ''
  for (const byte of bytes) {
    binary += String.fromCharCode(byte)
  }
  return btoa(binary).replaceAll('+', '-').replaceAll('/', '_').replace(/=+$/, '')
}

export function fromBase64Url(text: string): Uint8Array<ArrayBuffer> {
  if (!ALPHABET.test(text) || text.length % 4 === 1) {
    throw new Base64UrlError('base64url sans remplissage attendu')
  }
  const base64 = text
    .replaceAll('-', '+')
    .replaceAll('_', '/')
    .padEnd(Math.ceil(text.length / 4) * 4, '=')
  return Uint8Array.from(atob(base64), (char) => char.charCodeAt(0))
}
