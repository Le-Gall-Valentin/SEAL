import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createMemoryRouter } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { UsernameAvailabilityApiProvider } from '@/entities/account'
import { OAuthProvidersApiProvider, type OAuthProviderId } from '@/entities/oauth-provider'
import { PasswordBlocklistProvider } from '@/entities/password'
import { type ISessionApi, SessionApiProvider } from '@/entities/session'
import { type IRegisterApi, RegisterApiProvider } from '@/features/register'
import { RegisterPage } from './RegisterPage'

async function renderRegister(providers: readonly OAuthProviderId[]) {
  const sessionApi: ISessionApi = {
    me: () => Promise.resolve(null),
    reloadCsrf: () => Promise.resolve(),
  }
  const registerApi: IRegisterApi = {
    register: () => Promise.resolve(),
  }
  const availabilityApi = {
    usernameAvailability: () => Promise.resolve({ available: true, suggestions: [] }),
  }
  const router = createMemoryRouter([{ path: '/inscription', element: <RegisterPage /> }], {
    initialEntries: ['/inscription'],
  })
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <SessionApiProvider api={sessionApi}>
        <PasswordBlocklistProvider api={{ load: () => Promise.resolve([]) }}>
          <UsernameAvailabilityApiProvider api={availabilityApi}>
            <OAuthProvidersApiProvider api={{ providers: () => Promise.resolve(providers) }}>
              <RegisterApiProvider api={registerApi}>
                <RouterProvider router={router} />
              </RegisterApiProvider>
            </OAuthProvidersApiProvider>
          </UsernameAvailabilityApiProvider>
        </PasswordBlocklistProvider>
      </SessionApiProvider>
    </QueryClientProvider>,
  )
}

describe('Écran Inscription', () => {
  it('AUTH-10, OPS-03 : titre, sous-titre et lien vers la connexion de la maquette, sans fournisseur', async () => {
    const screen = await renderRegister([])

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

  it('AUTH-30, AUTH-35 : les boutons des fournisseurs configurés, après « Créer mon compte »', async () => {
    const screen = await renderRegister(['github'])

    const github = screen.getByRole('link', { name: 'Continuer avec GitHub' })
    await expect.element(github).toHaveAttribute('href', '/oauth2/authorization/github')
    await expect.element(screen.getByText(/Continuer avec Google/)).not.toBeInTheDocument()
    const submit = screen.getByRole('button', { name: 'Créer mon compte' }).element()
    expect(
      submit.compareDocumentPosition(github.element()) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy()
  })
})
