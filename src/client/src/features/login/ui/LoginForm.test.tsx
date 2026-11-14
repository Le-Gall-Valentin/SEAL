import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ComponentProps } from 'react'
import { createMemoryRouter } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import {
  type Account,
  type ISessionApi,
  SessionApiProvider,
  useCurrentAccount,
} from '@/entities/session'
import { ApiError, NetworkError } from '@/shared/lib'
import { LoginApiProvider } from '../model/loginApiPort'
import { LoginForm } from './LoginForm'

const ACTIVE: Account = {
  id: '0192f0c4-7b1a-7cc0-9a51-3f6f8e2d4b10',
  username: 'valentin',
  displayName: 'Valentin',
  email: 'valentin@exemple.ca',
  status: 'ACTIVE',
  verification: null,
}

// Observe le compte courant, comme la garde de route : une connexion réussie le fait relire.
function CurrentAccountProbe() {
  const account = useCurrentAccount()
  return <p>{account.data?.username ?? 'anonyme'}</p>
}

// Le test de ui/ n'importe pas api/ (ADR 0012) : le port se lit sur son fournisseur.
type ILoginApi = ComponentProps<typeof LoginApiProvider>['api']
type Credentials = Parameters<ILoginApi['login']>[0]

interface Harness {
  readonly sent: Credentials[]
  readonly meCalls: () => number
}

async function renderForm(login: (credentials: Credentials) => Promise<void>) {
  const sent: Credentials[] = []
  let meCalls = 0
  const loginApi: ILoginApi = {
    login: (credentials) => {
      sent.push(credentials)
      return login(credentials)
    },
  }
  const sessionApi: ISessionApi = {
    me: () => {
      meCalls += 1
      return Promise.resolve(meCalls > 1 ? ACTIVE : null)
    },
    reloadCsrf: () => Promise.resolve(),
  }
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const router = createMemoryRouter(
    [
      {
        path: '/connexion',
        element: (
          <>
            <CurrentAccountProbe />
            <LoginForm />
          </>
        ),
      },
      { path: '/mot-de-passe/oublie', element: <p>Mot de passe oublié</p> },
    ],
    { initialEntries: ['/connexion'] },
  )
  const screen = await render(
    <QueryClientProvider client={client}>
      <SessionApiProvider api={sessionApi}>
        <LoginApiProvider api={loginApi}>
          <RouterProvider router={router} />
        </LoginApiProvider>
      </SessionApiProvider>
    </QueryClientProvider>,
  )
  const harness: Harness = { sent, meCalls: () => meCalls }
  return { screen, harness }
}

async function fill(screen: Awaited<ReturnType<typeof renderForm>>['screen'], password = 'secret') {
  await screen.getByLabelText('Identifiant ou courriel').fill('valentin')
  await screen.getByLabelText('Mot de passe', { exact: true }).fill(password)
}

