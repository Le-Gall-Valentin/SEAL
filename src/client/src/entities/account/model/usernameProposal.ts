import { localPartOf } from './email'
import { usernameSuggestionFrom } from './username'
import type { UsernameStatus } from './useUsernameAvailability'

/** Refus de l'identifiant par le serveur, à l'envoi (AUTH-05). */
export interface UsernameRefusal {
  readonly value: string
  readonly reason: 'taken' | 'invalid' | 'reserved'
}

/** AUTH-04, étapes 1 à 4 : identifiant tiré du courriel, dès qu'il a une partie locale. */
export function usernameFromEmail(email: string): string {
  return localPartOf(email) === '' ? '' : usernameSuggestionFrom(email)
}

/**
 * AUTH-04, étape 5 : la base tirée du courriel est prise ou réservée, la première suggestion libre du serveur la
 * remplace (Inscription-Telephone : `valentin.dupont2`, « Disponible »). Sans suggestion, la base reste avec son état.
 */
export function proposeUsername(
  base: string,
  status: UsernameStatus,
): { readonly username: string; readonly status: UsernameStatus } {
  if (status.kind === 'taken' || status.kind === 'reserved') {
    const [first] = status.suggestions
    if (first !== undefined) {
      return { username: first, status: { kind: 'available' } }
    }
  }
  return { username: base, status }
}

/** AUTH-05 : un refus du serveur prime tant que la disponibilité ne l'a pas confirmé. */
export function statusAfterRefusal(
  status: UsernameStatus,
  refusal: UsernameRefusal | undefined,
  username: string,
): UsernameStatus {
  if (refusal === undefined || refusal.value !== username || status.kind === 'taken') {
    return status
  }
  switch (refusal.reason) {
    case 'taken':
      return { kind: 'taken', suggestions: [] }
    case 'reserved':
      return { kind: 'reserved', suggestions: [] }
    case 'invalid':
      return { kind: 'invalid' }
  }
}
