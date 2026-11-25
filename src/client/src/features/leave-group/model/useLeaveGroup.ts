import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  type ConversationFailure,
  conversationFailureOf,
  invalidateConversations,
} from '@/entities/conversation'
import { failureOf, useSubmitGuard } from '@/shared/lib'
import { useLeaveGroupApi } from './leaveGroupPort'

/**
 * GRP-03 : tout membre part d'un groupe ; la liste est relue. Un groupe déjà quitté (404 NOT_FOUND, CNV-06) se traite
 * comme quitté : l'état voulu est atteint.
 */
export function useLeaveGroup(onDone: () => void) {
  const client = useQueryClient()
  const api = useLeaveGroupApi()
  const guard = useSubmitGuard()
  const mutation = useMutation({
    mutationFn: async (conversationId: string) => {
      try {
        await api.leave(conversationId)
      } catch (error) {
        const failure = failureOf(error)
        if (!(failure.kind === 'refused' && failure.code === 'NOT_FOUND')) {
          throw error
        }
      }
    },
    onSuccess: async () => {
      await invalidateConversations(client)
      onDone()
    },
  })
  const failure: ConversationFailure | null =
    mutation.error === null ? null : conversationFailureOf(mutation.error)
  return {
    pending: mutation.isPending,
    failure,
    leave: (conversationId: string) => {
      guard.run(() => mutation.mutateAsync(conversationId))
    },
    reset: () => {
      mutation.reset()
    },
  }
}
