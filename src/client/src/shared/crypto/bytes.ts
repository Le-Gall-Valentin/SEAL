/** Égalité de deux suites d'octets publiques (clés publiques, identifiants) ; jamais pour comparer un secret. */
export function equalBytes(left: Uint8Array, right: Uint8Array): boolean {
  return left.length === right.length && left.every((byte, index) => byte === right[index])
}
