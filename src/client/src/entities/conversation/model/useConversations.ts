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

/** ADR 0041 (point 8) : après chaque action, la liste et le détail sont relus. */
export function invalidateConversations(client: QueryClient): Promise<void> {
  return client.invalidateQueries({ queryKey: CONVERSATIONS_ROOT_KEY })
}

/**
 * ADR 0043, ADR 0044 (décision 1) : une trame ou une reconnexion rend la liste et le détail périmés, sans requête ; ils sont
 * relus au prochain affichage, au retour du focus ou par {@link refetchConversations}.
 */
export function markConversationsStale(client: QueryClient): Promise<void> {
  return client.invalidateQueries({ queryKey: CONVERSATIONS_ROOT_KEY, refetchType: 'none' })
}

/**
 * ADR 0044 (décision 1) : relit ceux qui sont affichés et périmés. Une lecture déjà en cours, partie avant la trame,
 * est relancée : son résultat serait antérieur au changement annoncé. La garde d'activité regroupe les rafales.
 */
export function refetchConversations(client: QueryClient): Promise<void> {
  return client.refetchQueries({ queryKey: CONVERSATIONS_ROOT_KEY, type: 'active', stale: true })
}

/** La vue que rend une action (ajout, renommage) remplace le détail, sans attendre la relecture. */
export function storeConversation(client: QueryClient, conversation: Conversation): void {
  client.setQueryData(conversationKey(conversation.id), conversation)
}
