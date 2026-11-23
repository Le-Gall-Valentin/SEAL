import { requestNoContent } from '@/shared/api'
import type { IAnswerFriendRequestApi } from './IAnswerFriendRequestApi'

function path(requestId: string): string {
  return `/api/v1/friend-requests/${encodeURIComponent(requestId)}`
}

/** Adaptateur HTTP du port IAnswerFriendRequestApi (14.4). */
export const answerFriendRequestApi: IAnswerFriendRequestApi = {
  accept: (requestId) => requestNoContent(`${path(requestId)}/accept`, { method: 'POST' }),
  decline: (requestId) => requestNoContent(`${path(requestId)}/decline`, { method: 'POST' }),
  revoke: (requestId) => requestNoContent(path(requestId), { method: 'DELETE' }),
}
