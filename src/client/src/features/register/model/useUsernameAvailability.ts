import { useQuery } from '@tanstack/react-query'
import { isReservedUsername, isUsernameWellFormed } from '@/entities/account'
import { useDebouncedValue } from '@/shared/lib'
import { useRegisterApi } from './registerApiPort'

/** État de l'identifiant affiché sous son champ (Inscription-Telephone, Identifiant-OAuth, ADR 0024). */
export type UsernameStatus =
  | { readonly kind: 'empty' }
  | { readonly kind: 'invalid' }
  | { readonly kind: 'reserved'; readonly suggestions: readonly string[] }
  | { readonly kind: 'checking' }
  | { readonly kind: 'available' }
  | { readonly kind: 'taken'; readonly suggestions: readonly string[] }
  /** Disponibilité inconnue (réseau, limite de débit) : le serveur tranchera à l'envoi. */
  | { readonly kind: 'unknown' }

/** AUTH-05 : une requête par pause de la saisie (30 par minute et par IP côté serveur). */
const AVAILABILITY_DELAY_MS = 300

export function usernameAvailabilityKey(username: string) {
  return ['username-availability', username] as const
}

/** Toutes les disponibilités lues : un refus à l'envoi les rend périmées (AUTH-05). */
export const USERNAME_AVAILABILITY_KEY = ['username-availability'] as const

/**
 * AUTH-05 : disponibilité en direct de l'identifiant, après un délai. Un identifiant mal formé (AUTH-01) se signale
 * sans le serveur ; un réservé (AUTH-02) aussi, mais le serveur est interrogé pour ses suggestions libres.
 */
export function useUsernameAvailability(username: string): UsernameStatus {
  const api = useRegisterApi()
  const debounced = useDebouncedValue(username, AVAILABILITY_DELAY_MS)
  const wellFormed = isUsernameWellFormed(debounced)
  const query = useQuery({
    queryKey: usernameAvailabilityKey(debounced),
    queryFn: ({ signal }) => api.usernameAvailability(debounced, signal),
    enabled: wellFormed,
    staleTime: 30_000,
  })
  if (username !== debounced) {
    return { kind: 'checking' }
  }
  if (debounced === '') {
    return { kind: 'empty' }
  }
  if (!wellFormed) {
    return { kind: 'invalid' }
  }
  if (isReservedUsername(debounced)) {
    return { kind: 'reserved', suggestions: query.data?.suggestions ?? [] }
  }
  if (query.isPending) {
    return { kind: 'checking' }
  }
  if (query.isError) {
    return { kind: 'unknown' }
  }
  return query.data.available
    ? { kind: 'available' }
    : { kind: 'taken', suggestions: query.data.suggestions }
}
