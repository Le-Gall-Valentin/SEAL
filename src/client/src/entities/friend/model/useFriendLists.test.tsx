import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { describe, expect, it } from 'vitest'
import { renderHook } from 'vitest-browser-react'
import type { Friend, IFriendsApi, Page, ReceivedRequest } from '../api/IFriendsApi'
import { FriendsApiProvider } from './friendsPort'
import { RECEIVED_REQUESTS_KEY, useFriends, useReceivedRequests } from './useFriendLists'

const never = () => Promise.reject(new Error('non attendu'))
const id = (n: number) => `0192f7a1-0000-7000-8000-${String(n).padStart(12, '0')}`

function friend(n: number, displayName: string): Friend {
  return { userId: id(n), username: `ami.${String(n)}`, displayName, friendsSince: new Date(0) }
}

function wrapper(
  api: Partial<IFriendsApi>,
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } }),
) {
  const full: IFriendsApi = { friends: never, receivedRequests: never, sentRequests: never, ...api }
  return {
    client,
    wrapper: ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={client}>
        <FriendsApiProvider api={full}>{children}</FriendsApiProvider>
      </QueryClientProvider>
    ),
  }
}

describe('entities/friend : listes complètes (ADR 0037, point 3)', () => {
  it('ADR 0037 (point 3) : toutes les pages sont lues, même après une page vide, puis triées', async () => {
    const pages: Record<string, Page<Friend>> = {
      start: { items: [friend(1, 'Zoé'), friend(2, 'Adam')], next: id(2) },
      [id(2)]: { items: [], next: id(3) },
      [id(3)]: { items: [friend(4, 'Marc')], next: null },
    }
    const afters: (string | null)[] = []
    const { wrapper: Wrapper } = wrapper({
      friends: (after) => {
        afters.push(after)
        return Promise.resolve(pages[after ?? 'start'] ?? { items: [], next: null })
      },
    })

    const { result } = await renderHook(() => useFriends(), { wrapper: Wrapper })

    await expect
      .poll(() => result.current.data?.map((f) => f.displayName))
      .toEqual(['Adam', 'Marc', 'Zoé'])
    expect(afters).toEqual([null, id(2), id(3)])
  })

  it('A10 : un curseur qui revient en boucle arrête la lecture en erreur', async () => {
    const { wrapper: Wrapper } = wrapper({
      friends: () => Promise.resolve({ items: [friend(1, 'Zoé')], next: id(1) }),
    })

    const { result } = await renderHook(() => useFriends(), { wrapper: Wrapper })

    await expect.poll(() => result.current.isError).toBe(true)
  })

  it('FRD-04, ADR 0037 (point 2) : les invitations reçues sont relues toutes les 60 secondes', async () => {
    const received: ReceivedRequest = {
      id: id(9),
      userId: id(8),
      username: 'sarah.b',
      displayName: 'Sarah',
      createdAt: new Date(0),
    }
    const { client, wrapper: Wrapper } = wrapper({
      receivedRequests: () => Promise.resolve({ items: [received], next: null }),
    })

    const { result } = await renderHook(() => useReceivedRequests(), { wrapper: Wrapper })

    await expect.poll(() => result.current.data).toEqual([received])
    const query = client.getQueryCache().find({ queryKey: RECEIVED_REQUESTS_KEY })
    expect(query?.observers[0]?.options.refetchInterval).toBe(60_000)
  })
})
