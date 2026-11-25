import { CONVERSATION_RESPONSE, conversationOf } from '@/entities/conversation'
import { request } from '@/shared/api'
import type { IRenameGroupApi } from './IRenameGroupApi'

/** Adaptateur HTTP du port IRenameGroupApi : nouveau nom en NFC (SEC-01), vue rendue (GRP-03). */
export const renameGroupApi: IRenameGroupApi = {
  async rename(conversationId, name) {
    return conversationOf(
      await request(
        `/api/v1/conversations/${encodeURIComponent(conversationId)}`,
        CONVERSATION_RESPONSE,
        {
          method: 'PATCH',
          body: { name: name.normalize('NFC') },
        },
      ),
    )
  },
}
