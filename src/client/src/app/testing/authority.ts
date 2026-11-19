/** Doublures de l'Autorité et trousseaux jetables pour les tests de la couche app (KEY-11), jamais importés ailleurs. */
import type { AuthorityView, IAuthorityApi } from '@/entities/authority'
import { checkAuthority, type KeyStore, openKeyStore } from '@/shared/crypto'

/** Autorité des vecteurs (vectors/identity.json, section authority). */
export const VECTOR_AUTHORITY: AuthorityView = {
  publicKey: '11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHURo',
  keyId: 'If4x36FUomE',
  fingerprint: '21FE31DFA154A261626BF854046FD2271B7BED4B6ABE45AA58877EF47F9721B9',
}

/** Une autre Autorité cohérente : la clé « other » des vecteurs, keyId et empreinte calculés comme le serveur. */
export const OTHER_AUTHORITY: AuthorityView = {
  publicKey: '_FHNjmIYoaONpH7QAjDwWAgW7RO6MwOsXeuRFUiQgCU',
  keyId: '2sBz4BI73qU',
  fingerprint: 'DAC073E0123BDEA59DD9B3BDA9CF6037F63ACA82627D7ABCD5C4AC29DD74003E',
}

export function servingAuthority(view: AuthorityView = VECTOR_AUTHORITY): IAuthorityApi {
  return { fetchAuthority: () => Promise.resolve(view) }
}

let opened = 0

/** Trousseau IndexedDB neuf, dans une base que nul autre test n'ouvre ; une Autorité y est épinglée si on la donne. */
export async function freshKeyStore(pinned?: AuthorityView): Promise<KeyStore> {
  opened += 1
  const store = await openKeyStore(`seal-app-test-${String(Date.now())}-${String(opened)}`)
  if (pinned !== undefined) {
    await checkAuthority(store, pinned)
  }
  return store
}
