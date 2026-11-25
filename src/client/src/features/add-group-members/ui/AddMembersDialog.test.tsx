import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ComponentProps, ReactNode } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import type { Conversation, Member } from '@/entities/conversation'
import { type Friend, FriendsApiProvider } from '@/entities/friend'
import { ApiError, isolate } from '@/shared/lib'
import { AddMembersApiProvider } from '../model/addMembersPort'
import { AddMembersDialog } from './AddMembersDialog'

type Api = ComponentProps<typeof AddMembersApiProvider>['api']

const friend = (n: number, displayName: string): Friend => ({
  userId: `u-${String(n)}`,
  username: `ami.${String(n)}`,
  displayName,
  friendsSince: new Date(0),
  lastInteractionAt: null,
  interactionCount30d: 0,
})
const member = (f: Friend): Member => ({ ...f, role: 'MEMBER' })
const CAMILLE = friend(1, 'Camille Tremblay')
const INES = friend(2, 'Inès Côté')
const LEA = friend(3, 'Léa Roy')
const GROUP: Conversation = {
  id: 'g-1',
  type: 'GROUP',
  name: 'Ski',
  members: [{ userId: 'me', username: 'moi', displayName: 'Moi', role: 'ADMIN' }, member(CAMILLE)],
  createdAt: new Date(0),
  lastServerSeq: 0,
}

async function open(
  api: Api,
  conversation = GROUP,
  friends: () => Promise<{ items: Friend[]; next: string | null }> = () =>
    Promise.resolve({ items: [CAMILLE, INES, LEA], next: null }),
) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const onClose = vi.fn()
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>
      <FriendsApiProvider
        api={{
          friends,
          receivedRequests: () => Promise.resolve({ items: [], next: null }),
          sentRequests: () => Promise.resolve({ items: [], next: null }),
        }}
      >
        <AddMembersApiProvider api={api}>{children}</AddMembersApiProvider>
      </FriendsApiProvider>
    </QueryClientProvider>
  )
  const screen = await render(
    <AddMembersDialog conversation={conversation} open onClose={onClose} />,
    { wrapper },
  )
  return { screen, onClose, client }
}

describe('features/add-group-members : Ajouter des membres (GRP-03, GRP-04, ADR 0041)', () => {
  it('GRP-03, FRD-20 : les membres présents ne sont pas proposés ; « Ajouter (n) » ajoute les amis choisis', async () => {
    const admit = vi.fn(() =>
      Promise.resolve({ ...GROUP, members: [...GROUP.members, member(INES)] }),
    )
    const { screen, onClose, client } = await open({ admit })

    await expect.element(screen.getByRole('dialog', { name: 'Ajouter des membres' })).toBeVisible()
    await expect.element(screen.getByRole('option', { name: /Camille/u })).not.toBeInTheDocument()
    await screen.getByRole('option', { name: /Inès Côté/u }).click()
    await screen.getByRole('button', { name: 'Ajouter (1)' }).click()

    await expect.poll(() => onClose.mock.calls.length).toBe(1)
    expect(admit).toHaveBeenCalledWith('g-1', ['u-2'])
    expect(
      client.getQueryData<Conversation>(['conversations', 'detail', 'g-1'])?.members,
    ).toHaveLength(3)
  })

  it('GRP-01 : le sélecteur s’arrête aux places restantes', async () => {
    const crowd = Array.from({ length: 48 }, (_, i) =>
      member(friend(100 + i, `Membre ${String(i)}`)),
    )
    const { screen } = await open(
      { admit: vi.fn() },
      { ...GROUP, members: [GROUP.members[0] as Member, ...crowd] },
    )

    await screen.getByRole('option', { name: /Inès Côté/u }).click()

    await expect
      .element(screen.getByRole('combobox', { name: 'Membres' }))
      .toHaveAttribute('aria-disabled', 'true')
  })

  it.each([
    [new ApiError(409, 'GROUP_FULL'), 'Le groupe compte déjà 50 membres.'],
    [
      new ApiError(403, 'NOT_FRIENDS'),
      'Vous ne pouvez ajouter que vos amis. Leur liste vient d’être mise à jour.',
    ],
  ])('ADR 0041 (point 4) : %s s’annonce', async (error, text) => {
    const { screen, onClose } = await open({ admit: () => Promise.reject(error) })

    await screen.getByRole('option', { name: /Inès Côté/u }).click()
    await screen.getByRole('button', { name: 'Ajouter (1)' }).click()

    await expect.element(screen.getByRole('alert')).toHaveTextContent(text)
    expect(onClose).not.toHaveBeenCalled()
  })

  it('A06 : un double clic n’envoie qu’un ajout', async () => {
    const admit = vi.fn(() => Promise.resolve(GROUP))
    const { screen } = await open({ admit })

    await screen.getByRole('option', { name: /Inès Côté/u }).click()
    await screen.getByRole('button', { name: 'Ajouter (1)' }).dblClick()

    await expect.poll(() => admit.mock.calls.length).toBe(1)
  })

  it('FRD-21, ADR 0041 (point 4) : après NOT_FRIENDS, l’ancien ami quitte la sélection avec la liste relue', async () => {
    let calls = 0
    const { screen } = await open(
      { admit: () => Promise.reject(new ApiError(403, 'NOT_FRIENDS')) },
      GROUP,
      () => {
        calls += 1
        return Promise.resolve({
          items: calls === 1 ? [CAMILLE, INES, LEA] : [CAMILLE, LEA],
          next: null,
        })
      },
    )
    await screen.getByRole('option', { name: /Inès Côté/u }).click()
    await screen.getByRole('option', { name: /Léa Roy/u }).click()
    await screen.getByRole('button', { name: 'Ajouter (2)' }).click()

    await expect.element(screen.getByRole('alert')).toBeVisible()
    await expect
      .element(screen.getByRole('button', { name: `Retirer ${isolate('Inès Côté')}` }))
      .not.toBeInTheDocument()
    await expect.element(screen.getByRole('button', { name: 'Ajouter (1)' })).toBeEnabled()
  })
})
