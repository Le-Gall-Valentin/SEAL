import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, NetworkError } from '@/shared/lib'
import {
  createRealtimeConnection,
  type RealtimeOptions,
  type SocketHandlers,
  type SocketLike,
} from './connection'

const UUID = '0192f7a1-0000-7000-8000-000000000001'

class FakeSocket implements SocketLike {
  static all: FakeSocket[] = []
  readonly sent: string[] = []
  readonly url: string
  closedWith: number | null = null
  private opened = false
  private readonly handlers: SocketHandlers

  constructor(url: string, handlers: SocketHandlers) {
    this.url = url
    this.handlers = handlers
    FakeSocket.all.push(this)
  }

  isOpen(): boolean {
    return this.opened
  }

  send(data: string): void {
    this.sent.push(data)
  }

  close(code: number): void {
    this.closedWith = code
  }

  open(): void {
    this.opened = true
    this.handlers.onOpen()
  }

  receive(frame: unknown): void {
    this.handlers.onMessage(typeof frame === 'string' ? frame : JSON.stringify(frame))
  }

  drop(code: number): void {
    this.opened = false
    this.handlers.onClose(code)
  }

  /** Les trames envoyées, lues. */
  written(): { type: string; id: string; payload: unknown }[] {
    return this.sent.map(
      (text) => JSON.parse(text) as { type: string; id: string; payload: unknown },
    )
  }

  lastId(): string {
    return this.written().at(-1)?.id ?? ''
  }
}

function last(): FakeSocket {
  const socket = FakeSocket.all.at(-1)
  if (socket === undefined) {
    throw new Error('aucune connexion ouverte')
  }
  return socket
}

function connect(overrides: Partial<RealtimeOptions> = {}) {
  const options = {
    url: 'wss://seal.test/ws',
    onFrame: vi.fn(),
    onOpen: vi.fn(),
    onEnd: vi.fn(),
    createSocket: (url: string, handlers: SocketHandlers) => new FakeSocket(url, handlers),
    ...overrides,
  }
  return { options, connection: createRealtimeConnection(options) }
}

