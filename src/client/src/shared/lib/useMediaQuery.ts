import { useSyncExternalStore } from 'react'

/**
 * Vrai tant que la requête média est satisfaite, suivi en direct. Sert aux écrans dont la structure change avec la
 * largeur (Parametres-* : sous-page sur téléphone), pour ne monter qu'une seule fois un formulaire.
 */
export function useMediaQuery(query: string): boolean {
  return useSyncExternalStore(
    (onChange) => {
      const list = window.matchMedia(query)
      list.addEventListener('change', onChange)
      return () => {
        list.removeEventListener('change', onChange)
      }
    },
    () => window.matchMedia(query).matches,
  )
}

/** Largeur à partir de laquelle la tablette et l'ordinateur remplacent le téléphone (sm de Tailwind). */
export const WIDE_SCREEN = '(min-width: 640px)'
