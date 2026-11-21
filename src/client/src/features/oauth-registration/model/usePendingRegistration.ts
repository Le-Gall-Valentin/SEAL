import { useQuery } from '@tanstack/react-query'
import { useOAuthRegistrationApi } from './oauthRegistrationApiPort'

/** AUTH-32 : l'inscription OAuth en attente de la session, lue une fois à l'ouverture de l'écran. */
export function usePendingRegistration() {
  const api = useOAuthRegistrationApi()
  return useQuery({
    queryKey: ['oauth-pending'],
    queryFn: ({ signal }) => api.pending(signal),
    staleTime: Number.POSITIVE_INFINITY,
    gcTime: 0,
  })
}
