import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  type ConversationFailure,
  conversationFailureOf,
  invalidateConversations,
  storeConversation,
} from '@/entities/conversation'
import { invalidateFriends } from '@/entities/friend'
import { useSubmitGuard } from '@/shared/lib'
import { useAddMembersApi } from './addMembersPort'

interface Admission {
  readonly conversationId: string
  readonly userIds: readonly string[]
}

/**
 * GRP-03, GRP-04 : tout membre ajoute ses propres amis ; la vue rendue remplace le détail. FRD-21 : un ami retiré
 * entre-temps refuse tout l'ajout et la liste d'amis est relue ; un groupe disparu ou plein relit les conversations.
 */
export function useAddMembers(onDone: () => void) {
  const client = useQueryClient()
  const api = useAddMembersApi()
  const guard = useSubmitGuard()
  const mutation = useMutation({
    mutationFn: ({ conversationId, userIds }: Admission) => api.admit(conversationId, userIds),
    onSuccess: async (conversation) => {
      storeConversation(client, conversation)
      await invalidateConversations(client)
      onDone()
    },
    onError: async (error) => {
      const kind = conversationFailureOf(error).kind
      if (kind === 'not-friends') {
        await invalidateFriends(client)
      } else if (kind === 'gone' || kind === 'group-full') {
        // Groupe disparu ou plein : la conversation relue montre l'état réel et les places restantes.
        await invalidateConversations(client)
      }
    },
  })
  const failure: ConversationFailure | null =
    mutation.error === null ? null : conversationFailureOf(mutation.error)
  return {
    pending: mutation.isPending,
    failure,
    admit: (admission: Admission) => {
      guard.run(() => mutation.mutateAsync(admission))
    },
    reset: () => {
      mutation.reset()
    },
  }
}
