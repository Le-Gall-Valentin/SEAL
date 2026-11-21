import { z } from 'zod'
import {
  DISPLAY_NAME_SCHEMA,
  isDisplayNameShownInvalid,
  USERNAME_SCHEMA,
  type UsernameStatus,
} from '@/entities/account'

/** SEC-01 : les deux champs, validés par Zod avec les règles du serveur. */
const FIELDS = z.object({ username: USERNAME_SCHEMA, displayName: DISPLAY_NAME_SCHEMA })

/** Erreur du nom affiché et envoi permis (ADR 0024, point 9 ; SEC-01). */
export function oauthRegistrationView({
  username,
  status,
  displayName,
  displayNameTouched,
  refusedDisplayName,
}: {
  readonly username: string
  readonly status: UsernameStatus
  readonly displayName: string
  readonly displayNameTouched: boolean
  readonly refusedDisplayName: string | undefined
}) {
  const refused = refusedDisplayName === displayName
  const displayNameInvalid = isDisplayNameShownInvalid({
    value: displayName,
    touched: displayNameTouched,
    refused: refusedDisplayName,
  })
  const valid =
    FIELDS.safeParse({ username, displayName }).success &&
    !refused &&
    // AUTH-05 : une disponibilité inconnue (réseau, 429) laisse le serveur trancher à l'envoi.
    (status.kind === 'available' || status.kind === 'unknown')
  return { displayNameInvalid, valid }
}
