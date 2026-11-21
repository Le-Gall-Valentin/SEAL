import type { ErrorCode } from '@/shared/lib'
import { localPartOf } from './email'
import { usernameSuggestionFrom } from './username'
import type { UsernameStatus } from './useUsernameAvailability'

/** Refus de l'identifiant par le serveur, à l'envoi (AUTH-05). */
export interface UsernameRefusal {
  readonly value: string
  readonly reason: 'taken' | 'invalid' | 'reserved'
}

/** AUTH-01, AUTH-02, AUTH-05 : refus de l'identifiant par le serveur (CDC, section 14.6). */
const REFUSAL_REASONS: ReadonlyMap<ErrorCode, UsernameRefusal['reason']> = new Map([
  ['USERNAME_TAKEN', 'taken'],
  ['USERNAME_INVALID', 'invalid'],
  ['USERNAME_RESERVED', 'reserved'],
])

/** Motif d'un refus de l'identifiant, ou null pour tout autre code. */
export function usernameRefusalReason(code: ErrorCode): UsernameRefusal['reason'] | null {
  return REFUSAL_REASONS.get(code) ?? null
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

/** AUTH-05 : l'identifiant peut partir ; une disponibilité inconnue (réseau, 429) laisse le serveur trancher à l'envoi. */
export function isUsernameSubmittable(status: UsernameStatus): boolean {
  return status.kind === 'available' || status.kind === 'unknown'
}
