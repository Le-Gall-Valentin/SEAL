import { ACCOUNT_RESPONSE } from '@/entities/session'
import { request } from '@/shared/api'
import type { IProfileApi } from './IProfileApi'

/** Adaptateur HTTP du port IProfileApi. */
export const profileApi: IProfileApi = {
  changeDisplayName(displayName) {
    return request('/api/v1/account/profile', ACCOUNT_RESPONSE, {
      method: 'PATCH',
      body: { displayName },
    })
  },
}
