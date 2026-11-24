import { requestNoContent } from '@/shared/api'
import type { IRemoveFriendApi } from './IRemoveFriendApi'

/** Adaptateur HTTP du port IRemoveFriendApi (14.4). */
export const removeFriendApi: IRemoveFriendApi = {
  unfriend: (userId) =>
    requestNoContent(`/api/v1/friends/${encodeURIComponent(userId)}`, { method: 'DELETE' }),
}
