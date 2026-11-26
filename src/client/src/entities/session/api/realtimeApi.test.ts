import { describe, expect, it, vi } from 'vitest'
import type { SocketHandlers, SocketLike } from '@/shared/ws'
import type { RealtimeListener } from './IRealtimeApi'
import { createRealtimeApi, realtimeUrl } from './realtimeApi'

const UUID = '0192f7a1-0000-7000-8000-000000000001'

function listener(): RealtimeListener {
  return {
    onEvent: vi.fn(),
    onOpen: vi.fn(),
    onEnd: vi.fn(),
    beforeReconnect: vi.fn(),
    onUnreachable: vi.fn(),
  }
}

describe('entities/session : connexion temps réel (TRN-10)', () => {
  it('TRN-10 : wss:// sur la même origine que la page, chemin /ws', () => {
    expect(realtimeUrl({ protocol: 'https:', host: 'localhost:8443' })).toBe(
      'wss://localhost:8443/ws',
    )
  })

  it('TRN-10 : ws:// pour une page servie sans TLS (serveur de développement)', () => {
    expect(realtimeUrl({ protocol: 'http:', host: 'localhost:5173' })).toBe(
      'ws://localhost:5173/ws',
    )
  })

  it('TRN-10, TRN-15 : la connexion ouvre l’adresse de la page, remet événements et rappels, et se ferme', () => {
    const opened: {
      url: string
      handlers: SocketHandlers
      socket: SocketLike & { closed: number | null }
    }[] = []
    const api = createRealtimeApi(
      (url, handlers) => {
        const socket = {
          closed: null as number | null,
          isOpen: () => true,
          send: () => undefined,
          close(code: number) {
            this.closed = code
          },
        }
        opened.push({ url, handlers, socket })
        return socket
      },
      { protocol: 'https:', host: 'seal.test' },
    )
    const events = listener()

    const disconnect = api.connect(events)
    const [first] = opened
    first?.handlers.onOpen()
    first?.handlers.onMessage(JSON.stringify({ type: 'pong', id: 'c1', payload: {} }))
    first?.handlers.onMessage(
      JSON.stringify({ type: 'friend.request', id: 's1', payload: { requestId: UUID } }),
    )
    disconnect()

    expect(first?.url).toBe('wss://seal.test/ws')
    expect(events.onOpen).toHaveBeenCalledOnce()
    expect(events.onEvent).toHaveBeenCalledExactlyOnceWith({
      type: 'friend.request',
      requestId: UUID,
    })
    expect(first?.socket.closed).toBe(1000)
  })

  it('AUTH-52, ADR 0044 (décision 2) : l’adaptateur confie la réouverture et le signal d’injoignabilité à l’écouteur', () => {
    vi.useFakeTimers()
    try {
      const opened: SocketHandlers[] = []
      const api = createRealtimeApi(
        (_url, handlers) => {
          opened.push(handlers)
          return { isOpen: () => false, send: () => undefined, close: () => undefined }
        },
        { protocol: 'https:', host: 'seal.test' },
      )
      const events = listener()
      api.connect(events)

      for (let failure = 0; failure < 3; failure++) {
        opened.at(-1)?.onClose(1006)
        vi.advanceTimersByTime(30_000)
        vi.mocked(events.beforeReconnect).mock.calls.at(-1)?.[0]()
      }

      expect(events.beforeReconnect).toHaveBeenCalledTimes(3)
      expect(events.onUnreachable).toHaveBeenCalledOnce()
    } finally {
      vi.useRealTimers()
    }
  })
})
