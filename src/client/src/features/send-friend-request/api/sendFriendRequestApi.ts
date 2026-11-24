import { SENT_REQUEST_RESPONSE, sentRequestOf } from '@/entities/friend'
import { request } from '@/shared/api'
import type { ISendFriendRequestApi } from './ISendFriendRequestApi'

/** Adaptateur HTTP du port ISendFriendRequestApi : 201 avec l'invitation, sans nom affiché (API-01). */
export const sendFriendRequestApi: ISendFriendRequestApi = {
  async send(username) {
    return sentRequestOf(
      await request('/api/v1/friend-requests', SENT_REQUEST_RESPONSE, {
        method: 'POST',
        body: { username },
      }),
    )
  },
}
