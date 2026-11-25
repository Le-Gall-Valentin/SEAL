import { z } from 'zod'
import { CONVERSATION_RESPONSE, conversationOf } from '@/entities/conversation'
import { request } from '@/shared/api'
import type { IStartDirectApi } from './IStartDirectApi'

/** SEC-01 : réponse de `direct` (ADR 0039, point 6), champs inconnus refusés. */
const OPENED = z.strictObject({ created: z.boolean(), conversation: CONVERSATION_RESPONSE })

/** Adaptateur HTTP du port IStartDirectApi : trouver ou créer la directe (CNV-01). */
export const startDirectApi: IStartDirectApi = {
  async openDirect(userId) {
    const body = await request('/api/v1/conversations/direct', OPENED, {
      method: 'POST',
      body: { userId },
    })
    return { created: body.created, conversation: conversationOf(body.conversation) }
  },
}
