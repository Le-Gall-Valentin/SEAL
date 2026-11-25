import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { renderHook } from 'vitest-browser-react'
import { LeaveGroupApiProvider } from './leaveGroupPort'
import { useLeaveGroup } from './useLeaveGroup'

describe('features/leave-group : un seul envoi à la fois (A06)', () => {
  it('GRP-03, A06 : deux demandes rapprochées n’envoient qu’une requête', async () => {
    const call = vi.fn(() => new Promise<never>(() => undefined))
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={client}>
        <LeaveGroupApiProvider api={{ leave: call }}>{children}</LeaveGroupApiProvider>
      </QueryClientProvider>
    )
    const { result } = await renderHook(() => useLeaveGroup(() => undefined), { wrapper })

    result.current.leave('g-1')
    result.current.leave('g-1')

    await expect.poll(() => call.mock.calls.length).toBe(1)
    await new Promise((resolve) => setTimeout(resolve, 50))
    expect(call).toHaveBeenCalledTimes(1)
  })
})
