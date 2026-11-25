import { ApiError, NetworkError, toErrorCode } from '@/shared/lib'
import { parseServerFrame, type JsonValue, type ReplyOutcome, type ServerEvent } from './frames'

/** TRN-14 : un ping toutes les 25 secondes, sous les 60 secondes de silence qui ferment la connexion (4408). */
export const PING_INTERVAL_MS = 25_000
/** TRN-14 : délai de réponse d'une trame. */
export const REPLY_TIMEOUT_MS = 10_000
/** TRN-15 : attentes avant de rouvrir, en secondes ; la dernière se répète. */
const BACKOFF_SECONDS = [1, 2, 4, 8, 16, 30] as const
/** TRN-13 : refus de suite (4429) avant d'abandonner. */
const MAX_REFUSALS = 3

const NORMAL_CLOSURE = 1000
const SESSION_ENDED = 4401
const TOO_MANY_CONNECTIONS = 4429

/** Ce que la connexion écoute d'une WebSocket. */
export interface SocketHandlers {
  readonly onOpen: () => void
  readonly onMessage: (data: unknown) => void
  readonly onClose: (code: number) => void
}

/** Ce que la connexion utilise d'une WebSocket ; les tests en injectent une fausse. */
export interface SocketLike {
  isOpen(): boolean
  send(data: string): void
  close(code: number): void
}

export type SocketFactory = (url: string, handlers: SocketHandlers) => SocketLike

/** Seul endroit du client qui ouvre une WebSocket (ADR 0012). */
const browserSocket: SocketFactory = (url, handlers) => {
  const socket = new WebSocket(url)
  socket.onopen = () => {
    handlers.onOpen()
  }
  socket.onmessage = (event) => {
    handlers.onMessage(event.data)
  }
  socket.onclose = (event) => {
    handlers.onClose(event.code)
  }
  return {
    isOpen: () => socket.readyState === WebSocket.OPEN,
    send: (data) => {
      socket.send(data)
    },
    close: (code) => {
      socket.close(code)
    },
  }
}

/** Fin définitive : session finie (4401, `session.revoked`) ou connexions refusées (4429, trois fois). */
export type RealtimeEnd = 'session' | 'refused'

export interface RealtimeOptions {
  readonly url: string
  /** Un événement validé du serveur. */
  readonly onFrame: (event: ServerEvent) => void
  /** TRN-15 : chaque (re)connexion, pour relire ce qui a pu être manqué. */
  readonly onOpen: () => void
  readonly onEnd: (reason: RealtimeEnd) => void
  readonly createSocket?: SocketFactory
}

export interface RealtimeConnection {
  /** Envoie une trame et rend la charge de son `ack`, ou échoue sur son `error`, une coupure ou 10 s de silence. */
  send(type: string, payload: Record<string, JsonValue>): Promise<JsonValue>
  close(): void
}

interface Pending {
  readonly resolve: (payload: JsonValue) => void
  readonly reject: (error: Error) => void
  readonly timer: ReturnType<typeof setTimeout>
}

function errorOf(outcome: Extract<ReplyOutcome, { ok: false }>): ApiError {
  // Trame WebSocket : aucun statut HTTP.
  return new ApiError(0, toErrorCode(outcome.code), {
    retryAfterSeconds: outcome.retryAfterSeconds,
  })
}

/** TRN-14, TRN-15 : connexion à /ws, entretenue par ping et rétablie après une coupure. */
export function createRealtimeConnection(options: RealtimeOptions): RealtimeConnection {
  const createSocket = options.createSocket ?? browserSocket
  const pending = new Map<string, Pending>()
  let socket: SocketLike | null = null
  let ping: ReturnType<typeof setInterval> | null = null
  let reconnect: ReturnType<typeof setTimeout> | null = null
  let attempt = 0
  let refusals = 0
  let ended = false
  let nextId = 0

  // L'id n'a rien de secret : un compteur suffit (la WebCrypto reste dans shared/crypto).
  const frameId = () => `c${String(++nextId)}`

  const write = (type: string, id: string, payload: Record<string, JsonValue>) => {
    socket?.send(JSON.stringify({ type, id, payload }))
  }

  const failPending = () => {
    pending.forEach(({ reject, timer }) => {
      clearTimeout(timer)
      reject(new NetworkError())
    })
    pending.clear()
  }

  const stopPing = () => {
    if (ping !== null) {
      clearInterval(ping)
      ping = null
    }
  }

  const end = (reason: RealtimeEnd) => {
    if (ended) {
      return
    }
    ended = true
    stopPing()
    failPending()
    socket?.close(NORMAL_CLOSURE)
    options.onEnd(reason)
  }

  const settle = (id: string, outcome: ReplyOutcome) => {
    const call = pending.get(id)
    if (call === undefined) {
      return
    }
    pending.delete(id)
    clearTimeout(call.timer)
    if (outcome.ok) {
      call.resolve(outcome.payload)
    } else {
      call.reject(errorOf(outcome))
    }
  }

  const receive = (data: unknown) => {
    if (typeof data !== 'string') {
      return
    }
    const frame = parseServerFrame(data)
    if (frame === null) {
      return
    }
    // TRN-15 : la connexion répond ; la prochaine coupure repart d'une seconde.
    attempt = 0
    refusals = 0
    if (frame.kind === 'reply') {
      settle(frame.id, frame.outcome)
    } else if (frame.event.type === 'session.revoked') {
      end('session')
    } else {
      options.onFrame(frame.event)
    }
  }

  const closed = (code: number) => {
    stopPing()
    failPending()
    socket = null
    if (ended) {
      return
    }
    if (code === SESSION_ENDED) {
      end('session')
      return
    }
    if (code === TOO_MANY_CONNECTIONS && ++refusals >= MAX_REFUSALS) {
      end('refused')
      return
    }
    const seconds = BACKOFF_SECONDS[Math.min(attempt, BACKOFF_SECONDS.length - 1)] ?? 30
    attempt++
    reconnect = setTimeout(open, seconds * 1000)
  }

  function open() {
    reconnect = null
    const current: SocketLike = createSocket(options.url, {
      onOpen: () => {
        write('ping', frameId(), {})
        ping = setInterval(() => {
          write('ping', frameId(), {})
        }, PING_INTERVAL_MS)
        options.onOpen()
      },
      onMessage: (data) => {
        if (socket === current) {
          receive(data)
        }
      },
      onClose: (code) => {
        if (socket === current) {
          closed(code)
        }
      },
    })
    socket = current
  }

  open()

  return {
    send(type, payload) {
      if (ended || socket === null || !socket.isOpen()) {
        return Promise.reject(new NetworkError())
      }
      const id = frameId()
      return new Promise<JsonValue>((resolve, reject) => {
        const timer = setTimeout(() => {
          pending.delete(id)
          reject(new NetworkError())
        }, REPLY_TIMEOUT_MS)
        pending.set(id, { resolve, reject, timer })
        write(type, id, payload)
      })
    },
    close() {
      if (reconnect !== null) {
        clearTimeout(reconnect)
        reconnect = null
      }
      ended = true
      stopPing()
      failPending()
      socket?.close(NORMAL_CLOSURE)
      socket = null
    },
  }
}
