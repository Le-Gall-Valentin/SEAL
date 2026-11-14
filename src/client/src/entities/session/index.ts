// API publique de la tranche session (ADR 0012).
export type { Account, ISessionApi } from './api/ISessionApi'
export { sessionApi } from './api/sessionApi'
export { SessionApiProvider } from './model/sessionApiPort'
export { destinationFor, SESSION_STATES, sessionStateOf } from './model/sessionState'
export type { SessionState } from './model/sessionState'
export {
  endSession,
  refreshSession,
  SESSION_QUERY_KEY,
  useCurrentAccount,
} from './model/useCurrentAccount'
