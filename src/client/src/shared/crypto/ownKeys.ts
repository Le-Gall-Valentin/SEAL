/**
 * KEY-33, ADR 0029 (point 3) : les clés que le serveur sert pour le compte, comparées au trousseau de l'appareil.
 * `foreign` : le certificat porte une autre IK (réinitialisation ailleurs, ou substitution par le serveur, 16.1) ;
 * `stale` : une sous-clé servie manque au trousseau, qui est périmé ou face à un coffre rejoué (PROTOCOL.md 5.5) ;
 * `match` : `published` dit si au moins une sous-clé du trousseau est publiée et encore valide.
 */
import { fromBase64Url } from './base64url'
import { equalBytes } from './bytes'
import type { Keyring, SubkeyInfo } from './keyring'

/** Ce que la comparaison lit de GET /api/v1/users/{id}/keys : octets en base64url, dates en millisecondes. */
export interface ServedKeys {
  readonly certificate: { readonly ikPublic: string; readonly notBefore: number }
  readonly subkeys: readonly { readonly ekId: string }[]
}

/** ADR 0025, point 7 : écart toléré par le serveur entre l'horloge du client et la sienne. */
const CLOCK_TOLERANCE_MS = 5 * 60_000

/** ADR 0029, point 11 : 15 jours de marge sur les 45 de validité, contre une horloge très en retard. */
const REPLACEABLE_AGE_MS = 30 * 86_400_000

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

/**
 * ADR 0029 (point 11), PROTOCOL.md 5.5 : une sous-clé jamais publiée, qu'une reprise redate au lieu d'en ajouter une.
 * Elle n'est pas servie, a été créée plus de 5 minutes après l'émission du certificat servi (une sous-clé d'un
 * certificat précédent n'est plus servie, mais a pu servir) et il y a moins de 30 jours (une sous-clé expirée n'est
 * plus servie non plus). Une erreur de ce jugement ne retire aucune clé : republier une sous-clé déjà publiée est
 * refusé par le serveur.
 */
export function neverPublished(
  served: ServedKeys,
  now: number,
): (subkey: Pick<SubkeyInfo, 'ekId' | 'createdAt'>) => boolean {
  const servedIds = served.subkeys.map((subkey) => decoded(subkey.ekId))
  return (subkey) =>
    subkey.createdAt >= served.certificate.notBefore + CLOCK_TOLERANCE_MS &&
    now - subkey.createdAt < REPLACEABLE_AGE_MS &&
    !servedIds.some((ekId) => ekId !== undefined && equalBytes(ekId, subkey.ekId))
}

/** Un base64url non canonique ne désigne aucune clé. */
function decoded(text: string): Uint8Array | undefined {
  try {
    return fromBase64Url(text)
  } catch {
    return undefined
  }
}
