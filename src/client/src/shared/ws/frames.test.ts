import { describe, expect, it } from 'vitest'
import { parseServerFrame } from './frames'

const UUID = '0192f7a1-0000-7000-8000-000000000001'

describe('shared/ws : lecture stricte des trames du serveur (TRN-14)', () => {
  it('TRN-14 : ack, error et pong se lisent comme des réponses', () => {
    expect(parseServerFrame(JSON.stringify({ type: 'ack', id: 'c1', payload: { n: 1 } }))).toEqual({
      kind: 'reply',
      id: 'c1',
      outcome: { ok: true, payload: { n: 1 } },
    })
    expect(
      parseServerFrame(
        JSON.stringify({
          type: 'error',
          id: 'c2',
          payload: { code: 'RATE_LIMITED', retryAfter: 3 },
        }),
      ),
    ).toEqual({
      kind: 'reply',
      id: 'c2',
      outcome: { ok: false, code: 'RATE_LIMITED', retryAfterSeconds: 3 },
    })
    expect(parseServerFrame(JSON.stringify({ type: 'pong', id: 'c3', payload: {} }))).toEqual({
      kind: 'reply',
      id: 'c3',
      outcome: { ok: true, payload: {} },
    })
  })

  it('FRD-04, CNV-04 : les événements connus se lisent avec leur charge', () => {
    expect(
      parseServerFrame(
        JSON.stringify({ type: 'friend.request', id: 's1', payload: { requestId: UUID } }),
      ),
    ).toEqual({ kind: 'event', event: { type: 'friend.request', requestId: UUID } })
    expect(
      parseServerFrame(
        JSON.stringify({ type: 'friend.update', id: 's2', payload: { userId: UUID } }),
      ),
    ).toEqual({ kind: 'event', event: { type: 'friend.update', userId: UUID } })
    expect(
      parseServerFrame(
        JSON.stringify({ type: 'conv.update', id: 's3', payload: { conversationId: UUID } }),
      ),
    ).toEqual({ kind: 'event', event: { type: 'conv.update', conversationId: UUID } })
    expect(
      parseServerFrame(JSON.stringify({ type: 'session.revoked', id: 's4', payload: {} })),
    ).toEqual({
      kind: 'event',
      event: { type: 'session.revoked' },
    })
  })

  it.each([
    ['du texte', 'pas du JSON'],
    ['un tableau', '[]'],
    ['un champ en trop', JSON.stringify({ type: 'pong', id: 'c', payload: {}, extra: 1 })],
    ['un id invalide', JSON.stringify({ type: 'pong', id: 'a b', payload: {} })],
    ['un type inconnu', JSON.stringify({ type: 'msg.new', id: 's', payload: {} })],
    [
      'une charge hors schéma',
      JSON.stringify({ type: 'friend.request', id: 's', payload: { requestId: 'x' } }),
    ],
    [
      'une charge à champ en trop',
      JSON.stringify({
        type: 'conv.update',
        id: 's',
        payload: { conversationId: UUID, name: 'x' },
      }),
    ],
    ['une erreur sans code', JSON.stringify({ type: 'error', id: 'c', payload: {} })],
  ])('A10 : %s est ignoré (échec fermé)', (_, text) => {
    expect(parseServerFrame(text)).toBeNull()
  })
})
