// API publique de la tranche session (ADR 0012).
export type { Account, ISessionApi } from './api/ISessionApi'
export { ACCOUNT_RESPONSE, sessionApi } from './api/sessionApi'
export type { IRealtimeApi, RealtimeEvent, RealtimeListener } from './api/IRealtimeApi'
export { realtimeApi } from './api/realtimeApi'
export { RealtimeApiProvider } from './model/realtimePort'
export { useRealtime } from './model/useRealtime'
export { SessionApiProvider } from './model/sessionApiPort'
export { destinationFor, SESSION_STATES, sessionStateOf } from './model/sessionState'
export type { SessionState } from './model/sessionState'
export {
  endSession,
  refreshSession,
  replaceSessionAccount,
  SESSION_QUERY_KEY,
  useCurrentAccount,
} from './model/useCurrentAccount'
