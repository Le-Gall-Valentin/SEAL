import type { SentRequest } from '@/entities/friend'

/** Port de POST /api/v1/friend-requests (FRD-01, 10.1). */
export interface ISendFriendRequestApi {
  /** `username` est déjà normalisé (ADR 0037, point 6). */
  send(username: string): Promise<SentRequest>
}
