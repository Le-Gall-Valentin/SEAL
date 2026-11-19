import { useQuery, type UseQueryResult } from '@tanstack/react-query'
import { type AuthorityCheck, checkAuthority } from '@/shared/crypto'
import { useAuthorityApi } from './authorityApiPort'
import { useKeyStore } from './keyStorePort'

export const AUTHORITY_QUERY_KEY = ['authority'] as const

/**
 * KEY-11 : clé servie comparée à celle de l'appareil, épinglée au premier usage ; un résultat « changed » ou
 * « invalid » bloque l'application (ADR 0027, point 5). Rien n'est gardé en cache après la sortie de la zone du
 * compte actif : chaque entrée attend un contrôle frais, qui seul décide de l'affichage (ADR 0027, point 7).
 */
export function useAuthorityCheck(): UseQueryResult<AuthorityCheck> {
  const api = useAuthorityApi()
  const store = useKeyStore()
  return useQuery({
    queryKey: AUTHORITY_QUERY_KEY,
    queryFn: async ({ signal }) => checkAuthority(store, await api.fetchAuthority(signal)),
    gcTime: 0,
  })
}
