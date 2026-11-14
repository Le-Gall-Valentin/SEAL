import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createMemoryRouter } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { type IPasswordResetApi, PasswordResetApiProvider } from '@/features/password-reset'
import { ForgotPasswordPage } from './ForgotPasswordPage'

describe('Écran Mot-de-passe-oublie', () => {
  it('AUTH-70 : titre, consigne et retour à la connexion de la maquette', async () => {
    const resetApi: IPasswordResetApi = {
      request: () => Promise.resolve(),
      inspect: () => Promise.reject(new Error('inutile')),
      confirm: () => Promise.resolve(),
    }
    const router = createMemoryRouter(
      [{ path: '/mot-de-passe/oublie', element: <ForgotPasswordPage /> }],
      { initialEntries: ['/mot-de-passe/oublie'] },
    )
    const screen = await render(
      <QueryClientProvider client={new QueryClient()}>
        <PasswordResetApiProvider api={resetApi}>
          <RouterProvider router={router} />
        </PasswordResetApiProvider>
      </QueryClientProvider>,
    )

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
})
