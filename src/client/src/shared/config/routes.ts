/** ADR 0024, point 5 : adresses des écrans ; le serveur renvoie le client pour chacune. */
export const ROUTES = {
  HOME: '/',
  LOGIN: '/connexion',
  REGISTER: '/inscription',
  VERIFY_EMAIL: '/verification-courriel',
  FORGOT_PASSWORD: '/mot-de-passe/oublie',
  // ADR 0022 : adresse du lien envoyé par courriel.
  RESET_PASSWORD: '/mot-de-passe/nouveau',
  // AUTH-32, ADR 0032 (point 4) : Identifiant-OAuth, où le serveur renvoie après un premier passage.
  CHOOSE_USERNAME: '/inscription/identifiant',
  // ACC-01 à ACC-03, ADR 0034 et 0035 : Paramètres, où revient aussi une ré-authentification OAuth.
  SETTINGS: '/parametres',
  // ADR 0038 (point 5) : écran Amis ; l'onglet Invitations est un paramètre de requête.
  FRIENDS: '/amis',
} as const

/**
 * ADR 0035 : sous-page des paramètres ouverte sur téléphone (`/parametres?section=compte`), que le serveur ignore ; la
 * navigation principale s'y retire, comme dans Parametres-Telephone-Preferences. Seule « Compte » existe en P0.
 */
export const SETTINGS_SECTION_PARAM = 'section'

export type SettingsSection = 'compte'

/** La sous-page de l'adresse, une règle pour la coque et pour la page : toute autre valeur, ou aucune, n'en est pas. */
export function settingsSectionOf(params: URLSearchParams): SettingsSection | null {
  return params.get(SETTINGS_SECTION_PARAM) === 'compte' ? 'compte' : null
}

/** L'adresse de la sous-page Compte. */
export const SETTINGS_ACCOUNT = `/parametres?${SETTINGS_SECTION_PARAM}=compte`

/** ADR 0038 (point 5) : onglet de l'écran Amis, `?onglet=invitations` ; absent, l'onglet Amis. */
const FRIENDS_TAB_PARAM = 'onglet'

export type FriendsTab = 'amis' | 'invitations'

export function friendsTabOf(params: URLSearchParams): FriendsTab {
  return params.get(FRIENDS_TAB_PARAM) === 'invitations' ? 'invitations' : 'amis'
}

export const FRIENDS_INVITATIONS = `${ROUTES.FRIENDS}?${FRIENDS_TAB_PARAM}=invitations`
