import { useEffect, useState } from 'react'

/** La valeur, une fois la saisie arrêtée depuis delayMs (AUTH-05 : une requête par pause, pas par touche). */
export function useDebouncedValue<T>(value: T, delayMs: number): T {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebounced(value)
    }, delayMs)
    return () => {
      clearTimeout(timer)
    }
  }, [value, delayMs])
  return debounced
}
