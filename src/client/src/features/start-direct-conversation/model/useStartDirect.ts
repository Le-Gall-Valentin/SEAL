import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  type ConversationFailure,
  conversationFailureOf,
  invalidateConversations,
  storeConversation,
} from '@/entities/conversation'
import { invalidateFriends } from '@/entities/friend'
import { useSubmitGuard } from '@/shared/lib'
import { useStartDirectApi } from './startDirectPort'

/**
 * CNV-01 : ouvre la directe avec un ami, trouvée ou créée, puis la montre (`onOpened`). FRD-21 : un ami retiré entre-temps
 * reçoit NOT_FRIENDS, et la liste d'amis est relue (ADR 0041, point 4).
 */
export function useStartDirect(onOpened: (conversationId: string) => void) {
  const client = useQueryClient()
  const api = useStartDirectApi()
  const guard = useSubmitGuard()
  const mutation = useMutation({
    mutationFn: (userId: string) => api.openDirect(userId),
    onSuccess: async ({ conversation }) => {
      storeConversation(client, conversation)
      await invalidateConversations(client)
      onOpened(conversation.id)
    },
    onError: async (error) => {
      if (conversationFailureOf(error).kind === 'not-friends') {
        await invalidateFriends(client)
      }
    },
  })
  const failure: ConversationFailure | null =
    mutation.error === null ? null : conversationFailureOf(mutation.error)
  return {
    pending: mutation.isPending,
    failure,
    open: (userId: string) => {
      guard.run(() => mutation.mutateAsync(userId))
    },
    reset: () => {
      mutation.reset()
    },
  }
}
