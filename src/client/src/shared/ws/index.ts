// API publique du segment ws de shared (ADR 0012) : seul un segment api/ de tranche l'atteint.
export { createRealtimeConnection, PING_INTERVAL_MS, REPLY_TIMEOUT_MS } from './connection'
export type {
  RealtimeConnection,
  RealtimeEnd,
  RealtimeOptions,
  SocketFactory,
  SocketHandlers,
  SocketLike,
} from './connection'
export { parseServerFrame } from './frames'
export type { JsonValue, ServerEvent } from './frames'
