import { type QueryClient, useQuery } from '@tanstack/react-query'
import { allPages } from '@/shared/lib'
import { newestFirst, sortFriends } from './friends'
import { useFriendsApi } from './friendsPort'

/** Racine des clés des amis : une action la relit toute (ADR 0037, point 2). */
export const FRIENDS_ROOT_KEY = ['friends'] as const
export const FRIENDS_KEY = ['friends', 'list'] as const
export const RECEIVED_REQUESTS_KEY = ['friends', 'received'] as const
export const SENT_REQUESTS_KEY = ['friends', 'sent'] as const

/**
 * FRD-10 : tous les amis encore actifs, triés par nom affiché (ADR 0038, point 6). `enabled` faux : rien n'est lu, la
 * liste déjà gardée reste servie (un dialogue fermé).
 */
export function useFriends({ enabled = true }: { readonly enabled?: boolean } = {}) {
  const api = useFriendsApi()
  return useQuery({
    queryKey: FRIENDS_KEY,
    enabled,
    queryFn: async ({ signal }) =>
      sortFriends(await allPages((after) => api.friends(after, signal))),
  })
}

/**
 * FRD-04 : invitations reçues en attente, la plus récente d'abord, relues au retour du focus (ADR 0037, point 2) et
 * sur les trames friend.* (ADR 0043).
 */
export function useReceivedRequests() {
  const api = useFriendsApi()
  return useQuery({
    queryKey: RECEIVED_REQUESTS_KEY,
    queryFn: async ({ signal }) =>
      newestFirst(await allPages((after) => api.receivedRequests(after, signal))),
    refetchOnWindowFocus: true,
  })
}

/** Invitations envoyées en attente, la plus récente d'abord. */
export function useSentRequests() {
  const api = useFriendsApi()
  return useQuery({
    queryKey: SENT_REQUESTS_KEY,
    queryFn: async ({ signal }) =>
      newestFirst(await allPages((after) => api.sentRequests(after, signal))),
  })
}

/** ADR 0037 (point 2) : après chaque action, les trois listes et le compteur sont relus. */
export function invalidateFriends(client: QueryClient): Promise<void> {
  return client.invalidateQueries({ queryKey: FRIENDS_ROOT_KEY })
}

/**
 * ADR 0043, ADR 0044 (décision 1) : une trame ou une reconnexion rend les trois listes et le compteur périmés, sans requête ; ils sont
 * relus au prochain affichage, au retour du focus ou par {@link refetchFriends}.
 */
export function markFriendsStale(client: QueryClient): Promise<void> {
  return client.invalidateQueries({ queryKey: FRIENDS_ROOT_KEY, refetchType: 'none' })
}

/**
 * ADR 0044 (décision 1) : relit ceux qui sont affichés et périmés ; une lecture déjà en cours sert, sans être
 * interrompue (une rafale de trames ne relance pas la lecture en boucle).
 */
export function refetchFriends(client: QueryClient): Promise<void> {
  return client.refetchQueries(
    { queryKey: FRIENDS_ROOT_KEY, type: 'active', stale: true },
    { cancelRefetch: false },
  )
}
