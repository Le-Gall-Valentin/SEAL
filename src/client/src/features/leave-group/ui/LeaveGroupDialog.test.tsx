import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ComponentProps, ReactNode } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import type { Conversation } from '@/entities/conversation'
import { ApiError, isolate, NetworkError } from '@/shared/lib'
import { LeaveGroupApiProvider } from '../model/leaveGroupPort'
import { LeaveGroupDialog } from './LeaveGroupDialog'

type Api = ComponentProps<typeof LeaveGroupApiProvider>['api']

const GROUP: Conversation = {
  id: 'g-1',
  type: 'GROUP',
  name: 'Ski du dimanche',
  members: [],
  createdAt: new Date(0),
  lastServerSeq: 0,
}

async function open(api: Api) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const onClose = vi.fn()
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>
      <LeaveGroupApiProvider api={api}>{children}</LeaveGroupApiProvider>
    </QueryClientProvider>
  )
  const screen = await render(<LeaveGroupDialog conversation={GROUP} open onClose={onClose} />, {
    wrapper,
  })
  return { screen, onClose }
}

describe('features/leave-group : quitter un groupe (GRP-03, ADR 0041)', () => {
  it('GRP-03, ADR 0041 (point 4) : confirmation au nom isolé, puis départ', async () => {
    const leave = vi.fn(() => Promise.resolve())
    const { screen, onClose } = await open({ leave })

    await expect
      .element(screen.getByRole('alertdialog', { name: `Quitter ${isolate('Ski du dimanche')} ?` }))
      .toBeVisible()
    await expect
      .element(screen.getByText('Vous ne recevrez plus les nouveaux messages de ce groupe.'))
      .toBeVisible()
    await screen.getByRole('button', { name: 'Quitter le groupe' }).click()

    await expect.poll(() => onClose.mock.calls).toEqual([[true]])
    expect(leave).toHaveBeenCalledWith('g-1')
  })

  it('GRP-03, CNV-06, ADR 0041 (point 4) : un groupe déjà quitté (NOT_FOUND) vaut un départ réussi', async () => {
    const { screen, onClose } = await open({
      leave: () => Promise.reject(new ApiError(404, 'NOT_FOUND')),
    })

    await screen.getByRole('button', { name: 'Quitter le groupe' }).click()

    await expect.poll(() => onClose.mock.calls).toEqual([[true]])
  })

  it('GRP-03, ADR 0041 (point 4) : une panne réseau s’annonce', async () => {
    const { screen, onClose } = await open({ leave: () => Promise.reject(new NetworkError()) })

    await screen.getByRole('button', { name: 'Quitter le groupe' }).click()

    await expect.element(screen.getByRole('alert')).toBeVisible()
    expect(onClose).not.toHaveBeenCalled()
  })

  it('GRP-03, A06 : un double clic pendant l’envoi n’envoie qu’un départ', async () => {
    const leave = vi.fn(() => new Promise<void>(() => undefined))
    const { screen } = await open({ leave })

    await screen.getByRole('button', { name: 'Quitter le groupe' }).dblClick()

    await expect.element(screen.getByRole('button', { name: 'Quitter le groupe' })).toBeDisabled()
    expect(leave).toHaveBeenCalledTimes(1)
  })
})
