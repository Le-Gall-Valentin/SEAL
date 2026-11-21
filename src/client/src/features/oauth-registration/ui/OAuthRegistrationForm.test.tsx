import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ComponentProps } from 'react'
import { createMemoryRouter, useLocation } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { UsernameAvailabilityApiProvider } from '@/entities/account'
import {
  type Account,
  type ISessionApi,
  SessionApiProvider,
  useCurrentAccount,
} from '@/entities/session'
import { ApiError } from '@/shared/lib'
import type { PendingRegistration } from '../model/oauthRegistration'
import { OAuthRegistrationApiProvider } from '../model/oauthRegistrationApiPort'
import { OAuthRegistrationForm } from './OAuthRegistrationForm'
import { userEvent } from 'vitest/browser'

// Le test de ui/ n'importe pas api/ (ADR 0012) : le port se lit sur son fournisseur.
type IOAuthRegistrationApi = ComponentProps<typeof OAuthRegistrationApiProvider>['api']
type Completion = Parameters<IOAuthRegistrationApi['complete']>[0]

const PENDING: PendingRegistration = {
  provider: 'google',
  email: 'valentin.dupont@gmail.com',
  displayName: 'Valentin Dupont',
}

const ACTIVE: Account = {
  id: '0192f0c4-7b1a-7cc0-9a51-3f6f8e2d4b10',
  username: 'valentin.dupont',
  displayName: 'Valentin Dupont',
  email: 'valentin.dupont@gmail.com',
  status: 'ACTIVE',
  verification: null,
}

const TAKEN = new Set(['pris'])

function Probe() {
  const account = useCurrentAccount()
  const location = useLocation()
  return (
    <>
      <p data-testid="statut">{account.data?.status ?? 'anonyme'}</p>
      <p data-testid="adresse">{`${location.pathname}${location.search}`}</p>
    </>
  )
}

async function renderForm(
  complete: (completion: Completion) => Promise<void>,
  pending: PendingRegistration = PENDING,
) {
  const sent: Completion[] = []
  let completed = false
  const registrationApi: IOAuthRegistrationApi = {
    pending: () => Promise.resolve(pending),
    complete: async (completion) => {
      sent.push(completion)
      await complete(completion)
      completed = true
    },
  }
  const availabilityApi = {
    usernameAvailability: (username: string) =>
      Promise.resolve(
        TAKEN.has(username)
          ? { available: false, suggestions: [`${username}2`] }
          : { available: true, suggestions: [] },
      ),
  }
  const sessionApi: ISessionApi = {
    me: () => Promise.resolve(completed ? ACTIVE : null),
    reloadCsrf: () => Promise.resolve(),
  }
  const router = createMemoryRouter(
    [
      {
        path: '*',
        element: (
          <>
            <Probe />
            <OAuthRegistrationForm pending={pending} />
          </>
        ),
      },
    ],
    { initialEntries: ['/inscription/identifiant'] },
  )
  const screen = await render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <SessionApiProvider api={sessionApi}>
        <UsernameAvailabilityApiProvider api={availabilityApi}>
          <OAuthRegistrationApiProvider api={registrationApi}>
            <RouterProvider router={router} />
          </OAuthRegistrationApiProvider>
        </UsernameAvailabilityApiProvider>
      </SessionApiProvider>
    </QueryClientProvider>,
  )
  return { screen, sent }
}

const field = (screen: Awaited<ReturnType<typeof renderForm>>['screen'], label: string) =>
  screen.getByLabelText(label, { exact: true })

