import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { renderHook } from 'vitest-browser-react'
import type { Conversation } from '@/entities/conversation'
import { ApiError } from '@/shared/lib'
import type { IStartDirectApi } from '../api/IStartDirectApi'
import { StartDirectApiProvider } from './startDirectPort'
import { useStartDirect } from './useStartDirect'

const DIRECT: Conversation = {
  id: '0193a1b2-0000-7000-8000-000000000003',
  type: 'DIRECT',
  name: null,
  members: [],
  createdAt: new Date(0),
  lastServerSeq: 0,
}

function setup(api: IStartDirectApi) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const invalidated = vi.spyOn(client, 'invalidateQueries')
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>
      <StartDirectApiProvider api={api}>{children}</StartDirectApiProvider>
    </QueryClientProvider>
  )
  return { client, invalidated, wrapper }
}

describe('features/start-direct-conversation : ouvrir une directe (CNV-01, FRD-21)', () => {
  it('CNV-01 : la directe trouvée ou créée est gardée, la liste relue, puis montrée', async () => {
    const onOpened = vi.fn()
    const { client, invalidated, wrapper } = setup({
      openDirect: () => Promise.resolve({ created: false, conversation: DIRECT }),
    })
    const { result } = await renderHook(() => useStartDirect(onOpened), { wrapper })

    result.current.open('ami')

    await expect.poll(() => onOpened.mock.calls).toEqual([[DIRECT.id]])
    expect(client.getQueryData(['conversations', 'detail', DIRECT.id])).toEqual(DIRECT)
    expect(invalidated).toHaveBeenCalledWith({ queryKey: ['conversations'] })
  })

  it('FRD-21, ADR 0041 (point 4) : NOT_FRIENDS se dit, et la liste d’amis est relue', async () => {
    const { invalidated, wrapper } = setup({
      openDirect: () => Promise.reject(new ApiError(403, 'NOT_FRIENDS')),
    })
    const { result } = await renderHook(() => useStartDirect(() => undefined), { wrapper })

    result.current.open('ancien.ami')

    await expect.poll(() => result.current.failure).toEqual({ kind: 'not-friends' })
    expect(invalidated).toHaveBeenCalledWith({ queryKey: ['friends'] })
  })

  it('A06 : deux demandes rapprochées n’envoient qu’une requête', async () => {
    const openDirect = vi.fn(() => Promise.resolve({ created: true, conversation: DIRECT }))
    const { wrapper } = setup({ openDirect })
    const { result } = await renderHook(() => useStartDirect(() => undefined), { wrapper })

    result.current.open('ami')
    result.current.open('ami')

    await expect.poll(() => result.current.pending).toBe(false)
    expect(openDirect).toHaveBeenCalledTimes(1)
  })
})
