import { MutationCache, QueryCache, QueryClient } from '@tanstack/react-query'
import { endSession } from '@/entities/session'
import { isUnauthenticated } from '@/shared/lib'

/**
 * État serveur du client (CDC, section 4), créé une fois par la racine de composition.
 *
 * - Rien n'est rejoué tout seul : un refus ne change pas au second essai, et une connexion rejouée consommerait deux
 *   fois la limite de débit (SEC-10).
 * - AUTH-53, ADR 0023 : une requête qui découvre la session perdue (UNAUTHENTICATED) termine la session du client ; les
 *   gardes de route ramènent alors à la connexion.
 */
export function createQueryClient(): QueryClient {
  const onError = (error: unknown) => {
    if (isUnauthenticated(error)) {
      endSession(client)
    }
  }
  const client: QueryClient = new QueryClient({
    queryCache: new QueryCache({ onError }),
    mutationCache: new MutationCache({ onError }),
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })
  return client
}
