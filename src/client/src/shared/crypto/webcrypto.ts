/** Appels WebCrypto partagés par les modules de shared/crypto, internes au segment. */

/** Paire X25519 ; WebCrypto ne garantit pas son type pour un algorithme donné par son nom. */
export async function generateX25519(
  extractable: boolean,
  usages: KeyUsage[],
): Promise<CryptoKeyPair> {
  const pair = await crypto.subtle.generateKey({ name: 'X25519' }, extractable, usages)
  if (!('privateKey' in pair)) {
    throw new TypeError('X25519 : une paire de clés était attendue')
  }
  return pair
}