describe('AUTH-20 à AUTH-22 : formulaire de connexion', () => {
  it('AUTH-20 : champs de la maquette, avec leur autocomplete', async () => {
    const { screen } = await renderForm(() => Promise.resolve())

    await expect
      .element(screen.getByLabelText('Identifiant ou courriel'))
      .toHaveAttribute('autocomplete', 'username')
    await expect
      .element(screen.getByLabelText('Mot de passe', { exact: true }))
      .toHaveAttribute('autocomplete', 'current-password')
    await expect
      .element(screen.getByRole('link', { name: 'Mot de passe oublié ?' }))
      .toHaveAttribute('href', '/mot-de-passe/oublie')
  })

  it('AUTH-20 : « Se connecter » reste désactivé tant qu’un champ est vide', async () => {
    const { screen } = await renderForm(() => Promise.resolve())

    const submit = screen.getByRole('button', { name: 'Se connecter' })
    await expect.element(submit).toBeDisabled()
    await screen.getByLabelText('Identifiant ou courriel').fill('valentin')
    await expect.element(submit).toBeDisabled()
    await screen.getByLabelText('Mot de passe', { exact: true }).fill('secret')
    await expect.element(submit).toBeEnabled()
  })

  it('AUTH-20, AUTH-16 : une connexion réussie relit le compte courant, une seule fois envoyée', async () => {
    let resolve: () => void = () => undefined
    const { screen, harness } = await renderForm(
      () =>
        new Promise<void>((done) => {
          resolve = done
        }),
    )
    await fill(screen)

    const submit = screen.getByRole('button', { name: 'Se connecter' })
    await submit.click()
    // Pendant l'envoi, un second clic ne renvoie rien.
    await expect.element(submit).toBeDisabled()
    resolve()

    await expect.element(screen.getByText('valentin', { exact: true })).toBeVisible()
    expect(harness.meCalls()).toBe(2)
    expect(harness.sent).toEqual([{ login: 'valentin', password: 'secret' }])
  })

  it('AUTH-21 : INVALID_CREDENTIALS affiche le message de la maquette et vide le mot de passe', async () => {
    const { screen } = await renderForm(() =>
      Promise.reject(new ApiError(401, 'INVALID_CREDENTIALS')),
    )
    await fill(screen)
    await screen.getByRole('button', { name: 'Se connecter' }).click()

    await expect
      .element(screen.getByRole('alert'))
      .toHaveTextContent('Identifiant ou mot de passe incorrect.')
    await expect.element(screen.getByLabelText('Mot de passe', { exact: true })).toHaveValue('')
    await expect.element(screen.getByLabelText('Identifiant ou courriel')).toHaveValue('valentin')
  })

  it('AUTH-22 : 429 affiche « Trop de tentatives » et le délai, désactive le formulaire, puis le rend', async () => {
    const { screen } = await renderForm(() =>
      Promise.reject(new ApiError(429, 'RATE_LIMITED', { retryAfterSeconds: 1 })),
    )
    await fill(screen)
    await screen.getByRole('button', { name: 'Se connecter' }).click()

    const alert = screen.getByRole('alert')
    await expect.element(alert.getByText('Trop de tentatives', { exact: true })).toBeVisible()
    await expect
      .element(alert)
      .toMatchTextContent(
        'Réessayez dans 1 minute. Vous pouvez aussi réinitialiser votre mot de passe.',
      )
    await expect.element(screen.getByLabelText('Identifiant ou courriel')).toBeDisabled()
    await expect.element(screen.getByLabelText('Mot de passe', { exact: true })).toBeDisabled()
    await expect.element(screen.getByRole('button', { name: 'Se connecter' })).toBeDisabled()
    await expect.element(screen.getByRole('link', { name: 'Mot de passe oublié ?' })).toBeVisible()

    // Retry-After écoulé : le formulaire revient, sans l'alerte.
    await expect.element(screen.getByRole('alert'), { timeout: 3000 }).not.toBeInTheDocument()
    await expect.element(screen.getByLabelText('Identifiant ou courriel')).toBeEnabled()
  })

  it('ADR 0015 : le délai s’écrit au pluriel au-delà d’une minute', async () => {
    const { screen } = await renderForm(() =>
      Promise.reject(new ApiError(429, 'RATE_LIMITED', { retryAfterSeconds: 900 })),
    )
    await fill(screen)
    await screen.getByRole('button', { name: 'Se connecter' }).click()

    await expect.element(screen.getByRole('alert')).toMatchTextContent('Réessayez dans 15 minutes.')
  })

  it('ADR 0024, point 8 : serveur injoignable', async () => {
    const { screen } = await renderForm(() => Promise.reject(new NetworkError()))
    await fill(screen)
    await screen.getByRole('button', { name: 'Se connecter' }).click()

    await expect
      .element(screen.getByRole('alert'))
      .toHaveTextContent('Connexion impossible. Vérifiez votre réseau puis réessayez.')
  })
})
