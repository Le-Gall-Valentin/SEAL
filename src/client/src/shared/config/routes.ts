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
} as const

/**
 * ADR 0035 : sous-page des paramètres ouverte sur téléphone (`/parametres?section=compte`), que le serveur ignore ; la
 * navigation principale s'y retire, comme dans Parametres-Telephone-Preferences.
 */
export const SETTINGS_SECTION_PARAM = 'section'
