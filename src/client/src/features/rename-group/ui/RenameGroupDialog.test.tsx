import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ComponentProps, ReactNode } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import type { Conversation } from '@/entities/conversation'
import { ApiError } from '@/shared/lib'
import { RenameGroupApiProvider } from '../model/renameGroupPort'
import { RenameGroupDialog } from './RenameGroupDialog'

type Api = ComponentProps<typeof RenameGroupApiProvider>['api']

const GROUP: Conversation = {
  id: 'g-1',
  type: 'GROUP',
  name: 'Ski',
  members: [],
  createdAt: new Date(0),
  lastServerSeq: 0,
}

async function open(api: Api) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const onClose = vi.fn()
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>
      <RenameGroupApiProvider api={api}>{children}</RenameGroupApiProvider>
    </QueryClientProvider>
  )
  const screen = await render(<RenameGroupDialog conversation={GROUP} open onClose={onClose} />, {
    wrapper,
  })
  return { screen, onClose, client }
}

describe('features/rename-group : Renommer le groupe (GRP-03, ADR 0041)', () => {
  it('GRP-03 : le nom actuel est proposé ; « Enregistrer » n’agit qu’après un changement valide', async () => {
    const rename = vi.fn(() => Promise.resolve({ ...GROUP, name: 'Raquette' }))
    const { screen, onClose, client } = await open({ rename })
    const field = screen.getByRole('textbox', { name: 'Nom du groupe' })

    await expect.element(screen.getByRole('dialog', { name: 'Renommer le groupe' })).toBeVisible()
    await expect.element(field).toHaveValue('Ski')
    await expect.element(screen.getByRole('button', { name: 'Enregistrer' })).toBeDisabled()
    await field.fill('Raquette')
    await screen.getByRole('button', { name: 'Enregistrer' }).click()

    await expect.poll(() => onClose.mock.calls.length).toBe(1)
    expect(rename).toHaveBeenCalledWith('g-1', 'Raquette')
    expect(client.getQueryData<Conversation>(['conversations', 'detail', 'g-1'])?.name).toBe(
      'Raquette',
    )
  })

  it('GRP-01, ADR 0040 : un nom refusé par le serveur se dit sous le champ', async () => {
    const { screen } = await open({
      rename: () => Promise.reject(new ApiError(400, 'VALIDATION_FAILED', { fields: ['name'] })),
    })

    await screen.getByRole('textbox', { name: 'Nom du groupe' }).fill('Autre')
    await screen.getByRole('button', { name: 'Enregistrer' }).click()

    await expect
      .element(screen.getByText('1 à 64 caractères, dont un visible au moins.'))
      .toBeVisible()
  })

  it('GRP-03, ADR 0041 (point 4) : un rôle périmé s’annonce', async () => {
    const { screen } = await open({ rename: () => Promise.reject(new ApiError(403, 'FORBIDDEN')) })

    await screen.getByRole('textbox', { name: 'Nom du groupe' }).fill('Autre')
    await screen.getByRole('button', { name: 'Enregistrer' }).click()

    await expect
      .element(screen.getByRole('alert'))
      .toHaveTextContent('Vous n’êtes plus administrateur de ce groupe.')
  })

  it('GRP-01, ADR 0024 (point 9) : le nom refusé par le serveur n’est plus signalé une fois modifié', async () => {
    const { screen } = await open({
      rename: () => Promise.reject(new ApiError(400, 'VALIDATION_FAILED', { fields: ['name'] })),
    })
    const field = screen.getByRole('textbox', { name: 'Nom du groupe' })
    await field.fill('Autre')
    await screen.getByRole('button', { name: 'Enregistrer' }).click()
    await expect
      .element(screen.getByText('1 à 64 caractères, dont un visible au moins.'))
      .toBeVisible()

    await field.fill('Autre nom')

    await expect
      .element(screen.getByText('1 à 64 caractères, dont un visible au moins.'))
      .not.toBeInTheDocument()
  })
})
