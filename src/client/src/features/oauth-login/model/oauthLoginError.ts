/** ADR 0032 (point 2) : les seuls codes que le serveur ajoute à /connexion après un départ ou un retour refusé. */
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
  const values = new URLSearchParams(search).getAll('erreur')
  const [value] = values
  return values.length === 1 && value !== undefined && isOAuthLoginError(value) ? value : null
}
