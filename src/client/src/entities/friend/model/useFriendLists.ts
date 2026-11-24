import { type QueryClient, useQuery, useQueryClient } from '@tanstack/react-query'
import { useActivityGatedInterval } from '@/shared/lib'
import type { Page } from '../api/IFriendsApi'
import { newestFirst, sortFriends } from './friends'
import { useFriendsApi } from './friendsPort'

/** Racine des clés des amis : une action la relit toute (ADR 0037, point 2). */
export const FRIENDS_ROOT_KEY = ['friends'] as const
export const FRIENDS_KEY = ['friends', 'list'] as const
export const RECEIVED_REQUESTS_KEY = ['friends', 'received'] as const
export const SENT_REQUESTS_KEY = ['friends', 'sent'] as const

/** ADR 0037 (points 2 et 11) : jusqu'à la WebSocket du lot 4, le compteur se relit toutes les 60 secondes. */
const RECEIVED_REFRESH_MS = 60_000

/** Garde-fou : 200 pages de 50, bien au-delà d'une liste d'amis réelle. */
const MAX_PAGES = 200

/**
 * ADR 0037 (point 3) : suit `next` jusqu'à `null`, même après une page vide (comptes inactifs retirés). Un curseur
 * déjà vu ou trop de pages arrêtent la lecture en erreur plutôt que de boucler (A10).
 */
async function allPages<T>(load: (after: string | null) => Promise<Page<T>>): Promise<T[]> {
  const items: T[] = []
  const seen = new Set<string>()
  let after: string | null = null
  for (let page = 0; page < MAX_PAGES; page++) {
    const { items: pageItems, next }: Page<T> = await load(after)
    items.push(...pageItems)
    if (next === null) {
      return items
    }
    if (seen.has(next)) {
      break
    }
    seen.add(next)
    after = next
  }
  throw new Error('pagination sans fin')
}

/** FRD-10 : tous les amis encore actifs, triés par nom affiché (ADR 0038, point 6). */
export function useFriends() {
  const api = useFriendsApi()
  return useQuery({
    queryKey: FRIENDS_KEY,
    queryFn: async ({ signal }) =>
      sortFriends(await allPages((after) => api.friends(after, signal))),
  })
}

/**
 * FRD-04 : invitations reçues en attente, la plus récente d'abord, relues au retour du focus (ADR 0037, point 2) ; la
 * relecture périodique est celle de `useReceivedRequestsRefresh`.
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

/**
 * FRD-04, ADR 0037 (point 11) : relit les invitations reçues toutes les 60 secondes, seulement si l'utilisateur a agi
 * depuis l'échéance précédente, pour qu'une relecture ne prolonge pas une session inactive (AUTH-52). La coque la
 * monte une fois pour tous les écrans.
 */
export function useReceivedRequestsRefresh(): void {
  const client = useQueryClient()
  useActivityGatedInterval(() => {
    void client.refetchQueries({ queryKey: RECEIVED_REQUESTS_KEY, type: 'active' })
  }, RECEIVED_REFRESH_MS)
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
