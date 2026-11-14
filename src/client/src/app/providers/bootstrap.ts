import type { Apis } from './ApiProviders'

/**
 * Démarrage du client. AUTH-60 : le jeton CSRF est rechargé par le port de la session ; un échec est sans suite, la
 * première requête non sûre le rechargera.
 */
export function bootstrap(apis: Apis): void {
  apis.session.reloadCsrf().catch(() => undefined)
}
