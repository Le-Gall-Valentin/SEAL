import { useEffect, useState } from 'react'
import { useClock } from '@/shared/lib'
import { secondsUntil } from './code'

/**
 * AUTH-14 : secondes avant l'expiration du code, relues chaque seconde par l'horloge injectée, jusqu'à zéro ; null
 * sans code vivant.
 */
export function useSecondsLeft(expiresAt: Date | null): number | null {
  const clock = useClock()
  const [now, setNow] = useState(() => clock.now())
  useEffect(() => {
    if (expiresAt === null) {
      return undefined
    }
    const timer = setInterval(() => {
      const current = clock.now()
      setNow(current)
      if (current >= expiresAt.getTime()) {
        clearInterval(timer)
      }
    }, 1000)
    return () => {
      clearInterval(timer)
    }
  }, [expiresAt, clock])
  return secondsUntil(expiresAt, now)
}
