import { focusManager, QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { renderHook } from 'vitest-browser-react'
import type { Friend, IFriendsApi, Page, ReceivedRequest } from '../api/IFriendsApi'
import { FriendsApiProvider } from './friendsPort'
import { useFriends, useReceivedRequests, useReceivedRequestsRefresh } from './useFriendLists'

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
  it('FRD-10, ADR 0037 (point 3) : toutes les pages sont lues, même après une page vide, puis triées', async () => {
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

  it('FRD-10, A10 : un curseur qui revient en boucle arrête la lecture en erreur', async () => {
    const { wrapper: Wrapper } = wrapper({
      friends: () => Promise.resolve({ items: [friend(1, 'Zoé')], next: id(1) }),
    })

    const { result } = await renderHook(() => useFriends(), { wrapper: Wrapper })

    await expect.poll(() => result.current.isError).toBe(true)
  })

  it('FRD-01, ADR 0038 (point 6) : invitations reçues de la plus récente à la plus ancienne', async () => {
    const older: ReceivedRequest = {
      id: id(7),
      userId: id(6),
      username: 'jade.m',
      displayName: 'Jade',
      createdAt: new Date(1_000),
    }
    const newer: ReceivedRequest = { ...older, id: id(5), createdAt: new Date(2_000) }
    const { wrapper: Wrapper } = wrapper({
      receivedRequests: () => Promise.resolve({ items: [older, newer], next: null }),
    })

    const { result } = await renderHook(() => useReceivedRequests(), { wrapper: Wrapper })

    await expect
      .poll(() => result.current.data?.map((request) => request.id))
      .toEqual([id(5), id(7)])
  })

  describe('FRD-04 : relecture des invitations reçues', () => {
    const received: ReceivedRequest = {
      id: id(9),
      userId: id(8),
      username: 'sarah.b',
      displayName: 'Sarah',
      createdAt: new Date(0),
    }
    afterEach(() => {
      vi.useRealTimers()
      focusManager.setFocused(undefined)
    })

    it('FRD-04, AUTH-52, ADR 0037 (point 11) : toutes les 60 s, seulement après une action de l’utilisateur', async () => {
      vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] })
      let calls = 0
      const { wrapper: Wrapper } = wrapper({
        receivedRequests: () => {
          calls += 1
          return Promise.resolve({ items: [received], next: null })
        },
      })
      const { result } = await renderHook(
        () => {
          useReceivedRequestsRefresh()
          return useReceivedRequests()
        },
        { wrapper: Wrapper },
      )
      await expect.poll(() => result.current.data).toEqual([received])
      expect(calls).toBe(1)

      vi.advanceTimersByTime(3 * 60_000)
      expect(calls).toBe(1)

      window.dispatchEvent(new Event('pointerdown'))
      vi.advanceTimersByTime(60_000)
      await expect.poll(() => calls).toBe(2)
    })

    it('FRD-04, ADR 0037 (point 2) : relues au retour du focus', async () => {
      let calls = 0
      const { wrapper: Wrapper } = wrapper({
        receivedRequests: () => {
          calls += 1
          return Promise.resolve({ items: [received], next: null })
        },
      })
      const { result } = await renderHook(() => useReceivedRequests(), { wrapper: Wrapper })
      await expect.poll(() => result.current.data).toEqual([received])

      focusManager.setFocused(false)
      focusManager.setFocused(true)

      await expect.poll(() => calls).toBe(2)
    })
  })
})
