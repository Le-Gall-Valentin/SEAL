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
export { failureOf, isFormFailure } from './failure'
export type { Failure, FormFailure } from './failure'
export { joinWithOr } from './joinWithOr'
export { isolate } from './isolate'
export { useDebouncedValue } from './useDebouncedValue'
export { useActivityGatedInterval } from './useActivityGatedInterval'
export { useRetryAfterLock } from './useRetryAfterLock'
export { useSubmitGuard } from './useSubmitGuard'
export type { SubmitGuard } from './useSubmitGuard'
export { createPort } from './createPort'
export { useMounted } from './useMounted'
export { ClockProvider, useClock } from './clock'
export { useNow } from './useNow'
export type { Clock } from './clock'
export { withLock } from './withLock'
export { downloadText } from './downloadText'
export { useHeldLock } from './useHeldLock'
export { CODE_LENGTH, EMPTY_CODE, enterDigits, isCodeComplete } from './codeDigits'
export { LARGE_SCREEN, useMediaQuery, WIDE_SCREEN } from './useMediaQuery'
