import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { renderHook } from 'vitest-browser-react'
import type { Conversation } from '@/entities/conversation'
import { ApiError } from '@/shared/lib'
import { RenameGroupApiProvider } from './renameGroupPort'
import { useRenameGroup } from './useRenameGroup'

function setup(rename: (conversationId: string, name: string) => Promise<Conversation>) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const invalidated = vi.spyOn(client, 'invalidateQueries')
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>
      <RenameGroupApiProvider api={{ rename }}>{children}</RenameGroupApiProvider>
    </QueryClientProvider>
  )
  return { invalidated, wrapper }
}

describe('features/rename-group : renommer un groupe (GRP-03, A06)', () => {
  it('GRP-03, A06 : deux renommages rapprochés n’envoient qu’une requête', async () => {
    const rename = vi.fn(() => new Promise<never>(() => undefined))
    const { wrapper } = setup(rename)
    const { result } = await renderHook(() => useRenameGroup(() => undefined), { wrapper })

    result.current.rename({ conversationId: 'g-1', name: 'Ski' })
    result.current.rename({ conversationId: 'g-1', name: 'Ski' })

    await expect.poll(() => rename.mock.calls.length).toBe(1)
    await new Promise((resolve) => setTimeout(resolve, 50))
    expect(rename).toHaveBeenCalledTimes(1)
  })

  it('GRP-03, CNV-06, ADR 0041 (point 4) : un groupe disparu (NOT_FOUND) s’annonce et relit les conversations', async () => {
    const onDone = vi.fn()
    const { invalidated, wrapper } = setup(() => Promise.reject(new ApiError(404, 'NOT_FOUND')))
    const { result } = await renderHook(() => useRenameGroup(onDone), { wrapper })

    result.current.rename({ conversationId: 'g-1', name: 'Ski' })

    await expect.poll(() => result.current.failure).toEqual({ kind: 'gone' })
    expect(invalidated).toHaveBeenCalledWith({ queryKey: ['conversations'] })
    expect(onDone).not.toHaveBeenCalled()
  })
})
