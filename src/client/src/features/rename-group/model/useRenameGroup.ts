import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  type ConversationFailure,
  conversationFailureOf,
  invalidateConversations,
  storeConversation,
} from '@/entities/conversation'
import { useSubmitGuard } from '@/shared/lib'
import { useRenameGroupApi } from './renameGroupPort'

interface Renaming {
  readonly conversationId: string
  readonly name: string
}

/**
 * GRP-03 : seul l'administrateur renomme ; la vue rendue remplace le détail. Un rôle périmé (FORBIDDEN) ou un groupe
 * disparu relit les conversations, pour que l'écran montre l'état réel.
 */
export function useRenameGroup(onDone: () => void) {
  const client = useQueryClient()
  const api = useRenameGroupApi()
  const guard = useSubmitGuard()
  const mutation = useMutation({
    mutationFn: ({ conversationId, name }: Renaming) => api.rename(conversationId, name),
    onSuccess: async (conversation) => {
      storeConversation(client, conversation)
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
    rename: (renaming: Renaming) => {
      guard.run(() => mutation.mutateAsync(renaming))
    },
    reset: () => {
      mutation.reset()
    },
  }
}
