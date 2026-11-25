import { afterEach, describe, expect, it, vi } from 'vitest'
import type { z } from 'zod'

const calls: string[] = []
const request = vi.fn<(path: string, schema: z.ZodType, options?: unknown) => Promise<unknown>>()

vi.mock('@/shared/api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/shared/api')>()),
  request: async (path: string, schema: z.ZodType, options?: unknown) => {
    calls.push(path)
    return schema.parse(await request(path, schema, options))
  },
}))

const { conversationsApi } = await import('./conversationsApi')

const ME = '0192f7a1-0000-7000-8000-000000000001'
const FRIEND = '0192f7a1-0000-7000-8000-000000000002'
const CONVERSATION = '0193a1b2-0000-7000-8000-000000000003'

const member = (userId: string, username: string, displayName: string, role = 'MEMBER') => ({
  userId,
  username,
  displayName,
  role,
})

const group = {
  id: CONVERSATION,
  type: 'GROUP',
  name: 'Ski du dimanche',
  members: [
    member(ME, 'moi', 'Moi Même', 'ADMIN'),
    member(FRIEND, 'camille.t', 'Camille Tremblay'),
  ],
  createdAt: '2026-11-24T20:10:25.537696Z',
  lastServerSeq: 0,
  unreadCount: 0,
  lastSenderSeq: null,
  lastEnvelope: null,
}

const direct = {
  ...group,
  type: 'DIRECT',
  name: null,
  members: [member(ME, 'moi', 'Moi Même'), member(FRIEND, 'camille.t', 'Camille Tremblay')],
}

describe('entities/conversation : adaptateur de la liste et du détail (14.5, ADR 0039)', () => {
  afterEach(() => {
    calls.length = 0
    vi.resetAllMocks()
  })

  it('CNV-04, ADR 0037 (point 3) : une page de conversations, dates converties, curseur suivant', async () => {
    request.mockResolvedValue({ items: [group, direct], next: CONVERSATION })

    const page = await conversationsApi.list(null)

    expect(calls).toEqual(['/api/v1/conversations?limit=50'])
    expect(page.next).toBe(CONVERSATION)
    expect(page.items[0]).toEqual({
      id: CONVERSATION,
      type: 'GROUP',
      name: 'Ski du dimanche',
      members: group.members,
      createdAt: new Date('2026-11-24T20:10:25.537Z'),
      lastServerSeq: 0,
    })
    expect(page.items[1]?.name).toBeNull()
  })

  it('CNV-06 : le détail se lit à l’adresse de la conversation, identifiant encodé', async () => {
    request.mockResolvedValue(group)

    const conversation = await conversationsApi.get(CONVERSATION)

    expect(calls).toEqual([`/api/v1/conversations/${CONVERSATION}`])
    expect(conversation.members).toHaveLength(2)
  })

  it.each([
    [
      'une enveloppe avant le lot 4 (ADR 0041, point 7)',
      { ...group, lastEnvelope: { ciphertext: 'x' } },
    ],
    [
      'un nom de groupe invisible (ADR 0040)',
      { ...group, name: `S${String.fromCodePoint(0x200b)}ki` },
    ],
    ['un nom de groupe de 65 caractères', { ...group, name: 'x'.repeat(65) }],
    ['une directe nommée', { ...direct, name: 'Nom' }],
    ['un groupe sans nom', { ...group, name: null }],
    ['un rôle inconnu', { ...group, members: [member(ME, 'moi', 'Moi', 'OWNER')] }],
    ['aucun membre', { ...group, members: [] }],
    [
      '51 membres',
      { ...group, members: Array.from({ length: 51 }, () => member(ME, 'moi', 'Moi')) },
    ],
    ['une directe à trois', { ...direct, members: [...direct.members, member(ME, 'moi', 'Moi')] }],
    ['un champ inconnu', { ...group, createdBy: ME }],
    ['un identifiant mal formé', { ...group, members: [member(ME, 'Pas Bon', 'Moi')] }],
  ])('SEC-01, ADR 0038 (point 9) : refuse %s', async (_cas, body) => {
    request.mockResolvedValue(body)

    await expect(conversationsApi.get(CONVERSATION)).rejects.toThrow()
  })
})
