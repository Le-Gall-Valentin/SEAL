import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { type Account, SessionApiProvider, useCurrentAccount } from '@/entities/session'
import { ApiError } from '@/shared/lib'
import { ProfileApiProvider } from '../model/profileApiPort'
import { ProfileCard } from './ProfileCard'

const ACCOUNT: Account = {
  id: '0192f0c4-7b1a-7cc0-9a51-3f6f8e2d4b10',
  username: 'valentin.dupont',
  displayName: 'Valentin Dupont',
  email: 'valentin@uqac.ca',
  status: 'ACTIVE',
  verification: null,
}

function Probe() {
  const account = useCurrentAccount().data
  return <p>Cache : {account?.displayName ?? '…'}</p>
}

function Screen() {
  const account = useCurrentAccount().data
  return account == null ? null : <ProfileCard account={account} />
}

async function renderCard(changeDisplayName: (name: string) => Promise<Account>) {
  const screen = await render(
    <QueryClientProvider client={new QueryClient()}>
      <SessionApiProvider
        api={{ me: () => Promise.resolve(ACCOUNT), reloadCsrf: () => Promise.resolve() }}
      >
        <ProfileApiProvider api={{ changeDisplayName }}>
          <Probe />
          <Screen />
        </ProfileApiProvider>
      </SessionApiProvider>
    </QueryClientProvider>,
  )
  return screen
}

describe('ACC-03 : carte Profil', () => {
  it('ACC-03 : avatar à initiales, nom affiché, identifiant en lecture seule ; « Enregistrer » inactif sans changement', async () => {
    const screen = await renderCard(() => Promise.reject(new Error('non attendu')))

    await expect.element(screen.getByRole('heading', { name: 'Profil' })).toBeVisible()
    await expect.element(screen.getByText('VD', { exact: true })).toBeVisible()
    await expect.element(screen.getByLabelText('Nom affiché')).toHaveValue('Valentin Dupont')
    const username = screen.getByLabelText('Identifiant')
    await expect.element(username).toHaveValue('valentin.dupont')
    await expect.element(username).toHaveAttribute('readonly')
    await expect
      .element(username)
      .toHaveAccessibleDescription('L’identifiant ne peut pas être modifié.')
    expect(
      screen.getByText('Visible par vos amis. Modifiable à tout moment.').elements(),
    ).toHaveLength(0)
    await expect.element(screen.getByRole('button', { name: 'Enregistrer' })).toBeDisabled()
  })

  it('ACC-03, ADR 0035 : le nom changé part ; le compte rendu remplace celui du cache et la réussite est annoncée', async () => {
    const sent: string[] = []
    const screen = await renderCard((name) => {
      sent.push(name)
      return Promise.resolve({ ...ACCOUNT, displayName: 'Léa Tremblay' })
    })

    await screen.getByLabelText('Nom affiché').fill('Léa Tremblay')
    await screen.getByRole('button', { name: 'Enregistrer' }).click()

    await expect.element(screen.getByRole('status')).toHaveTextContent('Nom affiché enregistré.')
    await expect.element(screen.getByText('Cache : Léa Tremblay')).toBeVisible()
    await expect.element(screen.getByText('LT', { exact: true })).toBeVisible()
    await expect.element(screen.getByRole('button', { name: 'Enregistrer' })).toBeDisabled()
    expect(sent).toEqual(['Léa Tremblay'])
  })

  it('ACC-03 : un caractère de direction est signalé une fois le champ quitté, et « Enregistrer » reste inactif', async () => {
    const screen = await renderCard(() => Promise.reject(new Error('non attendu')))

    await screen.getByLabelText('Nom affiché').fill('Valentin‮tnod')
    await screen.getByLabelText('Identifiant').click()

    await expect
      .element(screen.getByText('1 à 50 caractères, dont un visible au moins.'))
      .toBeVisible()
    await expect.element(screen.getByRole('button', { name: 'Enregistrer' })).toBeDisabled()
  })

  it('ACC-03 : un nom refusé par le serveur (400) reste signalé tant qu’il n’est pas changé', async () => {
    const screen = await renderCard(() => Promise.reject(new ApiError(400, 'VALIDATION_FAILED')))

    await screen.getByLabelText('Nom affiché').fill('Nom refusé')
    await screen.getByRole('button', { name: 'Enregistrer' }).click()

    await expect
      .element(screen.getByText('1 à 50 caractères, dont un visible au moins.'))
      .toBeVisible()
    await expect.element(screen.getByRole('button', { name: 'Enregistrer' })).toBeDisabled()
    await screen.getByLabelText('Nom affiché').fill('Nom accepté')
    await expect.element(screen.getByRole('button', { name: 'Enregistrer' })).toBeEnabled()
  })

  it('SEC-10 : un 429 annonce le délai et bloque le champ', async () => {
    const screen = await renderCard(() =>
      Promise.reject(new ApiError(429, 'RATE_LIMITED', { retryAfterSeconds: 60 })),
    )

    await screen.getByLabelText('Nom affiché').fill('Léa')
    await screen.getByRole('button', { name: 'Enregistrer' }).click()

    await expect.element(screen.getByText('Trop de tentatives')).toBeVisible()
    await expect.element(screen.getByLabelText('Nom affiché')).toBeDisabled()
  })
})
