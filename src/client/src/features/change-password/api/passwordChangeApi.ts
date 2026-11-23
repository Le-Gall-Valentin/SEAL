import { reloadCsrf, requestNoContent } from '@/shared/api'
import type { IPasswordChangeApi } from './IPasswordChangeApi'

/** Adaptateur HTTP du port IPasswordChangeApi. */
export const passwordChangeApi: IPasswordChangeApi = {
  async changePassword(change) {
    await requestNoContent('/api/v1/account/password', {
      method: 'POST',
      body: {
        currentPassword: change.currentPassword,
        newPassword: change.newPassword,
        signOutOtherSessions: change.signOutOtherSessions,
      },
    })
    // AUTH-60, ADR 0034 : nouvel identifiant de session et nouveau jeton CSRF. Un échec ne défait pas le changement :
    // shared/api rechargera le jeton absent à la prochaine requête non sûre.
    await reloadCsrf().catch(() => undefined)
  },
}
