import { focusManager, QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { renderHook } from 'vitest-browser-react'
import type { Conversation, IConversationsApi, Page } from '../api/IConversationsApi'
import { ConversationsApiProvider } from './conversationsPort'
import { useConversation, useConversations, useConversationsRefresh } from './useConversations'

const never = () => Promise.reject(new Error('non attendu'))
const id = (n: number) => `0193a1b2-0000-7000-8000-${String(n).padStart(12, '0')}`

function conversation(n: number, day: number): Conversation {
  return {
    id: id(n),
    type: 'GROUP',
    name: `Groupe ${String(n)}`,
    members: [],
    createdAt: new Date(Date.UTC(2026, 10, day)),
    lastServerSeq: 0,
  }
}

function wrapper(api: Partial<IConversationsApi>) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const full: IConversationsApi = { list: never, get: never, ...api }
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>
      <ConversationsApiProvider api={full}>{children}</ConversationsApiProvider>
    </QueryClientProvider>
  )
}

describe('entities/conversation : liste complète et détail (CNV-04, ADR 0037 point 3)', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('CNV-04, ADR 0041 (point 2) : toutes les pages sont lues, puis triées de la plus récente à la plus ancienne', async () => {
    const pages: Record<string, Page<Conversation>> = {
      start: { items: [conversation(1, 2), conversation(2, 20)], next: id(2) },
      [id(2)]: { items: [], next: id(3) },
      [id(3)]: { items: [conversation(4, 10)], next: null },
    }
    const afters: (string | null)[] = []
    const Wrapper = wrapper({
      list: (after) => {
        afters.push(after)
        return Promise.resolve(pages[after ?? 'start'] ?? { items: [], next: null })
      },
    })

    const { result } = await renderHook(() => useConversations(), { wrapper: Wrapper })

    await expect
      .poll(() => result.current.data?.map((c) => c.name))
      .toEqual(['Groupe 2', 'Groupe 4', 'Groupe 1'])
    expect(afters).toEqual([null, id(2), id(3)])
  })

  it('CNV-06 : le détail se lit pour l’identifiant demandé, et rien sans identifiant', async () => {
    const asked: string[] = []
    const Wrapper = wrapper({
      get: (wanted) => {
        asked.push(wanted)
        return Promise.resolve(conversation(7, 3))
      },
    })

    const none = await renderHook(() => useConversation(null), { wrapper: Wrapper })
    const one = await renderHook(() => useConversation(id(7)), { wrapper: Wrapper })

    await expect.poll(() => one.result.current.data?.name).toBe('Groupe 7')
    expect(none.result.current.fetchStatus).toBe('idle')
    expect(asked).toEqual([id(7)])
  })

  it('ADR 0041 (point 8), AUTH-52 : la liste ne se relit toutes les 60 s qu’après une action', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] })
    let reads = 0
    const Wrapper = wrapper({
      list: () => {
        reads += 1
        return Promise.resolve({ items: [], next: null })
      },
    })
    await renderHook(
      () => {
        useConversations()
        useConversationsRefresh()
      },
      { wrapper: Wrapper },
    )
    await expect.poll(() => reads).toBe(1)

    vi.advanceTimersByTime(3 * 60_000)
    await new Promise((resolve) => setTimeout(resolve, 50))
    expect(reads).toBe(1)

    window.dispatchEvent(new KeyboardEvent('keydown'))
    vi.advanceTimersByTime(60_000)
    await expect.poll(() => reads).toBe(2)
  })

  it('ADR 0041 (point 8) : la liste et la conversation ouverte se relisent au retour du focus', async () => {
    let lists = 0
    let gets = 0
    const Wrapper = wrapper({
      list: () => {
        lists += 1
        return Promise.resolve({ items: [], next: null })
      },
      get: () => {
        gets += 1
        return Promise.resolve(conversation(1, 1))
      },
    })
    await renderHook(
      () => {
        useConversations()
        useConversation(id(1))
      },
      { wrapper: Wrapper },
    )
    await expect.poll(() => [lists, gets]).toEqual([1, 1])

    try {
      focusManager.setFocused(false)
      focusManager.setFocused(true)
      await expect.poll(() => [lists, gets]).toEqual([2, 2])
    } finally {
      focusManager.setFocused(undefined)
    }
  })
})
