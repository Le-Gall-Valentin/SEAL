import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createMemoryRouter } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { PasswordBlocklistProvider } from '@/entities/password'
import { type ISessionApi, SessionApiProvider } from '@/entities/session'
import { type IRegisterApi, RegisterApiProvider } from '@/features/register'
import { RegisterPage } from './RegisterPage'

describe('Écran Inscription', () => {
  it('AUTH-10 : titre, sous-titre et lien vers la connexion de la maquette, sans OAuth (OPS-03)', async () => {
    const sessionApi: ISessionApi = {
      me: () => Promise.resolve(null),
      reloadCsrf: () => Promise.resolve(),
    }
    const registerApi: IRegisterApi = {
      register: () => Promise.resolve(),
      usernameAvailability: () => Promise.resolve({ available: true, suggestions: [] }),
    }
    const router = createMemoryRouter([{ path: '/inscription', element: <RegisterPage /> }], {
      initialEntries: ['/inscription'],
    })
    const screen = await render(
      <QueryClientProvider client={new QueryClient()}>
        <SessionApiProvider api={sessionApi}>
          <PasswordBlocklistProvider api={{ load: () => Promise.resolve([]) }}>
            <RegisterApiProvider api={registerApi}>
              <RouterProvider router={router} />
            </RegisterApiProvider>
          </PasswordBlocklistProvider>
        </SessionApiProvider>
      </QueryClientProvider>,
    )

    await expect
      .element(screen.getByRole('heading', { level: 1, name: 'Créer un compte' }))
      .toBeVisible()
    await expect
      .element(screen.getByText('Trois minutes, et vos échanges sont scellés.'))
      .toBeVisible()
    const login = screen.getByRole('link', { name: 'Se connecter' })
    await expect.element(login).toHaveAttribute('href', '/connexion')
    expect(login.element().parentElement?.textContent).toBe('Déjà un compte ? Se connecter')
    await expect.element(screen.getByText(/Continuer avec/)).not.toBeInTheDocument()
  })
})
