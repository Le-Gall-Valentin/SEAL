import { useCallback, useEffect, useRef, useState } from 'react'

interface HeldLock {
  /** Vrai une fois le verrou obtenu, jusqu'à sa libération. */
  readonly held: boolean
  /** Libère le verrou avant le démontage, pour qu'une tâche qui l'attend s'exécute aussitôt. */
  readonly release: () => void
}

/**
 * ADR 0029, point 3 : tient le verrou Web Locks `name` tant que l'écran est monté. Un autre onglet qui le demande attend
 * sa libération : à la sortie de l'écran, ou par `release`. Sûr sous StrictMode : un montage annulé abandonne sa demande.
 */
export function useHeldLock(name: string): HeldLock {
  const [held, setHeld] = useState(false)
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
    navigator.locks
      .request(name, { signal: controller.signal }, () => {
        setHeld(true)
        return released
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
  return { held, release }
}
