import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { renderHook } from 'vitest-browser-react'
import type { Conversation } from '@/entities/conversation'
import { ApiError, NetworkError } from '@/shared/lib'
import type { ICreateGroupApi } from '../api/ICreateGroupApi'
import { CreateGroupApiProvider } from './createGroupPort'
import { useCreateGroup } from './useCreateGroup'

const GROUP: Conversation = {
  id: '0193a1b2-0000-7000-8000-000000000004',
  type: 'GROUP',
  name: 'Ski',
  members: [],
  createdAt: new Date(0),
  lastServerSeq: 0,
}

function setup(api: ICreateGroupApi) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const invalidated = vi.spyOn(client, 'invalidateQueries')
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>
      <CreateGroupApiProvider api={api}>{children}</CreateGroupApiProvider>
    </QueryClientProvider>
  )
  return { client, invalidated, wrapper }
}

describe('features/create-group : créer un groupe (CNV-03, GRP-02, FRD-21)', () => {
  it('CNV-03 : le groupe créé est gardé, la liste relue, puis montré', async () => {
    const onCreated = vi.fn()
    const createGroup = vi.fn(() => Promise.resolve(GROUP))
    const { client, wrapper } = setup({ createGroup })
    const { result } = await renderHook(() => useCreateGroup(onCreated), { wrapper })

    result.current.create({ name: 'Ski', memberIds: ['a', 'b'] })

    await expect.poll(() => onCreated.mock.calls).toEqual([[GROUP.id]])
    expect(createGroup).toHaveBeenCalledWith('Ski', ['a', 'b'])
    expect(client.getQueryData(['conversations', 'detail', GROUP.id])).toEqual(GROUP)
  })

  it('GRP-01, ADR 0041 (point 4) : un nom refusé par le serveur se dit sous le champ', async () => {
    const { wrapper } = setup({
      createGroup: () =>
        Promise.reject(new ApiError(400, 'VALIDATION_FAILED', { fields: ['name'] })),
    })
    const { result } = await renderHook(() => useCreateGroup(() => undefined), { wrapper })

    result.current.create({ name: 'Ski', memberIds: ['a'] })

    await expect.poll(() => result.current.failure).toEqual({ kind: 'invalid-name' })
  })

  it('FRD-21 : un ami retiré entre-temps refuse le groupe, et la liste d’amis est relue', async () => {
    const { invalidated, wrapper } = setup({
      createGroup: () => Promise.reject(new ApiError(403, 'NOT_FRIENDS')),
    })
    const { result } = await renderHook(() => useCreateGroup(() => undefined), { wrapper })

    result.current.create({ name: 'Ski', memberIds: ['a'] })

    await expect.poll(() => result.current.failure).toEqual({ kind: 'not-friends' })
    expect(invalidated).toHaveBeenCalledWith({ queryKey: ['friends'] })
  })

  it('A06 : deux créations rapprochées n’envoient qu’une requête', async () => {
    const createGroup = vi.fn(() => new Promise<never>(() => undefined))
    const { wrapper } = setup({ createGroup })
    const { result } = await renderHook(() => useCreateGroup(() => undefined), { wrapper })

    result.current.create({ name: 'Ski', memberIds: ['a'] })
    result.current.create({ name: 'Ski', memberIds: ['a'] })

    await expect.poll(() => createGroup.mock.calls.length).toBe(1)
    await new Promise((resolve) => setTimeout(resolve, 50))
    expect(createGroup).toHaveBeenCalledTimes(1)
  })

  it('ADR 0041 (point 4) : une panne réseau reprend l’issue commune des formulaires', async () => {
    const { wrapper } = setup({ createGroup: () => Promise.reject(new NetworkError()) })
    const { result } = await renderHook(() => useCreateGroup(() => undefined), { wrapper })

    result.current.create({ name: 'Ski', memberIds: ['a'] })

    await expect.poll(() => result.current.failure).toEqual({ kind: 'network' })
  })
})
