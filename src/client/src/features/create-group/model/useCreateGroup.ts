import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  type ConversationFailure,
  conversationFailureOf,
  invalidateConversations,
  storeConversation,
} from '@/entities/conversation'
import { invalidateFriends } from '@/entities/friend'
import { useSubmitGuard } from '@/shared/lib'
import { useCreateGroupApi } from './createGroupPort'

interface NewGroup {
  readonly name: string
  readonly memberIds: readonly string[]
}

/**
 * CNV-03, GRP-02 : crée le groupe avec ses membres initiaux, puis le montre (`onCreated`). FRD-21 : un ami retiré
 * entre-temps refuse tout le groupe (NOT_FRIENDS), et la liste d'amis est relue (ADR 0041, point 4).
 */
export function useCreateGroup(onCreated: (conversationId: string) => void) {
  const client = useQueryClient()
  const api = useCreateGroupApi()
  const guard = useSubmitGuard()
  const mutation = useMutation({
    mutationFn: ({ name, memberIds }: NewGroup) => api.createGroup(name, memberIds),
    onSuccess: async (conversation) => {
      storeConversation(client, conversation)
      await invalidateConversations(client)
      onCreated(conversation.id)
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
    /** ADR 0024 (point 9) : le nom que le serveur a refusé, signalé tant que le champ le garde. */
    refusedName: failure?.kind === 'invalid-name' ? mutation.variables?.name : undefined,
    create: (group: NewGroup) => {
      guard.run(() => mutation.mutateAsync(group))
    },
    reset: () => {
      mutation.reset()
    },
  }
}
