import { useMutation, useQueryClient } from '@tanstack/react-query'
import { invalidateFriends } from '@/entities/friend'
import { failureOf, type FormFailure, isFormFailure, useSubmitGuard } from '@/shared/lib'
import { useRemoveFriendApi } from './removeFriendPort'

/**
 * FRD-11 : retirer un ami supprime l'amitié pour les deux ; la liste est relue. Un ami déjà retiré ailleurs (404) se
 * traite comme retiré : l'état voulu est atteint.
 */
export function useRemoveFriend(onDone: () => void) {
  const client = useQueryClient()
  const api = useRemoveFriendApi()
  const guard = useSubmitGuard()
  const mutation = useMutation({
    mutationFn: async (userId: string) => {
      try {
        await api.unfriend(userId)
      } catch (error) {
        const failure = failureOf(error)
        if (!(failure.kind === 'refused' && failure.code === 'NOT_FOUND')) {
          throw error
        }
      }
    },
    onSuccess: async () => {
      await invalidateFriends(client)
      onDone()
    },
  })
  const failure = mutation.error === null ? null : failureOf(mutation.error)
  const formFailure: FormFailure | null =
    failure === null ? null : isFormFailure(failure) ? failure : { kind: 'unexpected' }
  return {
    pending: mutation.isPending,
    formFailure,
    remove: (userId: string) => {
      guard.run(() => mutation.mutateAsync(userId))
    },
    /** La confirmation reste montée : l'erreur d'un retrait annulé ne doit pas reparaître pour l'ami suivant. */
    reset: () => {
      mutation.reset()
    },
  }
}
