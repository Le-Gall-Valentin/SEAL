import { hashKey, type QueryClient, useQuery } from '@tanstack/react-query'
import { useSessionApi } from './sessionApiPort'

/** Clé du compte courant : /me est la seule source de la session (ADR 0024, point 7). */
export const SESSION_QUERY_KEY = ['session', 'me'] as const

const SESSION_STALE_TIME_MS = 30_000

export function useCurrentAccount() {
  const api = useSessionApi()
  return useQuery({
    queryKey: SESSION_QUERY_KEY,
    queryFn: ({ signal }) => api.me(signal),
    // Chaque écran monte sa garde : sans délai, /me serait relu à chaque changement d'écran. Les transitions
    // (connexion, vérification, déconnexion) le relisent explicitement, et tout 401 termine la session.
    staleTime: SESSION_STALE_TIME_MS,
  })
}

/**
 * AUTH-53 : déconnexion ou session perdue. Le client devient anonyme et oublie toute donnée lue sous le compte ; les
 * gardes de route mènent alors à la connexion.
 */
export function endSession(client: QueryClient): void {
  // Les mutations gardent leurs variables, mots de passe compris : elles partent aussi.
  client.getMutationCache().clear()
  client.removeQueries({
    predicate: (query) => query.queryHash !== hashKey(SESSION_QUERY_KEY),
  })
  client.setQueryData(SESSION_QUERY_KEY, null)
}

/** AUTH-16 : après une connexion ou une vérification, /me est relu ; les gardes de route mènent à l'écran suivant. */
export function refreshSession(client: QueryClient): Promise<void> {
  return client.invalidateQueries({ queryKey: SESSION_QUERY_KEY })
}
