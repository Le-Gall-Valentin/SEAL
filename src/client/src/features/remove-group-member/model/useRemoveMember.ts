import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  type ConversationFailure,
  conversationFailureOf,
  invalidateConversations,
} from '@/entities/conversation'
import { failureOf, useSubmitGuard } from '@/shared/lib'
import { useRemoveMemberApi } from './removeMemberPort'

interface Removal {
  readonly conversationId: string
  readonly userId: string
}

/**
 * GRP-03 : l'administrateur retire un membre ; la conversation est relue. Un membre déjà parti (404 NOT_A_MEMBER) se
 * traite comme retiré : l'état voulu est atteint (ADR 0041, point 4).
 */
export function useRemoveMember(onDone: () => void) {
  const client = useQueryClient()
  const api = useRemoveMemberApi()
  const guard = useSubmitGuard()
  const mutation = useMutation({
    mutationFn: async ({ conversationId, userId }: Removal) => {
      try {
        await api.exclude(conversationId, userId)
      } catch (error) {
        const failure = failureOf(error)
        if (!(failure.kind === 'refused' && failure.code === 'NOT_A_MEMBER')) {
          throw error
        }
      }
    },
    onSuccess: async () => {
      await invalidateConversations(client)
      onDone()
    },
    onError: async (error) => {
      const kind = conversationFailureOf(error).kind
      if (kind === 'forbidden' || kind === 'gone') {
        await invalidateConversations(client)
      }
    },
  })
  const failure: ConversationFailure | null =
    mutation.error === null ? null : conversationFailureOf(mutation.error)
  return {
    pending: mutation.isPending,
    failure,
    remove: (removal: Removal) => {
      guard.run(() => mutation.mutateAsync(removal))
    },
    reset: () => {
      mutation.reset()
    },
  }
}
