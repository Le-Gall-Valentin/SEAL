import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import type { ReceivedRequest, SentRequest } from '@/entities/friend'
import { ApiError, ClockProvider } from '@/shared/lib'
import { AnswerFriendRequestApiProvider } from '../model/answerFriendRequestPort'
import { useAnswerFriendRequest } from '../model/useAnswerFriendRequest'
import { AnswerAlerts } from './AnswerAlerts'
import { ReceivedRequestRow } from './ReceivedRequestRow'
import { SentRequestRow } from './SentRequestRow'

const NOW = Date.parse('2026-11-23T12:00:00Z')
const received: ReceivedRequest = {
  id: '0193a1b2-0000-7000-8000-000000000001',
  userId: '0192f7a1-0000-7000-8000-000000000011',
  username: 'sarah.belanger',
  displayName: 'Sarah Bélanger',
  createdAt: new Date('2026-11-23T10:00:00Z'),
}
const sent: SentRequest = {
  id: '0193a1b2-0000-7000-8000-000000000002',
  userId: '0192f7a1-0000-7000-8000-000000000012',
  username: 'antoine.roy',
  createdAt: new Date('2026-11-20T12:00:00Z'),
}

type Api = Parameters<typeof AnswerFriendRequestApiProvider>[0]['api']

function Harness() {
  const answers = useAnswerFriendRequest()
  return (
    <>
      <AnswerAlerts gone={answers.gone} failure={answers.formFailure} />
      <ul>
        <ReceivedRequestRow
          request={received}
          busy={answers.busyId === received.id}
          onAccept={answers.accept}
          onDecline={answers.decline}
        />
        <SentRequestRow
          request={sent}
          busy={answers.busyId === sent.id}
          onRevoke={answers.revoke}
        />
      </ul>
    </>
  )
}

async function mount(api: Partial<Api>) {
  const never = () => Promise.reject(new Error('non attendu'))
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const invalidate = vi.spyOn(client, 'invalidateQueries')
  const screen = await render(
    <QueryClientProvider client={client}>
      <ClockProvider clock={{ now: () => NOW }}>
        <AnswerFriendRequestApiProvider
          api={{ accept: never, decline: never, revoke: never, ...api }}
        >
          <Harness />
        </AnswerFriendRequestApiProvider>
      </ClockProvider>
    </QueryClientProvider>,
  )
  return { screen, invalidate }
}

describe('features/answer-friend-request : invitations reçues et envoyées (Amis-Tablette, ADR 0038)', () => {
  it('API-01, ADR 0038 : reçue avec nom, identifiant et date ; envoyée par l’identifiant seul, « En attente »', async () => {
    const { screen } = await mount({})

    await expect.element(screen.getByText('Sarah Bélanger')).toBeVisible()
    await expect.element(screen.getByText('@sarah.belanger')).toBeVisible()
    await expect.element(screen.getByText('Reçue il y a 2 heures')).toBeVisible()
    await expect.element(screen.getByText('@antoine.roy')).toBeVisible()
    await expect.element(screen.getByText('Envoyée il y a 3 jours')).toBeVisible()
    await expect.element(screen.getByText('En attente')).toBeVisible()
    await expect
      .element(screen.getByRole('button', { name: 'Accepter — Sarah Bélanger' }))
      .toBeVisible()
    await expect
      .element(screen.getByRole('button', { name: 'Refuser — Sarah Bélanger' }))
      .toBeVisible()
    await expect
      .element(screen.getByRole('button', { name: 'Révoquer — @antoine.roy' }))
      .toBeVisible()
  })

  it.each([
    ['Accepter — Sarah Bélanger', 'accept', received.id],
    ['Refuser — Sarah Bélanger', 'decline', received.id],
    ['Révoquer — @antoine.roy', 'revoke', sent.id],
  ] as const)('FRD-01 : « %s » appelle %s puis relit les listes', async (name, action, id) => {
    const call = vi.fn(() => Promise.resolve())
    const { screen, invalidate } = await mount({ [action]: call })

    await screen.getByRole('button', { name }).click()

    await expect.poll(() => call.mock.calls).toEqual([[id]])
    await expect.poll(() => invalidate.mock.calls).toContainEqual([{ queryKey: ['friends'] }])
  })

  it('ADR 0038 (point 2) : une invitation close ailleurs (404) l’annonce et relit les listes', async () => {
    const { screen, invalidate } = await mount({
      accept: () => Promise.reject(new ApiError(404, 'NOT_FOUND')),
    })

    await screen.getByRole('button', { name: 'Accepter — Sarah Bélanger' }).click()

    await expect
      .element(screen.getByRole('alert'))
      .toHaveTextContent('Cette invitation n’existe plus.')
    await expect.poll(() => invalidate.mock.calls).toContainEqual([{ queryKey: ['friends'] }])
  })
})
