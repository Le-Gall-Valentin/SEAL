import { useCallback, useSyncExternalStore } from 'react'

/**
 * Vrai tant que la requête média est satisfaite, suivi en direct. Sert aux écrans dont la structure change avec la
 * largeur (Parametres-* : sous-page sur téléphone), pour ne monter qu'une seule fois un formulaire.
 */
export function useMediaQuery(query: string): boolean {
  // Même fonction d'un rendu à l'autre : React ne se réabonne qu'au changement de requête.
  const subscribe = useCallback(
    (onChange: () => void) => {
      const list = window.matchMedia(query)
      list.addEventListener('change', onChange)
      return () => {
        list.removeEventListener('change', onChange)
      }
    },
    [query],
  )
  return useSyncExternalStore(subscribe, () => window.matchMedia(query).matches)
}

/** Largeur à partir de laquelle la tablette et l'ordinateur remplacent le téléphone (sm de Tailwind). */
export const WIDE_SCREEN = '(min-width: 640px)'
