import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createMemoryRouter } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { type ISessionApi, SessionApiProvider } from '@/entities/session'
import { type ILoginApi, LoginApiProvider } from '@/features/login'
import { LoginPage } from './LoginPage'

async function renderLogin(state: unknown) {
  const sessionApi: ISessionApi = {
    me: () => Promise.resolve(null),
    reloadCsrf: () => Promise.resolve(),
  }
  const loginApi: ILoginApi = { login: () => Promise.resolve() }
  const router = createMemoryRouter([{ path: '/connexion', element: <LoginPage /> }], {
    initialEntries: [{ pathname: '/connexion', state }],
  })
  const screen = await render(
    <QueryClientProvider client={new QueryClient()}>
      <SessionApiProvider api={sessionApi}>
        <LoginApiProvider api={loginApi}>
          <RouterProvider router={router} />
        </LoginApiProvider>
      </SessionApiProvider>
    </QueryClientProvider>,
  )
  return Object.assign(screen, { router })
}

describe('Écran Connexion', () => {
  it('ADR 0024 : après une réinitialisation, « Mot de passe enregistré. Connectez-vous avec le nouveau. »', async () => {
    const screen = await renderLogin({ passwordReset: true })

    await expect
      .element(screen.getByRole('status'))
      .toMatchTextContent('Mot de passe enregistré. Connectez-vous avec le nouveau.')
    // Lue une fois, la confirmation quitte l'historique : un rechargement ne la réaffiche pas.
    await expect.poll((): unknown => screen.router.state.location.state).toBeNull()
    await expect
      .element(screen.getByRole('status'))
      .toMatchTextContent('Mot de passe enregistré. Connectez-vous avec le nouveau.')
  })

  it('ADR 0024 : sans réinitialisation, ni confirmation, ni état de navigation forgé', async () => {
    const screen = await renderLogin({ passwordReset: 'oui' })

    await expect.element(screen.getByRole('heading', { level: 1, name: 'Connexion' })).toBeVisible()
    await expect.element(screen.getByText(/Mot de passe enregistré/)).not.toBeInTheDocument()
  })

  it('AUTH-20 : titre, sous-titre et lien vers l’inscription de la maquette, sans OAuth (OPS-03)', async () => {
    const sessionApi: ISessionApi = {
      me: () => Promise.resolve(null),
      reloadCsrf: () => Promise.resolve(),
    }
    const loginApi: ILoginApi = { login: () => Promise.resolve() }
    const router = createMemoryRouter([{ path: '/connexion', element: <LoginPage /> }], {
      initialEntries: ['/connexion'],
    })
    const screen = await render(
      <QueryClientProvider client={new QueryClient()}>
        <SessionApiProvider api={sessionApi}>
          <LoginApiProvider api={loginApi}>
            <RouterProvider router={router} />
          </LoginApiProvider>
        </SessionApiProvider>
      </QueryClientProvider>,
    )

    await expect.element(screen.getByRole('heading', { level: 1, name: 'Connexion' })).toBeVisible()
    await expect.element(screen.getByText('Heureux de vous revoir.')).toBeVisible()
    const createAccount = screen.getByRole('link', { name: 'Créer un compte' })
    await expect.element(createAccount).toHaveAttribute('href', '/inscription')
    expect(createAccount.element().parentElement?.textContent).toBe(
      'Pas encore de compte ? Créer un compte',
    )
    await expect.element(screen.getByText('ou', { exact: true })).not.toBeInTheDocument()
    await expect.element(screen.getByText(/Continuer avec/)).not.toBeInTheDocument()
  })
})
