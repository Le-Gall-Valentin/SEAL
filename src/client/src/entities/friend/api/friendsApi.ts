import { z } from 'zod'
import { isDisplayNameValid, isUsernameWellFormed } from '@/entities/account/@x/friend'
import { pagePath, pageSchema, request } from '@/shared/api'
import type { IFriendsApi, Page, SentRequest } from './IFriendsApi'

const UUID = z.uuid()
// SEC-01, AUTH-01, ACC-03 : le nom d'un autre compte qui ne suit pas les règles d'écriture est refusé (échec fermé).
const USERNAME = z.string().max(20).refine(isUsernameWellFormed)
const DISPLAY_NAME = z.string().max(200).refine(isDisplayNameValid)

/** SEC-01 : réponses de la section 14.4 (ADR 0037, points 3, 7 et 9), champs inconnus refusés. */
const FRIEND = z.strictObject({
  userId: UUID,
  username: USERNAME,
  displayName: DISPLAY_NAME,
  friendsSince: z.iso.datetime(),
  lastInteractionAt: z.iso.datetime().nullable(),
  interactionCount30d: z.int().min(0),
})

const RECEIVED = z.strictObject({
  id: UUID,
  userId: UUID,
  username: USERNAME,
  displayName: DISPLAY_NAME,
  createdAt: z.iso.datetime(),
})

// API-01 : une invitation envoyée ne porte jamais le nom affiché d'un non-ami.
export const SENT_REQUEST_RESPONSE = z.strictObject({
  id: UUID,
  userId: UUID,
  username: USERNAME,
  createdAt: z.iso.datetime(),
})

const SENT = SENT_REQUEST_RESPONSE

/** Invitation envoyée, date convertie ; aussi la réponse 201 de l'invitation (ADR 0037, point 7). */
export function sentRequestOf(dto: z.infer<typeof SENT_REQUEST_RESPONSE>): SentRequest {
  return { ...dto, createdAt: new Date(dto.createdAt) }
}

function options(signal: AbortSignal | undefined) {
  return signal ? { signal } : {}
}

/** Adaptateur HTTP du port IFriendsApi. */
export const friendsApi: IFriendsApi = {
  async friends(after, signal) {
    const page = await request(
      pagePath('/api/v1/friends', after),
      pageSchema(FRIEND),
      options(signal),
    )
    return {
      next: page.next,
      items: page.items.map((dto) => ({
        userId: dto.userId,
        username: dto.username,
        displayName: dto.displayName,
        friendsSince: new Date(dto.friendsSince),
      })),
    }
  },

  async receivedRequests(after, signal) {
    const page = await request(
      pagePath('/api/v1/friend-requests?direction=incoming', after),
      pageSchema(RECEIVED),
      options(signal),
    )
    return withDates(page)
  },

  async sentRequests(after, signal) {
    const page = await request(
      pagePath('/api/v1/friend-requests?direction=outgoing', after),
      pageSchema(SENT),
      options(signal),
    )
    return withDates(page)
  },
}

function withDates<T extends { readonly createdAt: string }>(
  page: Page<T>,
): Page<Omit<T, 'createdAt'> & { readonly createdAt: Date }> {
  return {
    next: page.next,
    items: page.items.map((dto) => ({ ...dto, createdAt: new Date(dto.createdAt) })),
  }
}
