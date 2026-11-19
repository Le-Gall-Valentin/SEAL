import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { type ISessionApi, SessionApiProvider } from '@/entities/session'
import { LogoutApiProvider } from '@/features/logout'
import { RestoreKeysPage } from './RestoreKeysPage'

vi.mock('@/features/key-restore', () => ({
  KeyRestoreForm: ({ owner }: { readonly owner: { readonly username: string } }) => (
    <p>formulaire de {owner.username}</p>
  ),
}))

const OWNER = { userId: '0192f7a1-ffff-7000-8000-00000000a11c', username: 'valentin.dupont' }

describe('KEY-33 : page Restauration-Appareil (ADR 0029, point 6)', () => {
  it('KEY-33 : titre et texte de la maquette, le formulaire du compte, et « Se déconnecter » pour seule issue', async () => {
    const sessionApi: ISessionApi = {
      me: () => Promise.resolve(null),
      reloadCsrf: () => Promise.resolve(),
    }
    const screen = await render(
      <QueryClientProvider client={new QueryClient()}>
        <SessionApiProvider api={sessionApi}>
          <LogoutApiProvider api={{ logout: () => Promise.resolve() }}>
            <RestoreKeysPage owner={OWNER} onRestored={() => undefined} />
          </LogoutApiProvider>
        </SessionApiProvider>
      </QueryClientProvider>,
    )

    await expect
      .element(screen.getByRole('heading', { level: 1, name: 'Restaurer vos clés' }))
      .toBeVisible()
    await expect
      .element(
        screen.getByText(
          'Premier passage sur cet appareil : saisissez votre clé de récupération. Votre identité reste la même, vos contacts ne verront aucune alerte.',
        ),
      )
      .toBeVisible()
    await expect.element(screen.getByText('formulaire de valentin.dupont')).toBeVisible()
    await expect.element(screen.getByRole('button', { name: 'Se déconnecter' })).toBeVisible()
    expect(screen.getByRole('button', { name: 'Réinitialiser mon identité…' }).query()).toBeNull()
  })
})
