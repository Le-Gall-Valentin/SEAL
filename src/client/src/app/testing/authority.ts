/** Doublures de l'Autorité et du trousseau pour les tests de la couche app (KEY-11), jamais importées ailleurs. */
import type { AuthorityView, IAuthorityApi } from '@/entities/authority'
import type { KeyStore } from '@/shared/crypto'

/** Autorité des vecteurs (vectors/identity.json, section authority). */
export const VECTOR_AUTHORITY: AuthorityView = {
  publicKey: '11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHURo',
  keyId: 'If4x36FUomE',
  fingerprint: '21FE31DFA154A261626BF854046FD2271B7BED4B6ABE45AA58877EF47F9721B9',
}

export function servingAuthority(view: AuthorityView = VECTOR_AUTHORITY): IAuthorityApi {
  return { fetchAuthority: () => Promise.resolve(view) }
}

/** Trousseau en mémoire : seule l'Autorité y vit, épinglée ou non. */
export function memoryKeyStore(pinned?: Uint8Array): KeyStore {
  let authority = pinned === undefined ? undefined : Uint8Array.from(pinned)
  return {
    loadKeyring: () => Promise.resolve(undefined),
    saveKeyring: () => Promise.resolve(),
    loadCertificate: () => Promise.resolve(undefined),
    loadAuthority: () =>
      Promise.resolve(authority === undefined ? undefined : Uint8Array.from(authority)),
    pinAuthorityIfAbsent: (publicKey) => {
      authority ??= Uint8Array.from(publicKey)
      return Promise.resolve(Uint8Array.from(authority))
    },
    close: () => undefined,
  }
}
