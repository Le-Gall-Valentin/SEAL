import type { Page } from '@/shared/lib'

/**
 * FRD-10 : un ami, vu par le compte de la session. Les statistiques d'interaction nourrissent l'affinité du sélecteur
 * (FRD-20) ; elles valent `null` et 0 jusqu'aux messages du lot 4 (ADR 0037, point 4).
 */
export interface Friend {
  readonly userId: string
  readonly username: string
  readonly displayName: string
  readonly friendsSince: Date
  readonly lastInteractionAt: Date | null
  readonly interactionCount30d: number
}

/** Invitation reçue : l'émetteur a dévoilé son nom affiché en invitant (API-01, ADR 0037, point 9). */
export interface ReceivedRequest {
  readonly id: string
  readonly userId: string
  readonly username: string
  readonly displayName: string
  readonly createdAt: Date
}

/** Invitation envoyée : le destinataire n'est connu que par son identifiant (API-01, ADR 0037, point 9). */
export interface SentRequest {
  readonly id: string
  readonly userId: string
  readonly username: string
  readonly createdAt: Date
}

export type { Page }

/** Port des listes de la section 14.4 : amis, invitations reçues et envoyées, page par page. */
export interface IFriendsApi {
  friends(after: string | null, signal?: AbortSignal): Promise<Page<Friend>>
  receivedRequests(after: string | null, signal?: AbortSignal): Promise<Page<ReceivedRequest>>
  sentRequests(after: string | null, signal?: AbortSignal): Promise<Page<SentRequest>>
}
