// API croisée de oauth-provider pour l'entité account (ADR 0012) : les fournisseurs que le client sait afficher, dont
// la lecture des méthodes de connexion vérifie la réponse (ADR 0035, point 2).
export { LINKABLE_OAUTH_PROVIDERS, OAUTH_PROVIDER_IDS } from '../api/IOAuthProvidersApi'
export type { OAuthProviderId } from '../api/IOAuthProvidersApi'
