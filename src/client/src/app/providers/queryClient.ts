import { QueryClient } from '@tanstack/react-query'

/**
 * État serveur du client (CDC, section 4), créé une fois par la racine de composition. Rien n'est rejoué tout seul :
 * un refus ne change pas au second essai, et une connexion rejouée consommerait deux fois la limite de débit (SEC-10).
 */
export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })
}