describe('shared/ws : connexion temps réel (TRN-14, TRN-15)', () => {
  beforeEach(() => {
    FakeSocket.all = []
    vi.useFakeTimers()
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('TRN-14 : un ping à l’ouverture, puis toutes les 25 secondes', () => {
    connect()
    last().open()

    expect(
      last()
        .written()
        .map((frame) => frame.type),
    ).toEqual(['ping'])
    vi.advanceTimersByTime(25_000)
    vi.advanceTimersByTime(25_000)

    const pings = last().written()
    expect(pings.map((frame) => frame.type)).toEqual(['ping', 'ping', 'ping'])
    expect(new Set(pings.map((frame) => frame.id)).size).toBe(3)
    pings.forEach((frame) => {
      expect(frame.id).toMatch(/^[A-Za-z0-9_-]{1,64}$/)
    })
  })

  it('TRN-14 : ack et error reviennent à leur appel, par l’id', async () => {
    const { connection } = connect()
    last().open()

    const sent = connection.send('msg.send', { n: 1 })
    const firstId = last().lastId()
    const refused = connection.send('msg.send', { n: 2 })
    const secondId = last().lastId()
    last().receive({
      type: 'error',
      id: secondId,
      payload: { code: 'RATE_LIMITED', retryAfter: 3 },
    })
    last().receive({ type: 'ack', id: firstId, payload: { serverSeq: 7 } })

    await expect(sent).resolves.toEqual({ serverSeq: 7 })
    await expect(refused).rejects.toSatisfy(
      (error) =>
        error instanceof ApiError && error.code === 'RATE_LIMITED' && error.retryAfterSeconds === 3,
    )
    expect(last().written().at(-1)).toMatchObject({ type: 'msg.send', payload: { n: 2 } })
  })

  it('TRN-14 : sans réponse en 10 secondes, l’envoi échoue', async () => {
    const { connection } = connect()
    last().open()

    const sent = connection.send('msg.send', {})
    vi.advanceTimersByTime(10_000)

    await expect(sent).rejects.toBeInstanceOf(NetworkError)
  })

  it('TRN-14 : hors connexion, l’envoi échoue aussitôt', async () => {
    const { connection } = connect()

    await expect(connection.send('msg.send', {})).rejects.toBeInstanceOf(NetworkError)
  })

  it('TRN-15 : une coupure rouvre après 1, 2, 4, 8, 16 puis 30 secondes, et rappelle onOpen', () => {
    const { options } = connect()
    last().open()
    const waits: number[] = []

    for (let attempt = 0; attempt < 7; attempt++) {
      const before = FakeSocket.all.length
      last().drop(1006)
      let waited = 0
      while (FakeSocket.all.length === before) {
        vi.advanceTimersByTime(1_000)
        waited += 1
      }
      waits.push(waited)
    }
    last().open()

    expect(waits).toEqual([1, 2, 4, 8, 16, 30, 30])
    expect(options.onOpen).toHaveBeenCalledTimes(2)
  })

  it('TRN-15 : une trame reçue remet l’attente à une seconde', () => {
    connect()
    last().drop(1006)
    vi.advanceTimersByTime(1_000)
    last().drop(1006)
    vi.advanceTimersByTime(2_000)
    last().open()
    last().receive({ type: 'pong', id: last().lastId(), payload: {} })
    const before = FakeSocket.all.length

    last().drop(1006)
    vi.advanceTimersByTime(1_000)

    expect(FakeSocket.all.length).toBe(before + 1)
  })

  it('AUTH-53 : 4401 met fin à la connexion, sans la rouvrir, et fait échouer les envois en cours', async () => {
    const { options, connection } = connect()
    last().open()
    const pending = connection.send('msg.send', {})

    last().drop(4401)
    vi.advanceTimersByTime(60_000)

    expect(options.onEnd).toHaveBeenCalledExactlyOnceWith('session')
    expect(FakeSocket.all).toHaveLength(1)
    await expect(pending).rejects.toBeInstanceOf(NetworkError)
  })

  it('TRN-12 : session.revoked met fin à la connexion une seule fois, même suivie de 4401', () => {
    const { options } = connect()
    last().open()

    last().receive({ type: 'session.revoked', id: 's1', payload: {} })
    last().drop(4401)
    vi.advanceTimersByTime(60_000)

    expect(options.onEnd).toHaveBeenCalledExactlyOnceWith('session')
    expect(options.onFrame).not.toHaveBeenCalled()
    expect(FakeSocket.all).toHaveLength(1)
  })

  it('TRN-13 : 4429 trois fois de suite met fin à la connexion', () => {
    const { options } = connect()

    for (let refusal = 0; refusal < 3; refusal++) {
      last().open()
      last().drop(4429)
      vi.advanceTimersByTime(30_000)
    }

    expect(options.onEnd).toHaveBeenCalledExactlyOnceWith('refused')
    expect(FakeSocket.all).toHaveLength(3)
  })

  it('A10 : une trame illisible ou hors schéma n’atteint jamais onFrame ; une trame valide, si', () => {
    const { options } = connect()
    last().open()

    last().receive('pas du JSON')
    last().receive({ type: 'friend.request', id: 's1', payload: { requestId: 'x' } })
    last().receive({ type: 'msg.new', id: 's2', payload: {} })
    last().receive({ type: 'friend.request', id: 's3', payload: { requestId: UUID } })

    expect(options.onFrame).toHaveBeenCalledExactlyOnceWith({
      type: 'friend.request',
      requestId: UUID,
    })
  })

  it('close() ferme en 1000 et ne rouvre plus', () => {
    const { connection } = connect()
    last().open()

    connection.close()
    last().drop(1000)
    vi.advanceTimersByTime(60_000)

    expect(last().closedWith).toBe(1000)
    expect(FakeSocket.all).toHaveLength(1)
  })
})
