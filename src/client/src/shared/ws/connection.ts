import { ApiError, NetworkError, toErrorCode } from '@/shared/lib'
import { parseServerFrame, type JsonValue, type ReplyOutcome, type ServerEvent } from './frames'

/** TRN-13 : un ping toutes les 25 secondes, sous les 60 secondes de silence qui ferment la connexion (4408). */
export const PING_INTERVAL_MS = 25_000
/** ADR 0043, ADR 0044 (décision 7) : délai de réponse d'une trame, `pong` compris. */
export const REPLY_TIMEOUT_MS = 10_000
/** ADR 0043 (TRN-15) : attentes avant de rouvrir, en secondes ; la dernière se répète. */
const BACKOFF_SECONDS = [1, 2, 4, 8, 16, 30] as const
/** TRN-13 : refus de suite (4429) avant d'abandonner. */
const MAX_REFUSALS = 3
/** ADR 0044 (décision 2) : échecs de suite, sans réponse du serveur, avant de signaler la connexion injoignable. */
const MAX_SILENT_FAILURES = 3

const NORMAL_CLOSURE = 1000
/** Fermeture par le client d'une connexion qui ne répond plus au `ping` (codes 4000 à 4999 : application). */
const PONG_TIMEOUT = 4000
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

/** Événement du serveur remis à l'appelant ; `session.revoked` met fin à la connexion. */
export type RealtimeEvent = Exclude<ServerEvent, { readonly type: 'session.revoked' }>

export interface RealtimeOptions {
  readonly url: string
  /** Un événement validé du serveur. */
  readonly onFrame: (event: RealtimeEvent) => void
  /**
   * TRN-15 : chaque (re)connexion confirmée par le serveur (première trame valide reçue, le `pong` du ping
   * d'ouverture d'ordinaire), pour relire ce qui a pu être manqué.
   */
  readonly onOpen: () => void
  readonly onEnd: (reason: RealtimeEnd) => void
  /**
   * ADR 0044 (décision 2) : décide quand rouvrir après l'attente croissante ; `resume` rouvre. Par défaut, aussitôt.
   * Une poignée de main prolonge la session : la coque n'y consent qu'après une action de l'utilisateur.
   */
  readonly beforeReconnect?: (resume: () => void) => void
  /** ADR 0044 (décision 2) : trois échecs de suite sans réponse du serveur ; la session a peut-être fini. */
  readonly onUnreachable?: () => void
  readonly createSocket?: SocketFactory
}

export interface RealtimeConnection {
  /** Envoie une trame et rend la charge de son accusé, ou échoue sur son `error`, une coupure ou 10 s de silence. */
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

/** TRN-13, TRN-15 : connexion à /ws, entretenue par ping et rétablie après une coupure. */
export function createRealtimeConnection(options: RealtimeOptions): RealtimeConnection {
  const createSocket = options.createSocket ?? browserSocket
  const beforeReconnect =
    options.beforeReconnect ??
    ((resume: () => void) => {
      resume()
    })
  const pending = new Map<string, Pending>()
  let socket: SocketLike | null = null
  let confirmed = false
  let ping: ReturnType<typeof setInterval> | null = null
  let reconnect: ReturnType<typeof setTimeout> | null = null
  let attempt = 0
  let refusals = 0
  let silentFailures = 0
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
    if (!confirmed) {
      // TRN-15 : la connexion répond ; la prochaine coupure repart d'une seconde, et le rattrapage peut partir.
      confirmed = true
      attempt = 0
      refusals = 0
      silentFailures = 0
      options.onOpen()
    }
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
    const wasConfirmed = confirmed
    confirmed = false
    if (ended) {
      return
    }
    if (code === SESSION_ENDED) {
      end('session')
      return
    }
    if (code === TOO_MANY_CONNECTIONS) {
      if (++refusals >= MAX_REFUSALS) {
        end('refused')
        return
      }
    } else if (!wasConfirmed && ++silentFailures === MAX_SILENT_FAILURES) {
      options.onUnreachable?.()
    }
    const seconds = BACKOFF_SECONDS[Math.min(attempt, BACKOFF_SECONDS.length - 1)] ?? 30
    attempt++
    reconnect = setTimeout(() => {
      reconnect = null
      if (!ended) {
        beforeReconnect(() => {
          if (!ended && socket === null && reconnect === null) {
            open()
          }
        })
      }
    }, seconds * 1000)
  }

  /** ADR 0044 (décision 7) : une connexion qui ne répond plus au ping est abandonnée, puis rouverte. */
  const sendPing = () => {
    const id = frameId()
    const timer = setTimeout(() => {
      pending.delete(id)
      const current = socket
      if (current !== null) {
        closed(PONG_TIMEOUT)
        current.close(PONG_TIMEOUT)
      }
    }, REPLY_TIMEOUT_MS)
    pending.set(id, {
      resolve: () => undefined,
      reject: () => undefined,
      timer,
    })
    write('ping', id, {})
  }

  function open() {
    if (ended) {
      return
    }
    const current: SocketLike = createSocket(options.url, {
      onOpen: () => {
        if (socket !== current || ended) {
          return
        }
        sendPing()
        ping = setInterval(sendPing, PING_INTERVAL_MS)
      },
      onMessage: (data) => {
        // Échec fermé : rien n'est remis après la fin (session.revoked) ni d'une socket remplacée.
        if (socket === current && !ended) {
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
