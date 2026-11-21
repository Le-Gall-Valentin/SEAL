import { useRef } from 'react'

/** Verrou d'un formulaire : un seul envoi à la fois. */
export interface SubmitGuard {
  /**
   * Lance `send` si aucun envoi n'est en cours, et rend vrai ; sinon ne fait rien et rend faux. Le verrou est rendu
   * quand la promesse de `send` se règle, réussite comme échec : aucun formulaire ne peut oublier de le rendre.
   */
  run(send: () => Promise<unknown>): boolean
}

/**
 * ADR 0033, point 6 : un seul envoi à la fois. L'état « en cours » d'une mutation n'est relu qu'au rendu suivant : deux
 * clics ou deux « Entrée » rapprochés partiraient sinon tous deux. Le verrou est synchrone, posé avant l'envoi.
 *
 * `send` rend la promesse de l'envoi (`mutateAsync`) : son échec est déjà traité par la mutation (`onError`, état
 * d'erreur), le verrou n'en retient que la fin.
 */
export function useSubmitGuard(): SubmitGuard {
  const busy = useRef(false)
  return {
    run(send) {
      if (busy.current) {
        return false
      }
      busy.current = true
      const release = () => {
        busy.current = false
      }
      let sending: Promise<unknown>
      try {
        sending = send()
      } catch (error) {
        release()
        throw error
      }
      sending.then(release, release)
      return true
    },
  }
}
