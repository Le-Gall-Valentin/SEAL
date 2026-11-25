import type { Conversation } from '@/entities/conversation'

/** Port de `PATCH /api/v1/conversations/{id}` (14.5, GRP-03). */
export interface IRenameGroupApi {
  rename(conversationId: string, name: string): Promise<Conversation>
}
