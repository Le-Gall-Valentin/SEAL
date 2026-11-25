import { type QueryClient, useQuery, useQueryClient } from '@tanstack/react-query'
import { allPages, useActivityGatedInterval } from '@/shared/lib'
import type { Conversation } from '../api/IConversationsApi'
import { sortConversations } from './conversations'
import { useConversationsApi } from './conversationsPort'

/** Racine des clés des conversations : une action relit la liste et le détail (ADR 0041, point 8). */
export const CONVERSATIONS_ROOT_KEY = ['conversations'] as const
export const CONVERSATIONS_KEY = ['conversations', 'list'] as const
export const conversationKey = (id: string) => ['conversations', 'detail', id] as const

/** ADR 0041 (point 8) : jusqu'à la trame conv.update du lot 4, la liste se relit toutes les 60 s après une action. */
const REFRESH_MS = 60_000

/** CNV-04 : toutes les conversations actives, toutes pages lues, triées (ADR 0041, point 2). */
export function useConversations() {
  const api = useConversationsApi()
  return useQuery({
    queryKey: CONVERSATIONS_KEY,
    queryFn: async ({ signal }) =>
      sortConversations(await allPages((after) => api.list(after, signal))),
  })
}

/** CNV-06 : la conversation ouverte ; rien à lire sans identifiant. */
export function useConversation(id: string | null) {
  const api = useConversationsApi()
  return useQuery({
    queryKey: conversationKey(id ?? ''),
    queryFn: ({ signal }) => api.get(id ?? '', signal),
    enabled: id !== null,
  })
}

/**
 * ADR 0041 (point 8), AUTH-52 : relit la liste et la conversation ouverte toutes les 60 s, seulement si l'utilisateur a
 * agi ; jamais par `refetchInterval`, qui prolongerait la session.
 */
export function useConversationsRefresh(): void {
  const client = useQueryClient()
  useActivityGatedInterval(() => {
    void client.refetchQueries({ queryKey: CONVERSATIONS_ROOT_KEY, type: 'active' })
  }, REFRESH_MS)
}

/** ADR 0041 (point 8) : après chaque action, la liste et le détail sont relus. */
export function invalidateConversations(client: QueryClient): Promise<void> {
  return client.invalidateQueries({ queryKey: CONVERSATIONS_ROOT_KEY })
}

/** La vue que rend une action (ajout, renommage) remplace le détail, sans attendre la relecture. */
export function storeConversation(client: QueryClient, conversation: Conversation): void {
  client.setQueryData(conversationKey(conversation.id), conversation)
}
