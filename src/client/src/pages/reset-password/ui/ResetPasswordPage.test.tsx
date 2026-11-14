import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createMemoryRouter } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { PasswordBlocklistProvider } from '@/entities/password'
import { type Account, type ISessionApi, SessionApiProvider } from '@/entities/session'
import { LogoutApiProvider } from '@/features/logout'
import { type IPasswordResetApi, PasswordResetApiProvider } from '@/features/password-reset'
import { ResetPasswordPage } from './ResetPasswordPage'

const TOKEN = 'Ab0-_'.repeat(8) + 'xyz'
const SECOND_TOKEN = 'Zz9_-'.repeat(8) + 'abc'
const ACTIVE: Account = {
  id: '0192f0c4-7b1a-7cc0-9a51-3f6f8e2d4b10',
  username: 'noah',
  displayName: 'Noah',
  email: 'noah@uqac.ca',
  status: 'ACTIVE',
  verification: null,
}

async function renderPage(initial: Account | null) {
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
        username: token === TOKEN ? 'valentin.dupont' : 'noah',
        email: 'valentin@uqac.ca',
        expiresAt: new Date(Date.now() + 600_000),
      })
    },
    confirm: () => Promise.resolve(),
  }
  const router = createMemoryRouter(
    [{ path: '/mot-de-passe/nouveau', element: <ResetPasswordPage /> }],
    { initialEntries: [`/mot-de-passe/nouveau#jeton=${TOKEN}`] },
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
  return { screen, inspected, router }
}

describe('Écran Nouveau-Mot-de-passe', () => {
  it('ADR 0022 : le jeton est lu dans le fragment, puis retiré de l’adresse et de l’état du routeur', async () => {
    const { screen, inspected, router } = await renderPage(null)

    await expect.element(screen.getByText('@valentin.dupont', { exact: true })).toBeVisible()
    expect(inspected).toEqual([TOKEN])
    await expect.poll(() => router.state.location.hash).toBe('')
    expect(router.state.location.pathname).toBe('/mot-de-passe/nouveau')
  })

  it('ADR 0022 : un second lien ouvert dans le même onglet est lu à son tour, puis retiré', async () => {
    const { screen, inspected, router } = await renderPage(null)
    await expect.element(screen.getByText('@valentin.dupont', { exact: true })).toBeVisible()

    await router.navigate(`/mot-de-passe/nouveau#jeton=${SECOND_TOKEN}`)

    await expect.element(screen.getByText('@noah', { exact: true })).toBeVisible()
    expect(inspected).toEqual([TOKEN, SECOND_TOKEN])
    await expect.poll(() => router.state.location.hash).toBe('')
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

  it('ADR 0024, point 10 : après « Se déconnecter », le focus va au formulaire', async () => {
    const { screen } = await renderPage(ACTIVE)
    await screen.getByRole('button', { name: 'Se déconnecter' }).click()
    await expect.element(screen.getByText('@valentin.dupont', { exact: true })).toBeVisible()

    expect(document.activeElement?.tagName).not.toBe('BODY')
    expect(
      document.activeElement?.contains(screen.getByRole('heading', { level: 1 }).element()),
    ).toBe(true)
  })
})
