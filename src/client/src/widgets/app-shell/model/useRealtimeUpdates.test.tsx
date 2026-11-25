import { QueryClient, QueryClientProvider, useQuery } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { renderHook } from 'vitest-browser-react'
import { type IRealtimeApi, RealtimeApiProvider, type RealtimeListener } from '@/entities/session'
import { useRealtimeUpdates } from './useRealtimeUpdates'

function fakeRealtime() {
  const listeners: RealtimeListener[] = []
  const api: IRealtimeApi = {
    connect: (listener) => {
      listeners.push(listener)
      return () => undefined
    },
  }
  return { api, listener: () => listeners[0] }
}

function act(): void {
  window.dispatchEvent(new PointerEvent('pointerdown'))
}

/** Les deux listes, montées comme à l'écran, avec le nombre de lectures de chacune. */
async function mount() {
  const realtime = fakeRealtime()
  const client = new QueryClient()
  const reads = { friends: vi.fn(), conversations: vi.fn() }
  function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={client}>
        <RealtimeApiProvider api={realtime.api}>{children}</RealtimeApiProvider>
      </QueryClientProvider>
    )
  }
  await renderHook(
    () => {
      useRealtimeUpdates()
      useQuery({ queryKey: ['friends', 'received'], queryFn: () => (reads.friends(), []) })
      useQuery({
        queryKey: ['conversations', 'detail', 'c1'],
        queryFn: () => (reads.conversations(), {}),
      })
    },
    { wrapper: Wrapper },
  )
  await vi.waitFor(() => {
    expect(reads.friends).toHaveBeenCalledOnce()
    expect(reads.conversations).toHaveBeenCalledOnce()
  })
  return { realtime, reads, client }
}

describe('widgets/app-shell : mises à jour en direct (FRD-04, CNV-04, TRN-15)', () => {
  it('FRD-04 : friend.request relit les invitations sans attendre, après une action récente', async () => {
    const { realtime, reads } = await mount()

    act()
    realtime.listener()?.onEvent({ type: 'friend.request', requestId: 'r' })

    await vi.waitFor(() => {
      expect(reads.friends).toHaveBeenCalledTimes(2)
    })
    expect(reads.conversations).toHaveBeenCalledOnce()
  })

  it('CNV-04 : conv.update relit la conversation ouverte', async () => {
    const { realtime, reads } = await mount()

    act()
    realtime.listener()?.onEvent({ type: 'conv.update', conversationId: 'c1' })

    await vi.waitFor(() => {
      expect(reads.conversations).toHaveBeenCalledTimes(2)
    })
    expect(reads.friends).toHaveBeenCalledOnce()
  })

  it('TRN-15 : une reconnexion relit amis et conversations', async () => {
    const { realtime, reads } = await mount()

    act()
    realtime.listener()?.onOpen()

    await vi.waitFor(() => {
      expect(reads.friends).toHaveBeenCalledTimes(2)
      expect(reads.conversations).toHaveBeenCalledTimes(2)
    })
  })

  it('AUTH-52, ADR 0043 (point 4) : sans action récente, rien n’est relu avant la prochaine action', async () => {
    const { realtime, reads, client } = await mount()
    const later = Date.now() + 61_000
    vi.spyOn(Date, 'now').mockReturnValue(later)

    realtime.listener()?.onEvent({ type: 'friend.update', userId: 'u' })
    await new Promise((resolve) => setTimeout(resolve, 50))

    expect(reads.friends).toHaveBeenCalledOnce()
    expect(client.getQueryState(['friends', 'received'])?.isInvalidated).toBe(true)
    act()
    await vi.waitFor(() => {
      expect(reads.friends).toHaveBeenCalledTimes(2)
    })
    vi.restoreAllMocks()
  })
})
