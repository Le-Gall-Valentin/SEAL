import { ACCOUNT_RESPONSE } from '@/entities/session'
import { request, requestNoContent } from '@/shared/api'
import type { IEmailChangeApi } from './IEmailChangeApi'

/** Adaptateur HTTP du port IEmailChangeApi. */
export const emailChangeApi: IEmailChangeApi = {
  request(email) {
    return requestNoContent('/api/v1/account/email', { method: 'POST', body: { email } })
  },
  confirm(code) {
    return request('/api/v1/account/email/confirm', ACCOUNT_RESPONSE, {
      method: 'POST',
      body: { code },
    })
  },
}
