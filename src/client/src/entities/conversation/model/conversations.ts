import { normalizeSearch } from '@/shared/lib'
import { MAX_MEMBERS } from '../api/conversationsApi'
import type { Conversation, Member } from '../api/IConversationsApi'

/** Le segment ui n'atteint pas api (ADR 0012) : les types lui parviennent par ce modèle. */
export type { Conversation, Member }

/** CNV-01 : l'interlocuteur d'une directe ; absent si son compte n'est plus actif (ADR 0039, point 6). */
export function counterpartOf(conversation: Conversation, me: string): Member | undefined {
  return conversation.type === 'DIRECT'
    ? conversation.members.find((member) => member.userId !== me)
    : undefined
}

/** CNV-04 : nom du groupe, ou nom affiché de l'interlocuteur ; une directe sans interlocuteur n'a pas de titre. */
export function titleOf(conversation: Conversation, me: string): string | undefined {
  return conversation.type === 'GROUP'
    ? (conversation.name ?? undefined)
    : counterpartOf(conversation, me)?.displayName
}

/** ADR 0041 (point 2) : jusqu'aux messages du lot 4, la plus récemment créée d'abord, puis par identifiant. */
export function sortConversations(conversations: readonly Conversation[]): Conversation[] {
  return [...conversations].sort(
    (a, b) =>
      b.createdAt.getTime() - a.createdAt.getTime() || (a.id < b.id ? 1 : a.id > b.id ? -1 : 0),
  )
}

/**
 * CNV-10 : recherche côté client sur les noms, normalisés comme 10.3 (étape 1) : nom du groupe, ou identifiant et
 * nom affiché de l'interlocuteur d'une directe. Une saisie vide garde tout.
 */
export function matchConversation(conversation: Conversation, me: string, query: string): boolean {
  const wanted = normalizeSearch(query)
  if (wanted === '') {
    return true
  }
  const counterpart = counterpartOf(conversation, me)
  const names =
    conversation.type === 'GROUP'
      ? [conversation.name ?? '']
      : counterpart
        ? [counterpart.username, counterpart.displayName]
        : []
  return names.some((name) => normalizeSearch(name).includes(wanted))
}

/** GRP-03 : l'appelant est-il l'administrateur du groupe ? */
export function isAdmin(conversation: Conversation, me: string): boolean {
  return (
    conversation.type === 'GROUP' &&
    conversation.members.some((member) => member.userId === me && member.role === 'ADMIN')
  )
}

/** GRP-03 : seul l'administrateur renomme (ADR 0039, point 3). */
export function canRename(conversation: Conversation, me: string): boolean {
  return isAdmin(conversation, me)
}

/** GRP-03 : seul l'administrateur retire un membre, jamais lui-même : il part par « Quitter » (ADR 0039, point 3). */
export function canRemove(conversation: Conversation, me: string, userId: string): boolean {
  return (
    isAdmin(conversation, me) &&
    userId !== me &&
    conversation.members.some((member) => member.userId === userId)
  )
}

/** GRP-01 : places libres sous les 50 membres actifs. */
export function seatsLeft(conversation: Conversation): number {
  return Math.max(0, MAX_MEMBERS - conversation.members.length)
}

/** GRP-03 : tout membre ajoute ses amis, tant que le groupe n'est pas plein ; une directe ne s'étend pas. */
export function canAdd(conversation: Conversation): boolean {
  return conversation.type === 'GROUP' && seatsLeft(conversation) > 0
}

/** GRP-03 : tout membre part d'un groupe ; une directe ne se quitte pas (ADR 0039, point 3). */
export function canLeave(conversation: Conversation): boolean {
  return conversation.type === 'GROUP'
}
