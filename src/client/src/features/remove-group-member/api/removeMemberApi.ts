import { requestNoContent } from '@/shared/api'
import type { IRemoveMemberApi } from './IRemoveMemberApi'

/** Adaptateur HTTP du port IRemoveMemberApi : 204 au retrait (GRP-03). */
export const removeMemberApi: IRemoveMemberApi = {
  exclude: (conversationId, userId) =>
    requestNoContent(
      `/api/v1/conversations/${encodeURIComponent(conversationId)}/members/${encodeURIComponent(userId)}`,
      { method: 'DELETE' },
    ),
}
