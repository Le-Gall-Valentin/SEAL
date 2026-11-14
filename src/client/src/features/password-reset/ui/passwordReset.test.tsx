import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ComponentProps } from 'react'
import { createMemoryRouter, useLocation } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { PasswordBlocklistProvider } from '@/entities/password'
import { type ISessionApi, SessionApiProvider } from '@/entities/session'
import { ApiError, NetworkError } from '@/shared/lib'
import { PasswordResetApiProvider } from '../model/passwordResetApiPort'
import { ForgotPasswordForm } from './ForgotPasswordForm'
import { NewPasswordPanel } from './NewPasswordPanel'

type IPasswordResetApi = ComponentProps<typeof PasswordResetApiProvider>['api']

const TOKEN = 'Ab0-_'.repeat(8) + 'xyz'
const PASSWORD = 'raquette-sous-la-neige'
function LoginProbe() {
  const location = useLocation()
  return <p>{`connexion ${JSON.stringify(location.state)}`}</p>
}

async function renderReset(
  api: Partial<IPasswordResetApi>,
  options: { path?: string; token?: string | null } = {},
) {
  const calls: string[] = []
  const resetApi: IPasswordResetApi = {
    request: (email) => {
      calls.push(`request ${email}`)
      return (api.request ?? (() => Promise.resolve()))(email)
    },
    inspect: (token) => {
      calls.push(`inspect ${token}`)
      return (
        api.inspect ??
        (() =>
          Promise.resolve({
            username: 'valentin.dupont',
            email: 'valentin@uqac.ca',
            expiresAt: new Date(Date.now() + 24 * 60_000 - 5_000),
          }))
      )(token)
    },
    confirm: (token, password) => {
      calls.push(`confirm ${token} ${password}`)
      return (api.confirm ?? (() => Promise.resolve()))(token, password)
    },
  }
  const sessionApi: ISessionApi = {
    me: () => Promise.resolve(null),
    reloadCsrf: () => Promise.resolve(),
  }
  const token = options.token === undefined ? TOKEN : options.token
  const router = createMemoryRouter(
    [
      { path: '/mot-de-passe/oublie', element: <ForgotPasswordForm /> },
      { path: '/mot-de-passe/nouveau', element: <NewPasswordPanel token={token} linkId={0} /> },
      { path: '/connexion', element: <LoginProbe /> },
    ],
    {
      initialEntries: [
        options.token === undefined && api.request
          ? '/mot-de-passe/oublie'
          : '/mot-de-passe/nouveau',
      ],
    },
  )
  const screen = await render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <SessionApiProvider api={sessionApi}>
        <PasswordBlocklistProvider api={{ load: () => Promise.resolve(['Mailcreated5240']) }}>
          <PasswordResetApiProvider api={resetApi}>
            <RouterProvider router={router} />
          </PasswordResetApiProvider>
        </PasswordBlocklistProvider>
      </SessionApiProvider>
    </QueryClientProvider>,
  )
  return { screen, calls, router }
}

describe('AUTH-70 : Mot-de-passe-oublie', () => {
  it('AUTH-70 : demande, puis « Vérifiez votre boîte de réception », quel que soit le compte', async () => {
    const { screen, calls } = await renderReset(
      { request: () => Promise.resolve() },
      { path: '/mot-de-passe/oublie' },
    )

    await expect
      .element(screen.getByLabelText('Courriel', { exact: true }))
      .toHaveAttribute('autocomplete', 'email')
    await screen.getByLabelText('Courriel', { exact: true }).fill('valentin@uqac.ca')
    await screen.getByRole('button', { name: 'Envoyer le lien' }).click()

    const status = screen.getByRole('status')
    await expect.element(status).toMatchTextContent('Vérifiez votre boîte de réception')
    await expect
      .element(status)
      .toMatchTextContent(
        'Si un compte correspond à cette adresse, un lien vient de partir. Il est valable 30 minutes et ne sert qu’une fois.',
      )
    expect(calls).toEqual(['request valentin@uqac.ca'])
  })

  it('ADR 0024 : sessions fermées annoncées ; ni clés, ni double authentification, ni OAuth', async () => {
    const { screen } = await renderReset(
      { request: () => Promise.resolve() },
      { path: '/mot-de-passe/oublie' },
    )

    await expect
      .element(screen.getByText('Après la réinitialisation, toutes vos sessions seront fermées.'))
      .toBeVisible()
    await expect.element(screen.getByText(/clé/)).not.toBeInTheDocument()
    await expect.element(screen.getByText(/double authentification/)).not.toBeInTheDocument()
    await expect.element(screen.getByText(/Microsoft/)).not.toBeInTheDocument()
  })

  it('ADR 0024 : adresse mal formée, et 429 « Trop de tentatives »', async () => {
    const { screen } = await renderReset(
      {
        request: () =>
          Promise.reject(new ApiError(429, 'RATE_LIMITED', { retryAfterSeconds: 3600 })),
      },
      { path: '/mot-de-passe/oublie' },
    )
    await screen.getByLabelText('Courriel', { exact: true }).fill('valentin@')
    await screen.getByRole('button', { name: 'Envoyer le lien' }).hover()
    await expect.element(screen.getByRole('button', { name: 'Envoyer le lien' })).toBeDisabled()

    await screen.getByLabelText('Courriel', { exact: true }).fill('valentin@uqac.ca')
    await screen.getByRole('button', { name: 'Envoyer le lien' }).click()
    await expect.element(screen.getByRole('alert')).toMatchTextContent('Réessayez dans 60 minutes.')
  })
})

