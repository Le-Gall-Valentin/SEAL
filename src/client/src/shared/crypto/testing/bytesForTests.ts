/** Outils d'octets des tests. Réservé aux tests. */
import { fromBase64Url, toBase64Url } from '../base64url'

/** Le texte base64url `text`, l'octet `index` inversé (bit de poids faible). */
export function flipBase64Url(text: string, index: number): string {
  const bytes = fromBase64Url(text)
  const view = new DataView(bytes.buffer)
  view.setUint8(index, view.getUint8(index) ^ 0x01)
  return toBase64Url(bytes)
}
