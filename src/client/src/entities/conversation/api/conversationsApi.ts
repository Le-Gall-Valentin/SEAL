import { z } from 'zod'
import {
  isDisplayNameReadable,
  isNameReadable,
  isUsernameWellFormed,
} from '@/entities/account/@x/conversation'
import { pagePath, pageSchema, request } from '@/shared/api'
import type { Conversation, IConversationsApi } from './IConversationsApi'

/** GRP-01, ADR 0040 : nom d'un groupe en points de code, et sa borne brute en unités UTF-16 (SEC-01). */
export const GROUP_NAME_MAX_LENGTH = 64
export const GROUP_NAME_RAW_MAX_LENGTH = 128
/** GRP-01 : 50 membres actifs au plus, créateur compris. */
export const MAX_MEMBERS = 50

const UUID = z.uuid()
// SEC-01, AUTH-01, ACC-03 : le nom d'un autre compte qui ne suit pas les règles d'écriture est refusé (échec fermé),
// à la règle de lecture de l'ADR 0042.
const MEMBER = z.strictObject({
  userId: UUID,
  username: z.string().max(20).refine(isUsernameWellFormed),
  displayName: z.string().max(200).refine(isDisplayNameReadable),
  role: z.enum(['ADMIN', 'MEMBER']),
})

const COMMON = {
  id: UUID,
  members: z.array(MEMBER).min(1).max(MAX_MEMBERS),
  createdAt: z.iso.datetime(),
  lastServerSeq: z.int().min(0),
  unreadCount: z.int().min(0),
  lastSenderSeq: z.int().min(0).nullable(),
  // ADR 0041 (point 7) : la forme de l'enveloppe arrive au lot 4 ; d'ici là, toute enveloppe est refusée.
  lastEnvelope: z.null(),
}

/** SEC-01 : vue d'une conversation (14.5, ADR 0039 point 6), champs inconnus refusés. */
export const CONVERSATION_RESPONSE = z.discriminatedUnion('type', [
  z.strictObject({
    ...COMMON,
    type: z.literal('DIRECT'),
    name: z.null(),
    members: z.array(MEMBER).min(1).max(2),
  }),
  z.strictObject({
    ...COMMON,
    type: z.literal('GROUP'),
    name: z
      .string()
      .max(GROUP_NAME_RAW_MAX_LENGTH)
      .refine((name) => isNameReadable(name, GROUP_NAME_MAX_LENGTH)),
  }),
])

/** Vue reçue, date convertie ; aussi la réponse des créations, ajouts et renommages (features). */
export function conversationOf(dto: z.infer<typeof CONVERSATION_RESPONSE>): Conversation {
  return {
    id: dto.id,
    type: dto.type,
    name: dto.name,
    members: dto.members,
    createdAt: new Date(dto.createdAt),
    lastServerSeq: dto.lastServerSeq,
  }
}

function options(signal: AbortSignal | undefined) {
  return signal ? { signal } : {}
}

/** Adaptateur HTTP du port IConversationsApi. */
export const conversationsApi: IConversationsApi = {
  async list(after, signal) {
    const page = await request(
      pagePath('/api/v1/conversations', after),
      pageSchema(CONVERSATION_RESPONSE),
      options(signal),
    )
    return { next: page.next, items: page.items.map(conversationOf) }
  },

  async get(id, signal) {
    return conversationOf(
      await request(
        `/api/v1/conversations/${encodeURIComponent(id)}`,
        CONVERSATION_RESPONSE,
        options(signal),
      ),
    )
  },
}
