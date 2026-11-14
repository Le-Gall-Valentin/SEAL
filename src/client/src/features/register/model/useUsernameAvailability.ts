import { useQuery } from '@tanstack/react-query'
import { isReservedUsername, isUsernameWellFormed } from '@/entities/account'
import { useDebouncedValue } from '@/shared/lib'
import { useRegisterApi } from './registerApiContext'

/** État de l'identifiant affiché sous son champ (Inscription-Telephone, Identifiant-OAuth, ADR 0024). */
export type UsernameStatus =
  | { readonly kind: 'empty' }
  | { readonly kind: 'invalid' }
  | { readonly kind: 'reserved' }
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

/** AUTH-01, AUTH-02 : ce que le client sait sans le serveur. */
function localStatus(username: string): UsernameStatus | null {
  if (username === '') {
    return { kind: 'empty' }
  }
  if (!isUsernameWellFormed(username)) {
    return { kind: 'invalid' }
  }
  return isReservedUsername(username) ? { kind: 'reserved' } : null
}

/** AUTH-05 : disponibilité en direct de l'identifiant, après un délai. */
export function useUsernameAvailability(username: string): UsernameStatus {
  const api = useRegisterApi()
  const debounced = useDebouncedValue(username, AVAILABILITY_DELAY_MS)
  const local = localStatus(debounced)
  const query = useQuery({
    queryKey: usernameAvailabilityKey(debounced),
    queryFn: ({ signal }) => api.usernameAvailability(debounced, signal),
    enabled: local === null,
    staleTime: 30_000,
  })
  if (username !== debounced) {
    return { kind: 'checking' }
  }
  if (local !== null) {
    return local
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
