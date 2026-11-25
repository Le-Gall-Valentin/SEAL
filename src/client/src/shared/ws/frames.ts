import { z } from 'zod'

/** TRN-14 : 1 à 64 caractères, choisi par l'émetteur de la trame. */
export const FRAME_ID = /^[A-Za-z0-9_-]{1,64}$/

const UUID = z.string().regex(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/)
const ID = z.string().regex(FRAME_ID)

const JSON_OBJECT = z.record(z.string(), z.json())

/** Valeur JSON. */
export type JsonValue = z.infer<ReturnType<typeof z.json>>
type JsonObject = z.infer<typeof JSON_OBJECT>

function frame<T extends string, P extends z.ZodType>(type: T, payload: P) {
  return z.strictObject({ type: z.literal(type), id: ID, payload })
}

const REPLY = z.discriminatedUnion('type', [
  frame('ack', JSON_OBJECT),
  frame('pong', z.strictObject({})),
  frame(
    'error',
    z.strictObject({
      code: z.string().max(64),
      retryAfter: z.number().int().min(0).max(3600).optional(),
    }),
  ),
])

/** CDC 12.2, ADR 0043 : événements du serveur connus de ce client ; tout autre type est ignoré. */
const EVENT = z.discriminatedUnion('type', [
  frame('friend.request', z.strictObject({ requestId: UUID })),
  frame('friend.update', z.strictObject({ userId: UUID })),
  frame('conv.update', z.strictObject({ conversationId: UUID })),
  frame('session.revoked', z.strictObject({})),
])

export type ServerEvent =
  | { readonly type: 'friend.request'; readonly requestId: string }
  | { readonly type: 'friend.update'; readonly userId: string }
  | { readonly type: 'conv.update'; readonly conversationId: string }
  | { readonly type: 'session.revoked' }

export type ReplyOutcome =
  | { readonly ok: true; readonly payload: JsonObject }
  | { readonly ok: false; readonly code: string; readonly retryAfterSeconds: number | null }

export type ServerFrame =
  | { readonly kind: 'reply'; readonly id: string; readonly outcome: ReplyOutcome }
  | { readonly kind: 'event'; readonly event: ServerEvent }

function readJson(text: string): unknown {
  try {
    return JSON.parse(text)
  } catch {
    return undefined
  }
}

/** A10 : la trame lue, ou null si elle est illisible, hors schéma ou d'un type inconnu (échec fermé). */
export function parseServerFrame(text: string): ServerFrame | null {
  const raw = readJson(text)
  const reply = REPLY.safeParse(raw)
  if (reply.success) {
    const { id, type, payload } = reply.data
    switch (type) {
      case 'ack':
        return { kind: 'reply', id, outcome: { ok: true, payload } }
      case 'pong':
        return { kind: 'reply', id, outcome: { ok: true, payload: {} } }
      case 'error':
        return {
          kind: 'reply',
          id,
          outcome: { ok: false, code: payload.code, retryAfterSeconds: payload.retryAfter ?? null },
        }
    }
  }
  const event = EVENT.safeParse(raw)
  if (!event.success) {
    return null
  }
  return { kind: 'event', event: eventOf(event.data) }
}

function eventOf(frame: z.infer<typeof EVENT>): ServerEvent {
  switch (frame.type) {
    case 'friend.request':
      return { type: frame.type, requestId: frame.payload.requestId }
    case 'friend.update':
      return { type: frame.type, userId: frame.payload.userId }
    case 'conv.update':
      return { type: frame.type, conversationId: frame.payload.conversationId }
    case 'session.revoked':
      return { type: frame.type }
  }
}
