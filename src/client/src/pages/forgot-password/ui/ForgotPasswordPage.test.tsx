import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createMemoryRouter } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { OAuthProvidersApiProvider, type OAuthProviderId } from '@/entities/oauth-provider'
import { type IPasswordResetApi, PasswordResetApiProvider } from '@/features/password-reset'
import { ForgotPasswordPage } from './ForgotPasswordPage'

async function renderForgot(providers: readonly OAuthProviderId[] = []) {
  const resetApi: IPasswordResetApi = {
    request: () => Promise.resolve(),
    inspect: () => Promise.reject(new Error('inutile')),
    confirm: () => Promise.resolve(),
  }
  const router = createMemoryRouter(
    [{ path: '/mot-de-passe/oublie', element: <ForgotPasswordPage /> }],
    { initialEntries: ['/mot-de-passe/oublie'] },
  )
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <OAuthProvidersApiProvider api={{ providers: () => Promise.resolve(providers) }}>
        <PasswordResetApiProvider api={resetApi}>
          <RouterProvider router={router} />
        </PasswordResetApiProvider>
      </OAuthProvidersApiProvider>
    </QueryClientProvider>,
  )
}

describe('Écran Mot-de-passe-oublie', () => {
  it('AUTH-70 : titre, consigne et retour à la connexion de la maquette', async () => {
    const screen = await renderForgot()

    await expect
      .element(screen.getByRole('heading', { level: 1, name: 'Mot de passe oublié' }))
      .toBeVisible()
    await expect
      .element(
        screen.getByText(
          'Indiquez le courriel de votre compte : nous y enverrons un lien pour choisir un nouveau mot de passe.',
        ),
      )
      .toBeVisible()
    await expect
      .element(screen.getByRole('link', { name: 'Retour à la connexion' }))
      .toHaveAttribute('href', '/connexion')
    await expect
      .element(screen.getByRole('img', { name: 'Le phoque SEAL cherche' }).first())
      .toBeInTheDocument()
  })

  it('OPS-03, ADR 0033 (point 3) : sans fournisseur configuré, aucune phrase sur OAuth', async () => {
    const screen = await renderForgot([])

    await expect.element(screen.getByRole('heading', { level: 1 })).toBeVisible()
    await expect.element(screen.getByText(/Vous vous connectez avec/)).not.toBeInTheDocument()
  })

  it.each([
    [
      ['google', 'github'],
      'Vous vous connectez avec Google ou GitHub ? Ces comptes n’ont pas de mot de passe SEAL : utilisez le bouton du fournisseur.',
    ],
    [
      ['google'],
      'Vous vous connectez avec Google ? Ce compte n’a pas de mot de passe SEAL : utilisez le bouton du fournisseur.',
    ],
  ] as const)(
    'ADR 0033 (point 3) : %o, la phrase ne nomme que les fournisseurs configurés',
    async (providers, text) => {
      const screen = await renderForgot(providers)

      await expect.element(screen.getByText(text, { exact: true })).toBeVisible()
    },
  )
})
