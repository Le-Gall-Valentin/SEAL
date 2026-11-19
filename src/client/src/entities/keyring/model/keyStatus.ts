import { useQuery, type UseQueryResult } from '@tanstack/react-query'
import { useClock, withLock } from '@/shared/lib'
import { completeKeys, ForeignIdentityError, type KeyOwner, type KeysContext } from './completeKeys'
import { useKeysApi } from './keysApiPort'
import { useKeyStore } from './keyStorePort'

/** ADR 0029, point 3 : verrou des clés, partagé par les onglets ; aucune étape des parcours ne se fait sans lui. */
export const KEYS_LOCK = 'seal-keys'

/**
 * ready : trousseau complet ; setup : première connexion (KEY-02) ; restore : nouvel appareil (KEY-33) ; foreign :
 * certificat d'une autre clé d'identité, l'application se bloque (16.1).
 */
export type KeyStatus = 'ready' | 'setup' | 'restore' | 'foreign'

/**
 * KEY-02, KEY-33 : trousseau et certificat sur l'appareil → ready ; trousseau seul → la publication reprend, puis
 * ready ; rien sur l'appareil → setup sans coffre sur le serveur, restore avec.
 */
export async function keyStatus(context: KeysContext): Promise<KeyStatus> {
  const { api, store, owner } = context
  if ((await store.loadKeyring(owner.userId)) !== undefined) {
    if ((await store.loadCertificate(owner.userId)) !== undefined) {
      return 'ready'
    }
    try {
      await completeKeys(context)
      return 'ready'
    } catch (error) {
      if (error instanceof ForeignIdentityError) {
        return 'foreign'
      }
      throw error
    }
  }
  return (await api.readVault()) === undefined ? 'setup' : 'restore'
}

/** Statut relu à chaque entrée dans la zone du compte, sous le verrou des clés ; rien n'est gardé en cache. */
export function useKeyStatus(owner: KeyOwner): UseQueryResult<KeyStatus> {
  const api = useKeysApi()
  const store = useKeyStore()
  const clock = useClock()
  return useQuery({
    queryKey: ['keyring', owner.userId],
    queryFn: () => withLock(KEYS_LOCK, () => keyStatus({ api, store, owner, clock })),
    gcTime: 0,
    retry: false,
  })
}
