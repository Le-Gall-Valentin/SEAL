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

  /** Le serveur répond au dernier ping ; la connexion est alors confirmée (TRN-15). */
  pong(): void {
    const ping = this.written()
      .filter((frame) => frame.type === 'ping')
      .at(-1)
    this.receive({ type: 'pong', id: ping?.id ?? 'x', payload: {} })
  }

  /** Ouverture confirmée par le serveur. */
  accept(): void {
    this.open()
    this.pong()
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

describe('shared/ws : connexion temps réel (TRN-13, TRN-14, TRN-15, ADR 0043, ADR 0044)', () => {
  beforeEach(() => {
    FakeSocket.all = []
    vi.useFakeTimers()
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('TRN-13 : un ping à l’ouverture, puis toutes les 25 secondes', () => {
    connect()
    last().accept()

    vi.advanceTimersByTime(25_000)
    last().pong()
    vi.advanceTimersByTime(25_000)

    const pings = last().written()
    expect(pings.map((frame) => frame.type)).toEqual(['ping', 'ping', 'ping'])
    expect(new Set(pings.map((frame) => frame.id)).size).toBe(3)
    pings.forEach((frame) => {
      expect(frame.id).toMatch(/^[A-Za-z0-9_-]{1,64}$/)
    })
  })

  it('ADR 0044 (décision 7) : un ping sans pong en 10 secondes abandonne la socket et rouvre', () => {
    connect()
    last().accept()
    vi.advanceTimersByTime(25_000)
    const silent = last()

    vi.advanceTimersByTime(10_000)

    expect(silent.closedWith).toBe(4000)
    vi.advanceTimersByTime(1_000)
    expect(FakeSocket.all).toHaveLength(2)
  })

  it('TRN-14 : l’accusé et l’erreur reviennent à leur appel, par l’id', async () => {
    const { connection } = connect()
    last().accept()

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
  })

  it('ADR 0043 : sans réponse en 10 secondes, l’envoi échoue', async () => {
    const { connection } = connect()
    last().accept()

    const sent = connection.send('msg.send', {})
    vi.advanceTimersByTime(10_000)

    await expect(sent).rejects.toBeInstanceOf(NetworkError)
  })

  it('TRN-14 : hors connexion, l’envoi échoue aussitôt', async () => {
    const { connection } = connect()

    await expect(connection.send('msg.send', {})).rejects.toBeInstanceOf(NetworkError)
  })

  it('TRN-15 : une coupure fait échouer aussitôt les envois en cours', async () => {
    const { connection } = connect()
    last().accept()
    const sent = connection.send('msg.send', {})

    last().drop(1006)

    await expect(sent).rejects.toBeInstanceOf(NetworkError)
  })

  it('ADR 0043 (TRN-15) : une coupure rouvre après 1, 2, 4, 8, 16 puis 30 secondes', () => {
    connect()
    last().accept()
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

    expect(waits).toEqual([1, 2, 4, 8, 16, 30, 30])
  })

  it('TRN-15 : onOpen part à la première réponse du serveur, pas à l’ouverture de la socket', () => {
    const { options } = connect()

    last().open()
    expect(options.onOpen).not.toHaveBeenCalled()
    last().pong()

    expect(options.onOpen).toHaveBeenCalledOnce()
  })

  it('TRN-15 : une réponse remet l’attente à une seconde', () => {
    connect()
    last().drop(1006)
    vi.advanceTimersByTime(1_000)
    last().drop(1006)
    vi.advanceTimersByTime(2_000)
    last().accept()
    const before = FakeSocket.all.length

    last().drop(1006)
    vi.advanceTimersByTime(1_000)

    expect(FakeSocket.all.length).toBe(before + 1)
  })

  it('AUTH-52, ADR 0044 (décision 2) : la réouverture attend le feu vert de beforeReconnect', () => {
    const resumes: (() => void)[] = []
    connect({
      beforeReconnect: (go) => {
        resumes.push(go)
      },
    })
    last().accept()

    last().drop(1006)
    vi.advanceTimersByTime(60_000)
    expect(FakeSocket.all).toHaveLength(1)

    expect(resumes).toHaveLength(1)
    resumes.at(-1)?.()
    expect(FakeSocket.all).toHaveLength(2)
  })

  it('ADR 0044 (décision 2) : un double feu vert ne rouvre qu’une connexion', () => {
    const resumes: (() => void)[] = []
    connect({
      beforeReconnect: (go) => {
        resumes.push(go)
      },
    })
    last().drop(1006)
    vi.advanceTimersByTime(1_000)

    resumes.at(-1)?.()
    resumes.at(-1)?.()

    expect(FakeSocket.all).toHaveLength(2)
  })

  it('TRN-15 : close() pendant une réouverture programmée ou en attente ne rouvre jamais', () => {
    const resumes: (() => void)[] = []
    const { connection } = connect({
      beforeReconnect: (go) => {
        resumes.push(go)
      },
    })
    last().drop(1006)
    connection.close()
    vi.advanceTimersByTime(60_000)
    expect(FakeSocket.all).toHaveLength(1)

    const other = connect({
      beforeReconnect: (go) => {
        resumes.push(go)
      },
    })
    last().drop(1006)
    vi.advanceTimersByTime(1_000)
    other.connection.close()
    resumes.at(-1)?.()

    expect(FakeSocket.all).toHaveLength(2)
  })

  it('ADR 0044 (décision 2) : trois échecs sans réponse du serveur signalent la connexion injoignable, une fois', () => {
    const onUnreachable = vi.fn()
    connect({ onUnreachable })

    for (let failure = 0; failure < 4; failure++) {
      last().drop(1006)
      vi.advanceTimersByTime(30_000)
    }

    expect(onUnreachable).toHaveBeenCalledOnce()
  })

  it('AUTH-53 : 4401 met fin à la connexion, sans la rouvrir, et fait échouer les envois en cours', async () => {
    const { options, connection } = connect()
    last().accept()
    const pending = connection.send('msg.send', {})

    last().drop(4401)
    vi.advanceTimersByTime(60_000)

    expect(options.onEnd).toHaveBeenCalledExactlyOnceWith('session')
    expect(FakeSocket.all).toHaveLength(1)
    await expect(pending).rejects.toBeInstanceOf(NetworkError)
  })

  it('TRN-12 : session.revoked met fin à la connexion une seule fois, même suivie de 4401', () => {
    const { options } = connect()
    last().accept()

    last().receive({ type: 'session.revoked', id: 's1', payload: {} })
    last().drop(4401)
    vi.advanceTimersByTime(60_000)

    expect(options.onEnd).toHaveBeenCalledExactlyOnceWith('session')
    expect(options.onFrame).not.toHaveBeenCalled()
    expect(FakeSocket.all).toHaveLength(1)
  })

  it('TRN-13 : 4429 trois fois de suite met fin à la connexion, sans aucun rattrapage', () => {
    const { options } = connect()

    for (let refusal = 0; refusal < 3; refusal++) {
      last().open()
      last().drop(4429)
      vi.advanceTimersByTime(30_000)
    }

    expect(options.onEnd).toHaveBeenCalledExactlyOnceWith('refused')
    expect(options.onOpen).not.toHaveBeenCalled()
    expect(FakeSocket.all).toHaveLength(3)
  })

  it('A10 : une trame illisible ou hors schéma n’atteint jamais onFrame ; une trame valide, si', () => {
    const { options } = connect()
    last().accept()

    last().receive('pas du JSON')
    last().receive({ type: 'friend.request', id: 's1', payload: { requestId: 'x' } })
    last().receive({ type: 'msg.new', id: 's2', payload: {} })
    last().receive({ type: 'friend.request', id: 's3', payload: { requestId: UUID } })

    expect(options.onFrame).toHaveBeenCalledExactlyOnceWith({
      type: 'friend.request',
      requestId: UUID,
    })
  })

  it('AUTH-53 : close() ferme en 1000 et ne rouvre plus ; une ouverture tardive n’envoie rien', () => {
    const { connection } = connect()
    const socket = last()

    connection.close()
    socket.open()
    socket.drop(1000)
    vi.advanceTimersByTime(60_000)

    expect(socket.closedWith).toBe(1000)
    expect(socket.sent).toHaveLength(0)
    expect(FakeSocket.all).toHaveLength(1)
  })
})
