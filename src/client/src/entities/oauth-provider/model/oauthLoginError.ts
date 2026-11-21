import { ROUTES } from '@/shared/config'

/**
 * ADR 0032 (point 2), ADR 0033 : les seuls codes de /connexion?erreur=, qu'ajoutent le serveur après un départ ou un
 * retour refusé, et le client quand une inscription OAuth ne peut plus aboutir.
 */
/** Nom du paramètre de /connexion qui porte le code. */
export const LOGIN_ERROR_PARAM = 'erreur'

const OAUTH_LOGIN_ERRORS = ['OAUTH_EMAIL_IN_USE', 'RATE_LIMITED', 'REQUEST_REJECTED'] as const

export type OAuthLoginError = (typeof OAUTH_LOGIN_ERRORS)[number]

function isOAuthLoginError(value: string): value is OAuthLoginError {
  return (OAUTH_LOGIN_ERRORS as readonly string[]).includes(value)
}

/**
 * ADR 0033 (point 1) : le code d'erreur de l'adresse de Connexion, s'il est l'un des trois et seul. Tout autre texte,
 * qu'un lien peut porter, est ignoré.
 */
export function oauthLoginErrorOf(search: string): OAuthLoginError | null {
  const values = new URLSearchParams(search).getAll(LOGIN_ERROR_PARAM)
  const [value] = values
  return values.length === 1 && value !== undefined && isOAuthLoginError(value) ? value : null
}

/** L'adresse de Connexion qui affichera l'alerte de ce code (ADR 0033, point 2). */
export function loginPathWithError(error: OAuthLoginError): string {
  return `${ROUTES.LOGIN}?${LOGIN_ERROR_PARAM}=${error}`
}
