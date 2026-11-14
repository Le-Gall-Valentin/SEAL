import { useEffect, useEffectEvent } from 'react'
import { ApiError } from './apiErrors'

/** SEC-10 : un 429 sans Retry-After lisible verrouille une minute. */
const DEFAULT_LOCK_SECONDS = 60

/** Plus long délai de setTimeout (32 bits signés) : au-delà, le minuteur partirait aussitôt. */
const MAX_TIMER_DELAY_MS = 2_147_483_647

function lockSeconds(error: unknown): number | null {
  return error instanceof ApiError && error.code === 'RATE_LIMITED'
    ? (error.retryAfterSeconds ?? DEFAULT_LOCK_SECONDS)
    : null
}

/**
 * SEC-10 : vrai tant qu'un 429 court ; à la fin de Retry-After, release est appelé (en général, la remise à zéro de la
 * mutation, qui retire l'alerte et libère le formulaire). Le minuteur ne dépend que de l'erreur : un écran qui refait
 * son rendu, ou passe une nouvelle fonction release, ne le relance pas.
 */
export function useRetryAfterLock(error: unknown, release: () => void): boolean {
  const lock = lockSeconds(error)
  const onRelease = useEffectEvent(release)
  useEffect(() => {
    if (lock === null) {
      return undefined
    }
    const timer = setTimeout(
      () => {
        onRelease()
      },
      Math.min(lock * 1000, MAX_TIMER_DELAY_MS),
    )
    return () => {
      clearTimeout(timer)
    }
  }, [lock])
  return lock !== null
}
