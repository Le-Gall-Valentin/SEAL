// API publique du segment ws de shared (ADR 0012) : seul un segment api/ de tranche l'atteint.
export { createRealtimeConnection } from './connection'
export type {
  RealtimeEnd,
  RealtimeEvent,
  SocketFactory,
  SocketHandlers,
  SocketLike,
} from './connection'
