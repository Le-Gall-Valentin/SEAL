import { useEffect } from 'react'
import { ApiError } from './apiErrors'

/** SEC-10 : un 429 sans Retry-After lisible verrouille une minute. */
const DEFAULT_LOCK_SECONDS = 60

function lockSeconds(error: unknown): number | null {
  return error instanceof ApiError && error.code === 'RATE_LIMITED'
    ? (error.retryAfterSeconds ?? DEFAULT_LOCK_SECONDS)
    : null
}

/**
 * SEC-10 : vrai tant qu'un 429 court ; à la fin de Retry-After, release est appelé (en général, la remise à zéro de la
 * mutation, qui retire l'alerte et libère le formulaire).
 */
export function useRetryAfterLock(error: unknown, release: () => void): boolean {
  const lock = lockSeconds(error)
  useEffect(() => {
    if (lock === null) {
      return undefined
    }
    const timer = setTimeout(release, lock * 1000)
    return () => {
      clearTimeout(timer)
    }
  }, [lock, release])
  return lock !== null
}
