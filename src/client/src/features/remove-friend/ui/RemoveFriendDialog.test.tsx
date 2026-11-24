import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { userEvent } from 'vitest/browser'
import type { Friend } from '@/entities/friend'
import { ApiError, isolate, NetworkError } from '@/shared/lib'
import { RemoveFriendApiProvider } from '../model/removeFriendPort'
import { RemoveFriendDialog } from './RemoveFriendDialog'

const friend: Friend = {
  userId: '0192f7a1-0000-7000-8000-000000000011',
  username: 'ines.cote',
  displayName: 'Inès Côté',
  friendsSince: new Date(0),
}

async function mount(unfriend: (userId: string) => Promise<void>) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const invalidate = vi.spyOn(client, 'invalidateQueries')
  const onClose = vi.fn()
  const screen = await render(
    <QueryClientProvider client={client}>
      <RemoveFriendApiProvider api={{ unfriend }}>
        <RemoveFriendDialog friend={friend} onClose={onClose} />
      </RemoveFriendApiProvider>
    </QueryClientProvider>,
  )
  return { screen, invalidate, onClose }
}

describe('features/remove-friend : confirmation du retrait (FRD-11, ADR 0038)', () => {
  it('FRD-11, ADR 0038 (point 2) : la confirmation nomme l’ami et annonce l’effet', async () => {
    const { screen } = await mount(vi.fn())

    const dialog = screen.getByRole('alertdialog')
    await expect.element(dialog).toBeVisible()
    // ADR 0017, ADR 0038 (point 9) : le nom, texte libre, est isolé dans la phrase.
    expect(dialog.element().querySelector('h2')?.textContent).toBe(
      'Retirer \u2068Inès Côté\u2069 de vos amis ?',
    )
    await expect
      .element(
        screen.getByText('Vous ne pourrez plus lui écrire. Vos groupes communs ne changent pas.'),
      )
      .toBeVisible()
  })

  it('FRD-11 : « Retirer » retire l’ami, ferme et relit les listes', async () => {
    const unfriend = vi.fn(() => Promise.resolve())
    const { screen, invalidate, onClose } = await mount(unfriend)

    await screen.getByRole('button', { name: 'Retirer', exact: true }).click()

    await expect.poll(() => onClose.mock.calls.length).toBe(1)
    expect(unfriend).toHaveBeenCalledWith(friend.userId)
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['friends'] })
  })

  it('FRD-11 : un ami déjà retiré ailleurs (404) se traite comme retiré', async () => {
    const { screen, onClose } = await mount(() => Promise.reject(new ApiError(404, 'NOT_FOUND')))

    await screen.getByRole('button', { name: 'Retirer', exact: true }).click()

    await expect.poll(() => onClose.mock.calls.length).toBe(1)
  })

  it('FRD-11 : « Annuler » et Échap ne retirent rien', async () => {
    const unfriend = vi.fn(() => Promise.resolve())
    const { screen, onClose } = await mount(unfriend)

    await screen.getByRole('button', { name: 'Annuler' }).click()
    await userEvent.keyboard('{Escape}')

    expect(onClose).toHaveBeenCalledTimes(2)
    expect(unfriend).not.toHaveBeenCalled()
  })

  it('FRD-11 : un échec réseau s’annonce dans la confirmation, qui reste ouverte', async () => {
    const { screen, onClose } = await mount(() => Promise.reject(new NetworkError()))

    await screen.getByRole('button', { name: 'Retirer', exact: true }).click()

    await expect
      .element(screen.getByRole('alert'))
      .toHaveTextContent('Connexion impossible. Vérifiez votre réseau puis réessayez.')
    expect(onClose).not.toHaveBeenCalled()
  })

  it('FRD-11 : pendant l’envoi, « Annuler » est inactif et Échap ne ferme pas', async () => {
    const { screen, onClose } = await mount(() => new Promise<void>(() => undefined))

    await screen.getByRole('button', { name: 'Retirer', exact: true }).click()

    await expect.element(screen.getByRole('button', { name: 'Annuler' })).toBeDisabled()
    await userEvent.keyboard('{Escape}')
    expect(onClose).not.toHaveBeenCalled()
    await expect.element(screen.getByRole('alertdialog')).toBeVisible()
  })

  it('FRD-11 : l’erreur d’un retrait annulé ne réapparaît pas pour l’ami suivant', async () => {
    const other: Friend = {
      ...friend,
      userId: '0192f7a1-0000-7000-8000-000000000012',
      displayName: 'Jade Morin',
    }
    function Harness() {
      const [current, setCurrent] = useState<Friend | null>(friend)
      return (
        <>
          <button
            type="button"
            onClick={() => {
              setCurrent(other)
            }}
          >
            Ouvrir Jade
          </button>
          <RemoveFriendDialog
            friend={current}
            onClose={() => {
              setCurrent(null)
            }}
          />
        </>
      )
    }
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const screen = await render(
      <QueryClientProvider client={client}>
        <RemoveFriendApiProvider api={{ unfriend: () => Promise.reject(new NetworkError()) }}>
          <Harness />
        </RemoveFriendApiProvider>
      </QueryClientProvider>,
    )

    await screen.getByRole('button', { name: 'Retirer', exact: true }).click()
    await expect.element(screen.getByRole('alert')).toBeVisible()
    await screen.getByRole('button', { name: 'Annuler' }).click()
    await screen.getByRole('button', { name: 'Ouvrir Jade' }).click()

    await expect
      .element(
        screen.getByRole('alertdialog', { name: `Retirer ${isolate('Jade Morin')} de vos amis ?` }),
      )
      .toBeVisible()
    expect(screen.container.querySelector('[role="alert"]')).toBeNull()
  })
})
