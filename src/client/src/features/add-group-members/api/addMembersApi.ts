import { CONVERSATION_RESPONSE, conversationOf } from '@/entities/conversation'
import { request } from '@/shared/api'
import type { IAddMembersApi } from './IAddMembersApi'

/** Adaptateur HTTP du port IAddMembersApi : les amis de l'appelant rejoignent le groupe, vue rendue (GRP-03). */
export const addMembersApi: IAddMembersApi = {
  async admit(conversationId, userIds) {
    return conversationOf(
      await request(
        `/api/v1/conversations/${encodeURIComponent(conversationId)}/members`,
        CONVERSATION_RESPONSE,
        {
          method: 'POST',
          body: { userIds },
        },
      ),
    )
  },
}
