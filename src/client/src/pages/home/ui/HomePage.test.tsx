import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import {
  type Account,
  type ISessionApi,
  SessionApiProvider,
  useCurrentAccount,
} from '@/entities/session'
import { LogoutApiProvider } from '@/features/logout'
import { HomePage } from './HomePage'

const ACTIVE: Account = {
  id: '0192f0c4-7b1a-7cc0-9a51-3f6f8e2d4b10',
  username: 'valentin',
  displayName: 'Valentin',
  email: 'valentin@uqac.ca',
  status: 'ACTIVE',
  verification: null,
}

function SessionProbe() {
  const account = useCurrentAccount()
  return <p>{account.data === null ? 'anonyme' : 'connecté'}</p>
}

describe('ADR 0024, point 2 : accueil provisoire du lot 1', () => {
  it('ADR 0024 : le logo et le seul bouton « Se déconnecter », qui ferme la session (AUTH-53)', async () => {
    let loggedIn = true
    const sessionApi: ISessionApi = {
      me: () => Promise.resolve(loggedIn ? ACTIVE : null),
      reloadCsrf: () => Promise.resolve(),
    }
    const screen = await render(
      <QueryClientProvider client={new QueryClient()}>
        <SessionApiProvider api={sessionApi}>
          <LogoutApiProvider
            api={{
              logout: () => {
                loggedIn = false
                return Promise.resolve()
              },
            }}
          >
            <SessionProbe />
            <HomePage />
          </LogoutApiProvider>
        </SessionApiProvider>
      </QueryClientProvider>,
    )

    // Le sceau du logo est décoratif (ADR 0024, point 10) : le titre se lit « seal ».
    await expect.element(screen.getByRole('heading', { level: 1, name: 'seal' })).toBeVisible()
    expect(screen.getByRole('button').elements()).toHaveLength(1)
    await expect.element(screen.getByText('connecté', { exact: true })).toBeVisible()

    await screen.getByRole('button', { name: 'Se déconnecter' }).click()
    await expect.element(screen.getByText('anonyme')).toBeVisible()
  })
})
