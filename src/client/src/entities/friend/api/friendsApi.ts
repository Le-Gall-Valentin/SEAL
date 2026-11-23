import { z } from 'zod'
import { request } from '@/shared/api'
import type { IFriendsApi, Page } from './IFriendsApi'

/** Taille de page demandée, le plafond du serveur (ADR 0037, point 3). */
const PAGE_LIMIT = 50

const UUID = z.uuid()
const USERNAME = z.string().min(3).max(20)
const DISPLAY_NAME = z.string().min(1).max(200)

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
const SENT = z.strictObject({
  id: UUID,
  userId: UUID,
  username: USERNAME,
  createdAt: z.iso.datetime(),
})

function pageOf<T extends z.ZodType>(item: T) {
  return z.strictObject({ items: z.array(item).max(PAGE_LIMIT), next: UUID.nullable() })
}

function path(base: string, after: string | null): string {
  const separator = base.includes('?') ? '&' : '?'
  const cursor = after === null ? '' : `&after=${encodeURIComponent(after)}`
  return `${base}${separator}limit=${String(PAGE_LIMIT)}${cursor}`
}

function options(signal: AbortSignal | undefined) {
  return signal ? { signal } : {}
}

/** Adaptateur HTTP du port IFriendsApi. */
export const friendsApi: IFriendsApi = {
  async friends(after, signal) {
    const page = await request(path('/api/v1/friends', after), pageOf(FRIEND), options(signal))
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
      path('/api/v1/friend-requests?direction=incoming', after),
      pageOf(RECEIVED),
      options(signal),
    )
    return withDates(page)
  },

  async sentRequests(after, signal) {
    const page = await request(
      path('/api/v1/friend-requests?direction=outgoing', after),
      pageOf(SENT),
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
