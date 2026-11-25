import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { renderHook } from 'vitest-browser-react'
import { ApiError } from '@/shared/lib'
import { RemoveMemberApiProvider } from './removeMemberPort'
import { useRemoveMember } from './useRemoveMember'

describe('features/remove-group-member : un seul envoi à la fois (A06)', () => {
  it('GRP-03, A06 : deux demandes rapprochées n’envoient qu’une requête', async () => {
    const call = vi.fn(() => new Promise<never>(() => undefined))
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={client}>
        <RemoveMemberApiProvider api={{ exclude: call }}>{children}</RemoveMemberApiProvider>
      </QueryClientProvider>
    )
    const { result } = await renderHook(() => useRemoveMember(() => undefined), { wrapper })

    result.current.remove({ conversationId: 'g-1', userId: 'u-1' })
    result.current.remove({ conversationId: 'g-1', userId: 'u-1' })

    await expect.poll(() => call.mock.calls.length).toBe(1)
    await new Promise((resolve) => setTimeout(resolve, 50))
    expect(call).toHaveBeenCalledTimes(1)
  })

  it('CNV-06, ADR 0041 (point 4) : un groupe disparu (NOT_FOUND) s’annonce et relit les conversations', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const invalidated = vi.spyOn(client, 'invalidateQueries')
    const onDone = vi.fn()
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={client}>
        <RemoveMemberApiProvider
          api={{ exclude: () => Promise.reject(new ApiError(404, 'NOT_FOUND')) }}
        >
          {children}
        </RemoveMemberApiProvider>
      </QueryClientProvider>
    )
    const { result } = await renderHook(() => useRemoveMember(onDone), { wrapper })

    result.current.remove({ conversationId: 'g-1', userId: 'u-1' })

    await expect.poll(() => result.current.failure).toEqual({ kind: 'gone' })
    expect(invalidated).toHaveBeenCalledWith({ queryKey: ['conversations'] })
    expect(onDone).not.toHaveBeenCalled()
  })
})
