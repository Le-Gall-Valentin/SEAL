import { CONVERSATION_RESPONSE, conversationOf } from '@/entities/conversation'
import { request } from '@/shared/api'
import type { ICreateGroupApi } from './ICreateGroupApi'

/**
 * Adaptateur HTTP du port ICreateGroupApi : groupe et membres initiaux en une requête (CNV-03, GRP-02). Le nom part en
 * NFC : saisi décomposé, il tiendrait en 64 caractères mais dépasserait les 128 unités du serveur (SEC-01).
 */
export const createGroupApi: ICreateGroupApi = {
  async createGroup(name, memberIds) {
    return conversationOf(
      await request('/api/v1/conversations/group', CONVERSATION_RESPONSE, {
        method: 'POST',
        body: { name: name.normalize('NFC'), memberIds },
      }),
    )
  },
}
