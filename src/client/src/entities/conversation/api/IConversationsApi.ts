import type { Page } from '@/shared/lib'

export type { Page }

/** CNV-01, GRP-01 : conversation directe entre deux amis, ou groupe de 2 à 50 membres. */
export type ConversationType = 'DIRECT' | 'GROUP'

/** GRP-03 : un administrateur par groupe ; tous les membres d'une directe sont `MEMBER`. */
export type MemberRole = 'ADMIN' | 'MEMBER'

/** Membre actif au compte actif (ADR 0039, point 6), dans l'ordre d'arrivée ; profil visible par API-01. */
export interface Member {
  readonly userId: string
  readonly username: string
  readonly displayName: string
  readonly role: MemberRole
}

/**
 * CNV-04 : une conversation telle que l'appelant la voit. Non-lus, dernier `senderSeq` et dernière enveloppe attendent
 * les messages du lot 4 (ADR 0041, point 7).
 */
export interface Conversation {
  readonly id: string
  readonly type: ConversationType
  /** Nom du groupe ; `null` pour une directe, nommée par son interlocuteur. */
  readonly name: string | null
  readonly members: readonly Member[]
  readonly createdAt: Date
  readonly lastServerSeq: number
}

/** Port des lectures de la section 14.5 : la liste page par page, et le détail d'une conversation. */
export interface IConversationsApi {
  list(after: string | null, signal?: AbortSignal): Promise<Page<Conversation>>
  get(id: string, signal?: AbortSignal): Promise<Conversation>
}
