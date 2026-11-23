/** FRD-10 : un ami, vu par le compte de la session ; les statistiques d'interaction attendent le lot 4 (ADR 0037). */
export interface Friend {
  readonly userId: string
  readonly username: string
  readonly displayName: string
  readonly friendsSince: Date
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

/** ADR 0037, point 3 : une page et le curseur de la suivante, `null` sur la dernière. */
export interface Page<T> {
  readonly items: readonly T[]
  readonly next: string | null
}

/** Port des listes de la section 14.4 : amis, invitations reçues et envoyées, page par page. */
export interface IFriendsApi {
  friends(after: string | null, signal?: AbortSignal): Promise<Page<Friend>>
  receivedRequests(after: string | null, signal?: AbortSignal): Promise<Page<ReceivedRequest>>
  sentRequests(after: string | null, signal?: AbortSignal): Promise<Page<SentRequest>>
}
