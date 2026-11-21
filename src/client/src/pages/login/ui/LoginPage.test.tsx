import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createMemoryRouter } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { OAuthProvidersApiProvider, type OAuthProviderId } from '@/entities/oauth-provider'
import { type ISessionApi, SessionApiProvider } from '@/entities/session'
import { type ILoginApi, LoginApiProvider } from '@/features/login'
import { ApiError } from '@/shared/lib'
import { LoginPage } from './LoginPage'

interface Entry {
  readonly state?: unknown
  readonly search?: string
}

async function renderLogin(
  entry: Entry = {},
  providers: readonly OAuthProviderId[] = [],
  login: ILoginApi['login'] = () => Promise.resolve(),
) {
  const sessionApi: ISessionApi = {
    me: () => Promise.resolve(null),
    reloadCsrf: () => Promise.resolve(),
  }
  const loginApi: ILoginApi = { login }
  const router = createMemoryRouter([{ path: '/connexion', element: <LoginPage /> }], {
    initialEntries: [{ pathname: '/connexion', search: entry.search ?? '', state: entry.state }],
  })
  const screen = await render(
    <QueryClientProvider client={new QueryClient()}>
      <SessionApiProvider api={sessionApi}>
        <OAuthProvidersApiProvider api={{ providers: () => Promise.resolve(providers) }}>
          <LoginApiProvider api={loginApi}>
            <RouterProvider router={router} />
          </LoginApiProvider>
        </OAuthProvidersApiProvider>
      </SessionApiProvider>
    </QueryClientProvider>,
  )
  return Object.assign(screen, { router })
}

describe('Écran Connexion', () => {
  it('ADR 0024 : après une réinitialisation, « Mot de passe enregistré. Connectez-vous avec le nouveau. »', async () => {
    const screen = await renderLogin({ state: { passwordReset: true } })

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
    const screen = await renderLogin({ state: { passwordReset: 'oui' } })

    await expect.element(screen.getByRole('heading', { level: 1, name: 'Connexion' })).toBeVisible()
    await expect.element(screen.getByText(/Mot de passe enregistré/)).not.toBeInTheDocument()
  })

  it('AUTH-20, OPS-03 : titre, sous-titre et lien vers l’inscription de la maquette, sans fournisseur ni « ou »', async () => {
    const screen = await renderLogin()

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

  it('AUTH-30, AUTH-35 : les fournisseurs configurés, après « Se connecter » et avant « Pas encore de compte ? »', async () => {
    const screen = await renderLogin({}, ['google', 'github'])

    const google = screen.getByRole('link', { name: 'Continuer avec Google' })
    await expect.element(google).toHaveAttribute('href', '/oauth2/authorization/google')
    await expect
      .element(screen.getByRole('link', { name: 'Continuer avec GitHub' }))
      .toHaveAttribute('href', '/oauth2/authorization/github')
    const submit = screen.getByRole('button', { name: 'Se connecter' }).element()
    const createAccount = screen.getByRole('link', { name: 'Créer un compte' }).element()
    expect(
      submit.compareDocumentPosition(google.element()) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy()
    expect(
      google.element().compareDocumentPosition(createAccount) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy()
  })

  it.each([
    [
      'OAUTH_EMAIL_IN_USE',
      'Ce courriel a déjà un compteConnectez-vous avec votre méthode habituelle.',
    ],
    ['RATE_LIMITED', 'Trop de tentativesRéessayez plus tard.'],
    ['REQUEST_REJECTED', 'La connexion n’a pas aboutiRéessayez.'],
  ])(
    'ADR 0033 (point 1) : ?erreur=%s affiche l’alerte, puis quitte l’adresse',
    async (code, text) => {
      const screen = await renderLogin({ search: `?erreur=${code}` })

      await expect.element(screen.getByRole('alert')).toHaveTextContent(text)
      await expect.poll(() => screen.router.state.location.search).toBe('')
      await expect.element(screen.getByRole('alert')).toHaveTextContent(text)
    },
  )

  it('ADR 0033 (point 1) : un code inconnu n’affiche rien, et quitte aussi l’adresse', async () => {
    const screen = await renderLogin({ search: '?erreur=INTERNAL_ERROR' })

    await expect.poll(() => screen.router.state.location.search).toBe('')
    await expect.element(screen.getByRole('heading', { level: 1, name: 'Connexion' })).toBeVisible()
    await expect.element(screen.getByRole('alert')).not.toBeInTheDocument()
  })

  it('ADR 0033 (point 1) : l’alerte du retour OAuth disparaît au premier envoi du formulaire', async () => {
    const screen = await renderLogin({ search: '?erreur=OAUTH_EMAIL_IN_USE' }, [], () =>
      Promise.reject(new ApiError(401, 'INVALID_CREDENTIALS')),
    )
    await expect.element(screen.getByText('Ce courriel a déjà un compte')).toBeVisible()

    await screen.getByLabelText('Identifiant ou courriel').fill('valentin')
    await screen.getByLabelText('Mot de passe', { exact: true }).fill('secret')
    await screen.getByRole('button', { name: 'Se connecter' }).click()

    await expect
      .element(screen.getByRole('alert'))
      .toMatchTextContent('Identifiant ou mot de passe incorrect')
    await expect.element(screen.getByText('Ce courriel a déjà un compte')).not.toBeInTheDocument()
  })

  it('ADR 0024 (point 9), ADR 0033 : confirmation et code ensemble, l’adresse perd les deux', async () => {
    const screen = await renderLogin({
      state: { passwordReset: true },
      search: '?erreur=REQUEST_REJECTED',
    })

    await expect.element(screen.getByRole('alert')).toBeVisible()
    await expect.poll(() => screen.router.state.location.search).toBe('')
    await expect.poll((): unknown => screen.router.state.location.state).toBeNull()
  })
})
