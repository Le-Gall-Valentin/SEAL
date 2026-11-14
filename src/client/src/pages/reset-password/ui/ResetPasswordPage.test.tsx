import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createMemoryRouter } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { afterEach, describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { PasswordBlocklistProvider } from '@/entities/password'
import { type Account, type ISessionApi, SessionApiProvider } from '@/entities/session'
import { LogoutApiProvider } from '@/features/logout'
import { type IPasswordResetApi, PasswordResetApiProvider } from '@/features/password-reset'
import { ResetPasswordPage } from './ResetPasswordPage'

const TOKEN = 'Ab0-_'.repeat(8) + 'xyz'
const ACTIVE: Account = {
  id: '0192f0c4-7b1a-7cc0-9a51-3f6f8e2d4b10',
  username: 'noah',
  displayName: 'Noah',
  email: 'noah@uqac.ca',
  status: 'ACTIVE',
  verification: null,
}

async function renderPage(initial: Account | null) {
  globalThis.history.replaceState(null, '', `${globalThis.location.pathname}#jeton=${TOKEN}`)
  let account = initial
  const inspected: string[] = []
  const sessionApi: ISessionApi = {
    me: () => Promise.resolve(account),
    reloadCsrf: () => Promise.resolve(),
  }
  const resetApi: IPasswordResetApi = {
    request: () => Promise.resolve(),
    inspect: (token) => {
      inspected.push(token)
      return Promise.resolve({
        username: 'valentin.dupont',
        email: 'valentin@uqac.ca',
        expiresAt: new Date(Date.now() + 600_000),
      })
    },
    confirm: () => Promise.resolve(),
  }
  const router = createMemoryRouter(
    [{ path: '/mot-de-passe/nouveau', element: <ResetPasswordPage /> }],
    { initialEntries: ['/mot-de-passe/nouveau'] },
  )
  const screen = await render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <SessionApiProvider api={sessionApi}>
        <PasswordBlocklistProvider api={{ load: () => Promise.resolve([]) }}>
          <LogoutApiProvider
            api={{
              logout: () => {
                account = null
                return Promise.resolve()
              },
            }}
          >
            <PasswordResetApiProvider api={resetApi}>
              <RouterProvider router={router} />
            </PasswordResetApiProvider>
          </LogoutApiProvider>
        </PasswordBlocklistProvider>
      </SessionApiProvider>
    </QueryClientProvider>,
  )
  return { screen, inspected }
}

describe('Écran Nouveau-Mot-de-passe', () => {
  afterEach(() => {
    globalThis.history.replaceState(null, '', globalThis.location.pathname)
  })

  it('ADR 0022 : le jeton est lu dans le fragment, puis retiré de l’adresse', async () => {
    const { screen, inspected } = await renderPage(null)

    await expect.element(screen.getByText('@valentin.dupont', { exact: true })).toBeVisible()
    expect(inspected).toEqual([TOKEN])
    expect(globalThis.location.hash).toBe('')
    expect(globalThis.location.href).not.toContain(TOKEN)
  })

  it('ADR 0024 : navigateur connecté, « Se déconnecter » d’abord, sans perdre le lien', async () => {
    const { screen, inspected } = await renderPage(ACTIVE)

    await expect
      .element(
        screen.getByText(
          'Vous êtes connecté. Déconnectez-vous pour choisir un nouveau mot de passe.',
        ),
      )
      .toBeVisible()
    expect(inspected).toEqual([])
    await screen.getByRole('button', { name: 'Se déconnecter' }).click()

    await expect.element(screen.getByText('@valentin.dupont', { exact: true })).toBeVisible()
    expect(inspected).toEqual([TOKEN])
  })
})
