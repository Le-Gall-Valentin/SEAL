import { z } from 'zod'
import { isDisplayNameValid } from './displayName'
import { isEmailWellFormed } from './email'
import { isReservedUsername, isUsernameWellFormed } from './username'

/** SEC-01 : les champs du compte, validés côté client par Zod avec les règles du serveur. */
export const EMAIL_SCHEMA = z.string().refine(isEmailWellFormed)

/** AUTH-01, AUTH-02 : bien formé et non réservé. */
export const USERNAME_SCHEMA = z
  .string()
  .refine(isUsernameWellFormed)
  .refine((username) => !isReservedUsername(username))

/** AUTH-10. */
export const DISPLAY_NAME_SCHEMA = z.string().refine(isDisplayNameValid)
