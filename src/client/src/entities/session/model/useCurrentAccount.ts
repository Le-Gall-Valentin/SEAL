import { hashKey, type QueryClient, useQuery } from '@tanstack/react-query'
import { useSessionApi } from './sessionApiContext'

/** Clé du compte courant : /me est la seule source de la session (ADR 0024, point 7). */
export const SESSION_QUERY_KEY = ['session', 'me'] as const

export function useCurrentAccount() {
  const api = useSessionApi()
  return useQuery({
    queryKey: SESSION_QUERY_KEY,
    queryFn: ({ signal }) => api.me(signal),
  })
}

/**
 * AUTH-53 : déconnexion ou session perdue. Le client devient anonyme et oublie toute donnée lue sous le compte ; les
 * gardes de route mènent alors à la connexion.
 */
export function endSession(client: QueryClient): void {
  client.removeQueries({
    predicate: (query) => query.queryHash !== hashKey(SESSION_QUERY_KEY),
  })
  client.setQueryData(SESSION_QUERY_KEY, null)
}
