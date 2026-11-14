// API publique du segment lib de shared (ADR 0012).
export {
  ApiError,
  ERROR_CODES,
  isUnauthenticated,
  NetworkError,
  retryAfterMinutes,
  toErrorCode,
} from './apiErrors'
export type { ErrorCode } from './apiErrors'
export { failureOf } from './failure'
export type { Failure } from './failure'
export { useDebouncedValue } from './useDebouncedValue'
export { useRetryAfterLock } from './useRetryAfterLock'
export { createPort } from './createPort'
