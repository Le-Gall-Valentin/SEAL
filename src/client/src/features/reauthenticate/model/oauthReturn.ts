/** Paramètre de /parametres qui porte le refus d'une ré-authentification OAuth (ADR 0034, point 2). */
export const REAUTH_ERROR_PARAM = 'erreur'

/** ADR 0034 : les seuls codes d'un retour refusé vers les paramètres. */
const REAUTH_RETURN_ERRORS = ['REQUEST_REJECTED', 'RATE_LIMITED'] as const

export type ReauthReturnError = (typeof REAUTH_RETURN_ERRORS)[number]

function isReauthReturnError(value: string | undefined): value is ReauthReturnError {
  return value !== undefined && (REAUTH_RETURN_ERRORS as readonly string[]).includes(value)
}

/** Le code de l'adresse, s'il est l'un des deux et seul ; tout autre texte, qu'un lien peut porter, est ignoré. */
export function reauthReturnErrorOf(search: string): ReauthReturnError | null {
  const values = new URLSearchParams(search).getAll(REAUTH_ERROR_PARAM)
  const [value] = values
  return values.length === 1 && isReauthReturnError(value) ? value : null
}