describe('AUTH-70, ADR 0022 : Nouveau-Mot-de-passe', () => {
  it('ADR 0022 : le lien lu donne l’identifiant et les minutes restantes', async () => {
    const { screen, calls } = await renderReset({})

    await expect
      .element(screen.getByRole('heading', { level: 1, name: 'Nouveau mot de passe' }))
      .toBeVisible()
    const username = screen.getByText('@valentin.dupont', { exact: true })
    await expect.element(username).toBeVisible()
    expect(username.element().parentElement?.textContent).toBe(
      'Pour @valentin.dupont. Ce lien expire dans 24 minutes.',
    )
    expect(calls).toEqual([`inspect ${TOKEN}`])
  })

  it('ADR 0024 : règles sans phrase sur la composition ; sessions fermées, sans double authentification ni clés', async () => {
    const { screen } = await renderReset({})

    await expect
      .element(screen.getByText('Toutes vos sessions seront fermées. Reconnectez-vous ensuite.'))
      .toBeVisible()
    await expect.element(screen.getByText(/composition/)).not.toBeInTheDocument()
    await expect.element(screen.getByText(/divulgués/)).not.toBeInTheDocument()
    await expect.element(screen.getByText(/clé/)).not.toBeInTheDocument()
  })

  it('AUTH-70 : nouveau mot de passe enregistré, retour à la connexion avec la confirmation', async () => {
    const { screen, calls } = await renderReset({})
    await screen.getByLabelText('Nouveau mot de passe', { exact: true }).fill(PASSWORD)
    await screen.getByLabelText('Confirmer le mot de passe', { exact: true }).fill(PASSWORD)
    await expect
      .element(screen.getByRole('meter', { name: 'Robustesse' }))
      .toHaveAttribute('aria-valuetext', 'Bon')
    await screen.getByRole('button', { name: 'Enregistrer le mot de passe' }).click()

    await expect.element(screen.getByText('connexion {"passwordReset":true}')).toBeVisible()
    expect(calls).toEqual([`inspect ${TOKEN}`, `confirm ${TOKEN} ${PASSWORD}`])
  })

  it.each([
    ['lien expiré', { inspect: () => Promise.reject(new ApiError(422, 'CODE_EXPIRED')) }, TOKEN],
    ['sans jeton', {}, null],
  ] as const)('ADR 0024 : %s, « Ce lien n’est plus valable. »', async (_cas, api, token) => {
    const { screen } = await renderReset(api, { token })

    await expect.element(screen.getByText('Ce lien n’est plus valable.')).toBeVisible()
    await expect
      .element(screen.getByRole('link', { name: 'Demander un nouveau lien' }))
      .toHaveAttribute('href', '/mot-de-passe/oublie')
    await expect
      .element(screen.getByLabelText('Nouveau mot de passe', { exact: true }))
      .not.toBeInTheDocument()
  })

  it('ADR 0024 : lien consommé entre-temps, CODE_EXPIRED à la confirmation', async () => {
    const { screen } = await renderReset({
      confirm: () => Promise.reject(new ApiError(422, 'CODE_EXPIRED')),
    })
    await screen.getByLabelText('Nouveau mot de passe', { exact: true }).fill(PASSWORD)
    await screen.getByLabelText('Confirmer le mot de passe', { exact: true }).fill(PASSWORD)
    await screen.getByRole('button', { name: 'Enregistrer le mot de passe' }).click()

    await expect.element(screen.getByText('Ce lien n’est plus valable.')).toBeVisible()
  })

  it('AUTH-11 : mot de passe refusé par le serveur, la règle de la liste de blocage tombe', async () => {
    const { screen } = await renderReset({
      confirm: () => Promise.reject(new ApiError(422, 'PASSWORD_TOO_WEAK')),
    })
    await screen.getByLabelText('Nouveau mot de passe', { exact: true }).fill(PASSWORD)
    await screen.getByLabelText('Confirmer le mot de passe', { exact: true }).fill(PASSWORD)
    await screen.getByRole('button', { name: 'Enregistrer le mot de passe' }).click()

    await expect
      .element(
        screen
          .getByRole('listitem')
          .filter({ hasText: 'Absent des mots de passe les plus courants' }),
      )
      .toHaveAttribute('data-state', 'unmet')
  })

  it('ADR 0024, point 10 : panne de la lecture du lien, « Réessayer » la relance', async () => {
    let attempts = 0
    const { screen, calls } = await renderReset({
      inspect: () => {
        attempts += 1
        return attempts === 1
          ? Promise.reject(new NetworkError())
          : Promise.resolve({
              username: 'valentin.dupont',
              email: 'valentin@uqac.ca',
              expiresAt: new Date(Date.now() + 600_000),
            })
      },
    })

    await expect
      .element(screen.getByRole('alert'))
      .toHaveTextContent('Connexion impossible. Vérifiez votre réseau puis réessayez.')
    await screen.getByRole('button', { name: 'Réessayer' }).click()

    await expect.element(screen.getByText('@valentin.dupont', { exact: true })).toBeVisible()
    expect(calls).toEqual([`inspect ${TOKEN}`, `inspect ${TOKEN}`])
  })

  it('SEC-10 : lecture du lien refusée en 429, « Trop de tentatives »', async () => {
    const { screen } = await renderReset({
      inspect: () => Promise.reject(new ApiError(429, 'RATE_LIMITED', { retryAfterSeconds: 120 })),
    })

    await expect.element(screen.getByRole('alert')).toMatchTextContent('Réessayez dans 2 minutes.')
  })
})
