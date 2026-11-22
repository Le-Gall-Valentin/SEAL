import { reloadCsrf, requestNoContent } from '@/shared/api'
import type { IReauthApi } from './IReauthApi'

/** Adaptateur HTTP du port IReauthApi. */
export const reauthApi: IReauthApi = {
  async reauthenticate(password) {
    await requestNoContent('/api/v1/auth/reauth', { method: 'POST', body: { password } })
    // AUTH-60, ADR 0034 : la session change d'identifiant et de jeton CSRF ; l'action relancée ensuite est une
    // requête non sûre. Un échec ne défait pas la ré-authentification : shared/api rechargera le jeton absent.
    await reloadCsrf().catch(() => undefined)
  },
}
