import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createMemoryRouter } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { UsernameAvailabilityApiProvider } from '@/entities/account'
import { type ISessionApi, SessionApiProvider } from '@/entities/session'
import {
  type IOAuthRegistrationApi,
  OAuthRegistrationApiProvider,
  type PendingRegistration,
} from '@/features/oauth-registration'
import { NetworkError } from '@/shared/lib'
import { ChooseUsernamePage } from './ChooseUsernamePage'

async function renderPage(pending: () => Promise<PendingRegistration | null>) {
  const sessionApi: ISessionApi = {
    me: () => Promise.resolve(null),
    reloadCsrf: () => Promise.resolve(),
  }
  const registrationApi: IOAuthRegistrationApi = { pending, complete: () => Promise.resolve() }
  const router = createMemoryRouter(
    [
      { path: '/inscription/identifiant', element: <ChooseUsernamePage /> },
      { path: '/connexion', element: <p>Écran de connexion</p> },
    ],
    { initialEntries: ['/inscription/identifiant'] },
  )
  const screen = await render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <SessionApiProvider api={sessionApi}>
        <UsernameAvailabilityApiProvider
          api={{
            usernameAvailability: () => Promise.resolve({ available: true, suggestions: [] }),
          }}
        >
          <OAuthRegistrationApiProvider api={registrationApi}>
            <RouterProvider router={router} />
          </OAuthRegistrationApiProvider>
        </UsernameAvailabilityApiProvider>
      </SessionApiProvider>
    </QueryClientProvider>,
  )
  return Object.assign(screen, { router })
}

describe('Écran Identifiant-OAuth', () => {
  it('AUTH-32 : titre et phrase de la maquette, avec le fournisseur et le courriel de l’attente', async () => {
    const screen = await renderPage(() =>
      Promise.resolve({
        provider: 'github',
        email: 'valentin.dupont@gmail.com',
        displayName: 'Valentin Dupont',
      }),
    )

    await expect
      .element(screen.getByRole('heading', { level: 1, name: 'Choisissez votre identifiant' }))
      .toBeVisible()
    const email = screen.getByText('valentin.dupont@gmail.com', { exact: true })
    expect(email.element().parentElement?.textContent).toBe(
      'Vous arrivez de GitHub avec valentin.dupont@gmail.com. Il ne reste qu’à choisir votre identifiant.',
    )
    await expect.element(screen.getByRole('button', { name: 'Créer mon compte' })).toBeVisible()
  })

  it('AUTH-32, ADR 0033 (point 2) : sans inscription en attente, retour à la connexion avec REQUEST_REJECTED', async () => {
    const screen = await renderPage(() => Promise.resolve(null))

    await expect.element(screen.getByText('Écran de connexion')).toBeVisible()
    expect(screen.router.state.location.search).toBe('?erreur=REQUEST_REJECTED')
  })

  it('AUTH-32, A10 : attente illisible (réseau), « Impossible de charger » et « Réessayer »', async () => {
    let calls = 0
    const screen = await renderPage(() => {
      calls += 1
      return calls === 1
        ? Promise.reject(new NetworkError())
        : Promise.resolve({ provider: 'google', email: 'v@gmail.com', displayName: 'V' })
    })

    await expect
      .element(screen.getByRole('heading', { level: 1, name: 'Impossible de charger' }))
      .toBeVisible()
    await screen.getByRole('button', { name: 'Réessayer' }).click()
    await expect
      .element(screen.getByRole('heading', { level: 1, name: 'Choisissez votre identifiant' }))
      .toBeVisible()
  })
})
