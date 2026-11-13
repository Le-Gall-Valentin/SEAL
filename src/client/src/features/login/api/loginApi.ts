import { reloadCsrf, requestNoContent } from '@/shared/api'
import type { ILoginApi } from './ILoginApi'

/** Adaptateur HTTP du port ILoginApi. */
export const loginApi: ILoginApi = {
  async login(credentials) {
    await requestNoContent('/api/v1/auth/login', {
      method: 'POST',
      body: { login: credentials.login, password: credentials.password },
    })
    // AUTH-60 : la connexion change la session et retire le cookie XSRF-TOKEN (ADR 0019).
    await reloadCsrf()
  },
}
