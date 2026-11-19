/**
 * KEY-33, ADR 0029 (point 3) : les clés que le serveur sert pour le compte, comparées au trousseau de l'appareil.
 * `foreign` : le certificat porte une autre IK (réinitialisation ailleurs, ou substitution par le serveur, 16.1) ;
 * `stale` : une sous-clé servie manque au trousseau, qui est périmé ou face à un coffre rejoué (PROTOCOL.md 5.5) ;
 * `match` : `published` dit si au moins une sous-clé du trousseau est publiée et encore valide.
 */
import { fromBase64Url } from './base64url'
import { equalBytes } from './bytes'
import type { Keyring } from './keyring'

/** Ce que la comparaison lit de GET /api/v1/users/{id}/keys : octets en base64url. */
export interface ServedKeys {
  readonly certificate: { readonly ikPublic: string }
  readonly subkeys: readonly { readonly ekId: string }[]
}

export type KeysComparison =
  | { readonly kind: 'foreign' }
  | { readonly kind: 'stale' }
  | { readonly kind: 'match'; readonly published: boolean }

export function compareOwnKeys(keyring: Keyring, served: ServedKeys): KeysComparison {
  const ikPublic = decoded(served.certificate.ikPublic)
  if (ikPublic === undefined || !equalBytes(ikPublic, keyring.identityPublicKey)) {
    return { kind: 'foreign' }
  }
  const known = served.subkeys.every((subkey) => {
    const ekId = decoded(subkey.ekId)
    return ekId !== undefined && keyring.subkeys.some((local) => equalBytes(local.ekId, ekId))
  })
  return known ? { kind: 'match', published: served.subkeys.length > 0 } : { kind: 'stale' }
}

/** Un base64url non canonique ne désigne aucune clé. */
function decoded(text: string): Uint8Array | undefined {
  try {
    return fromBase64Url(text)
  } catch {
    return undefined
  }
}
