import { z } from 'zod'
import {
  DISPLAY_NAME_SCHEMA,
  EMAIL_SCHEMA,
  isDisplayNameShownInvalid,
  isUsernameSubmittable,
  isEmailWellFormed,
  USERNAME_SCHEMA,
  type UsernameStatus,
} from '@/entities/account'
import { canSubmitPassword, confirmMismatch, type PasswordRules } from '@/entities/password'

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

/** Valeurs refusées par le serveur : l'erreur reste tant que le champ garde cette valeur. */
export interface RegisterRefusals {
  readonly email?: string
  readonly displayName?: string
  readonly password?: string
}

/** SEC-01 : les champs du compte, validés par Zod avec les règles du serveur. */
const REGISTRATION_FIELDS = z.object({
  email: EMAIL_SCHEMA,
  username: USERNAME_SCHEMA,
  displayName: DISPLAY_NAME_SCHEMA,
})

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
  const displayNameInvalid = isDisplayNameShownInvalid({
    value: values.displayName,
    touched: touched.displayName,
    refused: refused.displayName,
  })
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
    isUsernameSubmittable(status) &&
    canSubmitPassword(rules) &&
    values.confirm === values.password
  return { emailInvalid, displayNameInvalid, confirmMismatch: mismatch, valid }
}
