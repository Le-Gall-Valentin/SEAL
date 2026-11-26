import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { renderHook } from 'vitest-browser-react'
import type { IRealtimeApi, RealtimeEvent, RealtimeListener } from '../api/IRealtimeApi'
import type { Account } from '../api/ISessionApi'
import { RealtimeApiProvider } from './realtimePort'
import { SessionApiProvider } from './sessionApiPort'
import { SESSION_QUERY_KEY } from './useCurrentAccount'
import { useRealtime } from './useRealtime'

function fakeRealtime() {
  const listeners: RealtimeListener[] = []
  const disconnect = vi.fn()
  const api: IRealtimeApi = {
    connect: (listener) => {
      listeners.push(listener)
      return disconnect
    },
  }
  return { api, listeners, disconnect }
}

const ACTIVE = { id: 'compte' } as unknown as Account

function wrapper(
  client: QueryClient,
  api: IRealtimeApi,
  me: () => Promise<Account | null> = () => Promise.resolve(ACTIVE),
) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={client}>
        <SessionApiProvider api={{ me, reloadCsrf: () => Promise.resolve() }}>
          <RealtimeApiProvider api={api}>{children}</RealtimeApiProvider>
        </SessionApiProvider>
      </QueryClientProvider>
    )
  }
}

const immediately = (resume: () => void) => {
  resume()
}

describe('entities/session : connexion temps réel liée à la session (TRN-12)', () => {
  it('TRN-10 : une seule connexion, fermée au démontage', async () => {
    const realtime = fakeRealtime()
    const { unmount } = await renderHook(
      () => {
        useRealtime({ onEvent: vi.fn(), onOpen: vi.fn(), beforeReconnect: immediately })
      },
      { wrapper: wrapper(new QueryClient(), realtime.api) },
    )

    expect(realtime.listeners).toHaveLength(1)
    await unmount()
    expect(realtime.disconnect).toHaveBeenCalledOnce()
  })

  it('FRD-04, TRN-15 : événements et (re)connexions vont aux rappels du moment', async () => {
    const realtime = fakeRealtime()
    const first = vi.fn()
    const latest = vi.fn()
    const onOpen = vi.fn()
    const { rerender } = await renderHook(
      (props?: { onEvent: (event: RealtimeEvent) => void }) => {
        useRealtime({ onEvent: props?.onEvent ?? first, onOpen, beforeReconnect: immediately })
      },
      { wrapper: wrapper(new QueryClient(), realtime.api), initialProps: { onEvent: first } },
    )
    await rerender({ onEvent: latest })

    realtime.listeners[0]?.onEvent({ type: 'conv.update', conversationId: 'c' })
    realtime.listeners[0]?.onOpen()

    expect(realtime.listeners).toHaveLength(1)
    expect(first).not.toHaveBeenCalled()
    expect(latest).toHaveBeenCalledExactlyOnceWith({ type: 'conv.update', conversationId: 'c' })
    expect(onOpen).toHaveBeenCalledOnce()
  })

  it('AUTH-53, TRN-12 : une session finie côté serveur termine la session du client', async () => {
    const realtime = fakeRealtime()
    const client = new QueryClient()
    client.setQueryData(SESSION_QUERY_KEY, { id: 'compte' })
    client.setQueryData(['friends', 'list'], [])
    await renderHook(
      () => {
        useRealtime({ onEvent: vi.fn(), onOpen: vi.fn(), beforeReconnect: immediately })
      },
      { wrapper: wrapper(client, realtime.api) },
    )

    realtime.listeners[0]?.onEnd('session')

    expect(client.getQueryData(SESSION_QUERY_KEY)).toBeNull()
    expect(client.getQueryData(['friends', 'list'])).toBeUndefined()
  })

  it('TRN-13 : des connexions refusées laissent la session ouverte', async () => {
    const realtime = fakeRealtime()
    const client = new QueryClient()
    client.setQueryData(SESSION_QUERY_KEY, { id: 'compte' })
    await renderHook(
      () => {
        useRealtime({ onEvent: vi.fn(), onOpen: vi.fn(), beforeReconnect: immediately })
      },
      { wrapper: wrapper(client, realtime.api) },
    )

    realtime.listeners[0]?.onEnd('refused')

    expect(client.getQueryData(SESSION_QUERY_KEY)).toEqual({ id: 'compte' })
  })

  it('AUTH-53, ADR 0044 (décision 2) : injoignable, /me est relu ; une session finie entre-temps est terminée et oubliée', async () => {
    const realtime = fakeRealtime()
    const client = new QueryClient()
    client.setQueryData(SESSION_QUERY_KEY, ACTIVE)
    client.setQueryData(['friends', 'list'], ['ami'])
    await renderHook(
      () => {
        useRealtime({ onEvent: vi.fn(), onOpen: vi.fn(), beforeReconnect: immediately })
      },
      { wrapper: wrapper(client, realtime.api, () => Promise.resolve(null)) },
    )

    realtime.listeners[0]?.onUnreachable()

    await vi.waitFor(() => {
      expect(client.getQueryData(SESSION_QUERY_KEY)).toBeNull()
    })
    expect(client.getQueryData(['friends', 'list'])).toBeUndefined()
  })

  it('ADR 0044 (décision 2) : injoignable mais session vivante, les données restent', async () => {
    const realtime = fakeRealtime()
    const client = new QueryClient()
    client.setQueryData(SESSION_QUERY_KEY, ACTIVE)
    client.setQueryData(['friends', 'list'], ['ami'])
    const me = vi.fn(() => Promise.resolve(ACTIVE))
    await renderHook(
      () => {
        useRealtime({ onEvent: vi.fn(), onOpen: vi.fn(), beforeReconnect: immediately })
      },
      { wrapper: wrapper(client, realtime.api, me) },
    )

    realtime.listeners[0]?.onUnreachable()

    await vi.waitFor(() => {
      expect(me).toHaveBeenCalledOnce()
    })
    expect(client.getQueryData(['friends', 'list'])).toEqual(['ami'])
  })
})
