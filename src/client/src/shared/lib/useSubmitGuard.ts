import { useRef } from 'react'

/** Verrou d'un envoi : `enter` le prend s'il est libre, `release` le rend à la fin de l'envoi. */
export interface SubmitGuard {
  enter(): boolean
  release(): void
}

/**
 * ADR 0033, point 6 : un seul envoi à la fois. L'état « en cours » d'une mutation n'est relu qu'au rendu suivant : deux
 * clics ou deux « Entrée » rapprochés partiraient sinon tous deux. Le verrou est synchrone, posé avant l'envoi.
 */
export function useSubmitGuard(): SubmitGuard {
  const busy = useRef(false)
  return {
    enter() {
      if (busy.current) {
        return false
      }
      busy.current = true
      return true
    },
    release() {
      busy.current = false
    },
  }
}
