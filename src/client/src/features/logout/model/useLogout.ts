import { useMutation, useQueryClient } from '@tanstack/react-query'
import { endSession } from '@/entities/session'
import { useLogoutApi } from './logoutApiContext'

/** AUTH-53 : déconnexion ; le client devient anonyme et oublie toute donnée lue sous le compte. */
export function useLogout() {
  const api = useLogoutApi()
  const client = useQueryClient()
  return useMutation({
    mutationFn: () => api.logout(),
    onSuccess: () => {
      endSession(client)
    },
  })
}
