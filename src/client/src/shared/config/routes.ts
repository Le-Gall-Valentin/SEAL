/** ADR 0024, point 5 : adresses des écrans ; le serveur renvoie le client pour chacune. */
export const ROUTES = {
  HOME: '/',
  LOGIN: '/connexion',
  REGISTER: '/inscription',
  VERIFY_EMAIL: '/verification-courriel',
  FORGOT_PASSWORD: '/mot-de-passe/oublie',
  // ADR 0022 : adresse du lien envoyé par courriel.
  RESET_PASSWORD: '/mot-de-passe/nouveau',
} as const
