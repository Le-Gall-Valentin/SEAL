import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { renderHook } from 'vitest-browser-react'
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
})
