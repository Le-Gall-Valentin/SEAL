// API publique de la fonctionnalité oauth-registration (ADR 0012, ADR 0033) : fin de l'inscription OAuth.
export type { IOAuthRegistrationApi } from './api/IOAuthRegistrationApi'
export { oauthRegistrationApi } from './api/oauthRegistrationApi'
export type { PendingRegistration } from './model/oauthRegistration'
export { OAuthRegistrationApiProvider } from './model/oauthRegistrationApiPort'
export { usePendingRegistration } from './model/usePendingRegistration'
export { OAuthRegistrationForm } from './ui/OAuthRegistrationForm'
