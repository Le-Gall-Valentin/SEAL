import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ComponentProps, ReactNode } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import type { Conversation, Member } from '@/entities/conversation'
import { ApiError, isolate } from '@/shared/lib'
import { RemoveMemberApiProvider } from '../model/removeMemberPort'
import { RemoveMemberDialog } from './RemoveMemberDialog'

type Api = ComponentProps<typeof RemoveMemberApiProvider>['api']

const CAMILLE: Member = {
  userId: 'u-1',
  username: 'camille.t',
  displayName: 'Camille Tremblay',
  role: 'MEMBER',
}
const GROUP: Conversation = {
  id: 'g-1',
  type: 'GROUP',
  name: 'Ski',
  members: [{ userId: 'me', username: 'moi', displayName: 'Moi', role: 'ADMIN' }, CAMILLE],
  createdAt: new Date(0),
  lastServerSeq: 0,
}

async function open(api: Api) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const onClose = vi.fn()
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>
      <RemoveMemberApiProvider api={api}>{children}</RemoveMemberApiProvider>
    </QueryClientProvider>
  )
  const screen = await render(
    <RemoveMemberDialog conversation={GROUP} member={CAMILLE} onClose={onClose} />,
    {
      wrapper,
    },
  )
  return { screen, onClose }
}

describe('features/remove-group-member : retirer un membre (GRP-03, ADR 0041)', () => {
  it('GRP-03, ADR 0041 (point 4) : confirmation au nom isolé, puis retrait', async () => {
    const exclude = vi.fn(() => Promise.resolve())
    const { screen, onClose } = await open({ exclude })

    await expect
      .element(
        screen.getByRole('alertdialog', {
          name: `Retirer ${isolate('Camille Tremblay')} du groupe ?`,
        }),
      )
      .toBeVisible()
    await expect
      .element(
        screen.getByText(
          `${isolate('Camille Tremblay')} ne recevra plus les nouveaux messages du groupe.`,
        ),
      )
      .toBeVisible()
    await screen.getByRole('button', { name: 'Retirer', exact: true }).click()

    await expect.poll(() => onClose.mock.calls).toEqual([[true]])
    expect(exclude).toHaveBeenCalledWith('g-1', 'u-1')
  })

  it('ADR 0041 (point 4) : un membre déjà parti (NOT_A_MEMBER) vaut un retrait réussi', async () => {
    const { screen, onClose } = await open({
      exclude: () => Promise.reject(new ApiError(404, 'NOT_A_MEMBER')),
    })

    await screen.getByRole('button', { name: 'Retirer', exact: true }).click()

    await expect.poll(() => onClose.mock.calls).toEqual([[true]])
  })

  it('GRP-03, ADR 0041 (point 4) : un rôle périmé s’annonce', async () => {
    const { screen, onClose } = await open({
      exclude: () => Promise.reject(new ApiError(403, 'FORBIDDEN')),
    })

    await screen.getByRole('button', { name: 'Retirer', exact: true }).click()

    await expect
      .element(screen.getByRole('alert'))
      .toHaveTextContent('Vous n’êtes plus administrateur de ce groupe.')
    expect(onClose).not.toHaveBeenCalled()
  })

  it('A06 : un double clic pendant l’envoi n’envoie qu’un retrait', async () => {
    const exclude = vi.fn(() => new Promise<void>(() => undefined))
    const { screen } = await open({ exclude })

    await screen.getByRole('button', { name: 'Retirer', exact: true }).dblClick()

    await expect
      .element(screen.getByRole('button', { name: 'Retirer', exact: true }))
      .toBeDisabled()
    expect(exclude).toHaveBeenCalledTimes(1)
  })
})
