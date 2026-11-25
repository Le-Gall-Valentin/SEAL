import { type QueryClient, useQuery } from '@tanstack/react-query'
import { allPages } from '@/shared/lib'
import type { Conversation } from '../api/IConversationsApi'
import { sortConversations } from './conversations'
import { useConversationsApi } from './conversationsPort'

/** Racine des clés des conversations : une action relit la liste et le détail (ADR 0041, point 8). */
export const CONVERSATIONS_ROOT_KEY = ['conversations'] as const
export const CONVERSATIONS_KEY = ['conversations', 'list'] as const
export const conversationKey = (id: string) => ['conversations', 'detail', id] as const

/** CNV-04 : toutes les conversations actives, toutes pages lues, triées (ADR 0041, point 2). */
export function useConversations() {
  const api = useConversationsApi()
  return useQuery({
    queryKey: CONVERSATIONS_KEY,
    queryFn: async ({ signal }) =>
      sortConversations(await allPages((after) => api.list(after, signal))),
    // ADR 0041 (point 8) : relue au retour du focus, explicitement, quel que soit le réglage commun.
    refetchOnWindowFocus: true,
  })
}

/** CNV-06 : la conversation ouverte ; rien à lire sans identifiant. */
export function useConversation(id: string | null) {
  const api = useConversationsApi()
  return useQuery({
    queryKey: conversationKey(id ?? ''),
    queryFn: ({ signal }) => api.get(id ?? '', signal),
    enabled: id !== null,
    refetchOnWindowFocus: true,
  })
}

/**
 * ADR 0041 (point 8) : après chaque action, la liste et le détail sont relus. `refetch` faux : ils sont seulement
 * périmés, relus au prochain affichage ou au retour du focus (AUTH-52, ADR 0043 point 4).
 */
export function invalidateConversations(
  client: QueryClient,
  { refetch = true }: { readonly refetch?: boolean } = {},
): Promise<void> {
  return refetch
    ? client.invalidateQueries({ queryKey: CONVERSATIONS_ROOT_KEY })
    : client.invalidateQueries({ queryKey: CONVERSATIONS_ROOT_KEY, refetchType: 'none' })
}

/** La vue que rend une action (ajout, renommage) remplace le détail, sans attendre la relecture. */
export function storeConversation(client: QueryClient, conversation: Conversation): void {
  client.setQueryData(conversationKey(conversation.id), conversation)
}
