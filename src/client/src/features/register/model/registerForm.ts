import { z } from 'zod'
import {
  DISPLAY_NAME_SCHEMA,
  EMAIL_SCHEMA,
  isDisplayNameValid,
  isEmailWellFormed,
  localPartOf,
  USERNAME_SCHEMA,
  usernameSuggestionFrom,
} from '@/entities/account'
import { canSubmitPassword, confirmMismatch, type PasswordRules } from '@/entities/password'
import type { UsernameStatus } from './useUsernameAvailability'

/** Champs saisis du formulaire d'Inscription-Telephone, l'identifiant à part. */
export interface RegisterValues {
  readonly email: string
  readonly displayName: string
  readonly password: string
  readonly confirm: string
}

/** Champs quittés au moins une fois : leur erreur s'affiche ensuite, jamais pendant la frappe. */
export interface RegisterTouched {
  readonly email: boolean
  readonly displayName: boolean
  readonly confirm: boolean
}

/** Refus de l'identifiant par le serveur, à l'envoi (AUTH-05). */
export interface UsernameRefusal {
  readonly value: string
  readonly reason: 'taken' | 'invalid' | 'reserved'
}

/** Valeurs refusées par le serveur : l'erreur reste tant que le champ garde cette valeur. */
export interface RegisterRefusals {
  readonly email?: string
  readonly displayName?: string
  readonly password?: string
  readonly username?: UsernameRefusal
}

/** SEC-01 : les champs du compte, validés par Zod avec les règles du serveur. */
const REGISTRATION_FIELDS = z.object({
  email: EMAIL_SCHEMA,
  username: USERNAME_SCHEMA,
  displayName: DISPLAY_NAME_SCHEMA,
})

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

/** Erreurs affichées sous les champs, et envoi permis (ADR 0024, point 9 ; SEC-01). */
export function registerView({
  values,
  username,
  status,
  rules,
  touched,
  refused,
}: {
  readonly values: RegisterValues
  readonly username: string
  readonly status: UsernameStatus
  readonly rules: PasswordRules
  readonly touched: RegisterTouched
  readonly refused: RegisterRefusals
}) {
  const emailInvalid =
    (touched.email && values.email !== '' && !isEmailWellFormed(values.email)) ||
    refused.email === values.email
  const displayNameInvalid =
    (touched.displayName && values.displayName !== '' && !isDisplayNameValid(values.displayName)) ||
    refused.displayName === values.displayName
  const mismatch = confirmMismatch({
    password: values.password,
    confirm: values.confirm,
    confirmTouched: touched.confirm,
  })
  const valid =
    REGISTRATION_FIELDS.safeParse({
      email: values.email,
      username,
      displayName: values.displayName,
    }).success &&
    refused.email !== values.email &&
    refused.displayName !== values.displayName &&
    // AUTH-05 : une disponibilité inconnue (réseau, 429) laisse le serveur trancher à l'envoi.
    (status.kind === 'available' || status.kind === 'unknown') &&
    canSubmitPassword(rules) &&
    values.confirm === values.password
  return { emailInvalid, displayNameInvalid, confirmMismatch: mismatch, valid }
}
