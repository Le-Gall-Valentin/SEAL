import { useQuery } from '@tanstack/react-query'
import { useLoginMethodsApi } from './loginMethodsPort'

/** Clé de cache des méthodes de connexion ; vidée avec le reste à la déconnexion. */
export const LOGIN_METHODS_QUERY_KEY = ['account', 'login-methods'] as const

/**
 * ADR 0035, point 2 : méthodes de connexion du compte de la session. Tant qu'elles ne sont pas lues, la page n'affiche
 * ni la carte « Mot de passe » ni de ré-authentification : rien n'est proposé à tort.
 */
export function useLoginMethods() {
  const api = useLoginMethodsApi()
  return useQuery({
    queryKey: LOGIN_METHODS_QUERY_KEY,
    queryFn: ({ signal }) => api.loginMethods(signal),
  })
}
