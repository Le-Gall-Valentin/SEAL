import { useEffect, useState } from 'react'
import { secondsUntil } from './code'

/**
 * AUTH-14 : secondes avant l'expiration du code, relues chaque seconde ; null sans code vivant. Après un renvoi, la
 * nouvelle expiration se compte depuis la dernière seconde relue, rattrapée au battement suivant.
 */
export function useSecondsLeft(expiresAt: Date | null): number | null {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    if (expiresAt === null) {
      return undefined
    }
    const timer = setInterval(() => {
      setNow(Date.now())
    }, 1000)
    return () => {
      clearInterval(timer)
    }
  }, [expiresAt])
  return secondsUntil(expiresAt, now)
}
