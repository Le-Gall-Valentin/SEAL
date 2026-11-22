import { type QueryClient, useQuery } from '@tanstack/react-query'
import type { OAuthProviderId } from '../api/IOAuthProvidersApi'
import { useOAuthProvidersApi } from './oauthProvidersPort'

const NONE: readonly OAuthProviderId[] = []
const QUERY_KEY = ['oauth-providers'] as const

/**
 * ADR 0035 : relit les fournisseurs configurés si leur lecture a échoué, sans rien relire sinon (la configuration ne
 * change qu'au redémarrage du serveur).
 */
export function retryFailedOAuthProviders(client: QueryClient): Promise<void> {
  return client.refetchQueries({
    queryKey: QUERY_KEY,
    predicate: (query) => query.state.status === 'error',
  })
}

/**
 * AUTH-35, OPS-03 : les fournisseurs configurés. Tant qu'ils ne sont pas lus, ou si la lecture échoue, aucun n'est
 * affiché : SEAL n'annonce pas une connexion qu'il ne peut pas lancer (ADR 0007, point 9). La configuration ne change
 * qu'au redémarrage du serveur : la réponse est gardée.
 */
export function useOAuthProviders(): readonly OAuthProviderId[] {
  const api = useOAuthProvidersApi()
  const query = useQuery({
    queryKey: QUERY_KEY,
    queryFn: ({ signal }) => api.providers(signal),
    staleTime: Number.POSITIVE_INFINITY,
  })
  return query.data ?? NONE
}
