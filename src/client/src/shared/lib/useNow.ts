import { useEffect, useState } from 'react'
import { useClock } from './clock'

/**
 * Instant présent de l'horloge injectée, relu toutes les `periodMs` millisecondes : un texte relatif (« il y a
 * 2 minutes ») avance seul, sans dépendre d'une nouvelle lecture des données.
 */
export function useNow(periodMs: number): number {
  const clock = useClock()
  const [now, setNow] = useState(() => clock.now())
  useEffect(() => {
    const timer = window.setInterval(() => {
      setNow(clock.now())
    }, periodMs)
    return () => {
      window.clearInterval(timer)
    }
  }, [clock, periodMs])
  return now
}
