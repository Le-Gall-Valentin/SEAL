import type { Conversation } from '@/entities/conversation'

/** Port de `POST /api/v1/conversations/group` (14.5, CNV-03). */
export interface ICreateGroupApi {
  createGroup(name: string, memberIds: readonly string[]): Promise<Conversation>
}
