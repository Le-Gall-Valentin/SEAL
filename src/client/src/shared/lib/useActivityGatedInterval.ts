import { useEffect, useEffectEvent } from 'react'

/**
 * Gestes qui comptent comme une action de l'utilisateur, écoutés en capture. Le défilement se compte par ses gestes
 * (molette, toucher, clavier, pointeur) et non par `scroll`, que la page émet aussi seule : un élément ajouté en tête
 * d'une liste défilée fait recaler le défilement sans personne devant l'écran (AUTH-52, audit F1).
 */
const ACTIVITY_EVENTS = ['pointerdown', 'pointermove', 'keydown', 'wheel', 'touchstart'] as const

/**
 * AUTH-52, ADR 0037 (point 11) : appelle `onTick` toutes les `periodMs` millisecondes, seulement si l'utilisateur a agi
 * (pointeur, clavier, molette ou toucher) depuis l'échéance précédente et si la page est visible. Une requête ainsi
 * déclenchée ne prolonge la session que d'une échéance au plus après la dernière action.
 */
export function useActivityGatedInterval(onTick: () => void, periodMs: number): void {
  const tick = useEffectEvent(onTick)
  useEffect(() => {
    let active = false
    const mark = () => {
      active = true
    }
    const options = { capture: true, passive: true }
    for (const type of ACTIVITY_EVENTS) {
      window.addEventListener(type, mark, options)
    }
    const timer = window.setInterval(() => {
      if (active && document.visibilityState !== 'hidden') {
        active = false
        tick()
      }
    }, periodMs)
    return () => {
      window.clearInterval(timer)
      for (const type of ACTIVITY_EVENTS) {
        window.removeEventListener(type, mark, options)
      }
    }
  }, [periodMs])
}