describe('Identifiant-OAuth : formulaire', () => {
  it('AUTH-04, AUTH-32 : identifiant proposé depuis le courriel, nom pré-rempli, avertissement « définitif »', async () => {
    const { screen } = await renderForm(() => Promise.resolve())

    await expect.element(field(screen, 'Identifiant')).toHaveValue('valentin.dupont')
    await expect.element(screen.getByText('Disponible')).toBeVisible()
    await expect.element(field(screen, 'Nom affiché')).toHaveValue('Valentin Dupont')
    await expect
      .element(screen.getByText('Visible par vos amis. Modifiable à tout moment.'))
      .toBeVisible()
    await expect.element(screen.getByText('Identifiant définitif.')).toBeVisible()
    await expect.element(screen.getByRole('button', { name: 'Créer mon compte' })).toBeEnabled()
  })

  it('AUTH-32 : « Créer mon compte » envoie les deux champs, puis relit le compte', async () => {
    const { screen, sent } = await renderForm(() => Promise.resolve())

    await screen.getByRole('button', { name: 'Créer mon compte' }).click()

    await expect.element(screen.getByTestId('statut')).toHaveTextContent('ACTIVE')
    expect(sent).toEqual([{ username: 'valentin.dupont', displayName: 'Valentin Dupont' }])
  })

  it('ACC-03 : sans nom proposé, l’envoi attend la saisie du nom', async () => {
    const { screen } = await renderForm(() => Promise.resolve(), { ...PENDING, displayName: '' })

    const submit = screen.getByRole('button', { name: 'Créer mon compte' })
    await expect.element(submit).toBeDisabled()
    await field(screen, 'Nom affiché').fill('Valentin')
    await expect.element(submit).toBeEnabled()
  })

  it('AUTH-05 : un identifiant pris au retour du serveur s’affiche « Déjà pris. »', async () => {
    const { screen } = await renderForm(() => Promise.reject(new ApiError(409, 'USERNAME_TAKEN')))

    await screen.getByRole('button', { name: 'Créer mon compte' }).click()

    await expect.element(screen.getByText('Déjà pris.')).toBeVisible()
    await expect.element(screen.getByRole('button', { name: 'Créer mon compte' })).toBeDisabled()
  })

  it('AUTH-05 : un identifiant pris propose les suggestions du serveur', async () => {
    const { screen } = await renderForm(() => Promise.resolve())

    await field(screen, 'Identifiant').fill('pris')

    await expect.element(screen.getByText('Déjà pris.')).toBeVisible()
    await screen.getByRole('button', { name: 'pris2' }).click()
    await expect.element(field(screen, 'Identifiant')).toHaveValue('pris2')
    // WCAG 2.4.3 : la suggestion choisie disparaît ; le focus revient au champ, pas à la page.
    await expect.element(field(screen, 'Identifiant')).toHaveFocus()
  })

  it.each([
    [new ApiError(404, 'NOT_FOUND'), '/connexion?erreur=REQUEST_REJECTED'],
    [new ApiError(409, 'OAUTH_ALREADY_LINKED'), '/connexion?erreur=REQUEST_REJECTED'],
    [new ApiError(409, 'OAUTH_EMAIL_IN_USE'), '/connexion?erreur=OAUTH_EMAIL_IN_USE'],
  ])('ADR 0033 (point 2) : %o renvoie à la connexion', async (error, destination) => {
    const { screen } = await renderForm(() => Promise.reject(error))

    await screen.getByRole('button', { name: 'Créer mon compte' }).click()

    await expect.element(screen.getByTestId('adresse')).toHaveTextContent(destination)
    await expect.element(screen.getByTestId('statut')).toHaveTextContent('anonyme')
  })

  it('SEC-10 : une limite atteinte s’affiche en alerte et bloque l’envoi', async () => {
    const { screen } = await renderForm(() =>
      Promise.reject(new ApiError(429, 'RATE_LIMITED', { retryAfterSeconds: 600 })),
    )

    await screen.getByRole('button', { name: 'Créer mon compte' }).click()

    await expect.element(screen.getByRole('alert').getByText('Trop de tentatives')).toBeVisible()
    await expect.element(screen.getByRole('button', { name: 'Créer mon compte' })).toBeDisabled()
  })

  it('ADR 0033 (point 6) : un double clic sur « Créer mon compte » n’envoie qu’une requête', async () => {
    const { screen, sent } = await renderForm(() => new Promise<void>(() => undefined))

    const submit = screen.getByRole('button', { name: 'Créer mon compte' })
    await expect.element(submit).toBeEnabled()
    await userEvent.dblClick(submit)

    await expect.poll(() => sent.length).toBe(1)
    await new Promise((resolve) => setTimeout(resolve, 50))
    expect(sent).toHaveLength(1)
  })
})
