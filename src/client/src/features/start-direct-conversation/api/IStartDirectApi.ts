import type { Conversation } from '@/entities/conversation'

/** CNV-01 : la directe trouvée (200, `created` faux) ou créée (201). */
export interface OpenedDirect {
  readonly created: boolean
  readonly conversation: Conversation
}

/** Port de `POST /api/v1/conversations/direct` (14.5). */
export interface IStartDirectApi {
  openDirect(userId: string): Promise<OpenedDirect>
}
