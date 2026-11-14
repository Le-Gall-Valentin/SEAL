import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import {
  type Account,
  type ISessionApi,
  SessionApiProvider,
  useCurrentAccount,
} from '@/entities/session'
import { LogoutApiProvider } from '../model/logoutApiPort'
import { LogoutButton } from './LogoutButton'

const ACTIVE: Account = {
  id: '0192f0c4-7b1a-7cc0-9a51-3f6f8e2d4b10',
  username: 'valentin',
  displayName: 'Valentin',
  email: 'valentin@uqac.ca',
  status: 'ACTIVE',
  verification: null,
}

function Probe() {
  const account = useCurrentAccount()
  return <p>{account.data === undefined ? '…' : (account.data?.username ?? 'anonyme')}</p>
}

describe('AUTH-53 : « Se déconnecter »', () => {
  it('AUTH-53 : ferme la session ; le client oublie le compte et ses données', async () => {
    let loggedIn = true
    let calls = 0
    const sessionApi: ISessionApi = {
      me: () => Promise.resolve(loggedIn ? ACTIVE : null),
      reloadCsrf: () => Promise.resolve(),
    }
    const client = new QueryClient()
    client.setQueryData(['conversations'], ['secret'])
    const screen = await render(
      <QueryClientProvider client={client}>
        <SessionApiProvider api={sessionApi}>
          <LogoutApiProvider
            api={{
              logout: () => {
                calls += 1
                loggedIn = false
                return Promise.resolve()
              },
            }}
          >
            <Probe />
            <LogoutButton />
          </LogoutApiProvider>
        </SessionApiProvider>
      </QueryClientProvider>,
    )

    await expect.element(screen.getByText('valentin')).toBeVisible()
    await screen.getByRole('button', { name: 'Se déconnecter' }).click()

    await expect.element(screen.getByText('anonyme')).toBeVisible()
    expect(calls).toBe(1)
    expect(client.getQueryData(['conversations'])).toBeUndefined()
  })
})
