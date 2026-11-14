import { useEffect, useState } from 'react'

/**
 * Faux au premier rendu, vrai juste après. Une région role="status" vide au montage, puis remplie, voit son contenu
 * annoncé par les lecteurs d'écran ; une région déjà pleine au montage ne l'est pas.
 */
export function useMounted(): boolean {
  const [mounted, setMounted] = useState(false)
  useEffect(() => {
    const timer = setTimeout(() => {
      setMounted(true)
    }, 0)
    return () => {
      clearTimeout(timer)
    }
  }, [])
  return mounted
}
