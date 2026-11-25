import { requestNoContent } from '@/shared/api'
import type { ILeaveGroupApi } from './ILeaveGroupApi'

/** Adaptateur HTTP du port ILeaveGroupApi : 204 au départ (GRP-03). */
export const leaveGroupApi: ILeaveGroupApi = {
  leave: (conversationId) =>
    requestNoContent(`/api/v1/conversations/${encodeURIComponent(conversationId)}/leave`, {
      method: 'POST',
    }),
}
