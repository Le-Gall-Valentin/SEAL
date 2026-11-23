import { useCallback, useEffect, useRef, useState } from 'react'

interface HeldLock {
  /** Vrai une fois le verrou obtenu, jusqu'à sa libération. */
  readonly held: boolean
  /** Vrai tant qu'un autre écran tient le verrou et que celui-ci l'attend (ADR 0029, point 13). */
  readonly waiting: boolean
  /** Libère le verrou avant le démontage, pour qu'une tâche qui l'attend s'exécute aussitôt. */
  readonly release: () => void
}

/**
 * ADR 0029, point 3 : tient le verrou Web Locks `name` tant que l'écran est monté. Un autre onglet qui le demande attend
 * sa libération : à la sortie de l'écran, ou par `release`. Sûr sous StrictMode : un montage annulé abandonne sa demande.
 * Le verrou est d'abord demandé sans attendre : `waiting` ne devient vrai que s'il est vraiment tenu ailleurs, pour
 * qu'un écran d'attente ne clignote pas quand il est libre (comme `withLock`).
 */
export function useHeldLock(name: string): HeldLock {
  const [held, setHeld] = useState(false)
  const [waiting, setWaiting] = useState(false)
  const releaseRef = useRef<() => void>(() => undefined)

  useEffect(() => {
    const controller = new AbortController()
    let free: () => void = () => undefined
    const released = new Promise<void>((resolve) => {
      free = resolve
    })
    releaseRef.current = () => {
      free()
      setHeld(false)
    }
    const grant = (): Promise<void> => {
      setWaiting(false)
      setHeld(true)
      return released
    }
    navigator.locks
      // Une demande sans attente ne prend pas de signal : un montage annulé entre-temps rend le verrou aussitôt.
      .request(name, { ifAvailable: true }, (lock) => {
        if (lock === null) {
          return 'busy'
        }
        return controller.signal.aborted ? 'done' : grant().then(() => 'done')
      })
      .then((outcome) => {
        if (outcome !== 'busy' || controller.signal.aborted) {
          return undefined
        }
        setWaiting(true)
        return navigator.locks.request(name, { signal: controller.signal }, grant)
      })
      // Une demande abandonnée au démontage rejette AbortError : rien à faire.
      .catch(() => undefined)
    return () => {
      controller.abort()
      free()
    }
  }, [name])

  const release = useCallback(() => {
    releaseRef.current()
  }, [])
  return { held, waiting, release }
}
