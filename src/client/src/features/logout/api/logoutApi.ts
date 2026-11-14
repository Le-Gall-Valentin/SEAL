import { reloadCsrf, requestNoContent } from '@/shared/api'
import type { ILogoutApi } from './ILogoutApi'

/** Adaptateur HTTP du port ILogoutApi. */
export const logoutApi: ILogoutApi = {
  async logout() {
    await requestNoContent('/api/v1/auth/logout', { method: 'POST' })
    // AUTH-60 : la déconnexion expire la session et le cookie XSRF-TOKEN.
    await reloadCsrf()
  },
}
