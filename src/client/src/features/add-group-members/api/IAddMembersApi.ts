import type { Conversation } from '@/entities/conversation'

/** Port de `POST /api/v1/conversations/{id}/members` (14.5, GRP-03). */
export interface IAddMembersApi {
  admit(conversationId: string, userIds: readonly string[]): Promise<Conversation>
}
