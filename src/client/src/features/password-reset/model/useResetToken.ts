import { useEffect, useState } from 'react'
import { readResetToken } from './resetLink'

/**
 * ADR 0022, point 2 : jeton lu une fois dans le fragment du lien, puis retiré de l'adresse (history.replaceState) ;
 * il ne vit qu'en mémoire, le temps de l'écran, jamais dans un stockage ni dans l'historique.
 */
export function useResetToken(): string | null {
  const [token] = useState(() => readResetToken(globalThis.location.hash))
  useEffect(() => {
    if (globalThis.location.hash !== '') {
      const { pathname, search } = globalThis.location
      globalThis.history.replaceState(globalThis.history.state, '', `${pathname}${search}`)
    }
  }, [])
  return token
}
