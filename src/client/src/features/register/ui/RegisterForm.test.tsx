import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ComponentProps } from 'react'
import { createMemoryRouter } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { PasswordBlocklistProvider } from '@/entities/password'
import {
  type Account,
  type ISessionApi,
  SessionApiProvider,
  useCurrentAccount,
} from '@/entities/session'
import { ApiError } from '@/shared/lib'
import { RegisterApiProvider } from '../model/registerApiPort'
import { RegisterForm } from './RegisterForm'

// Le test de ui/ n'importe pas api/ (ADR 0012) : le port se lit sur son fournisseur.
type IRegisterApi = ComponentProps<typeof RegisterApiProvider>['api']
type Registration = Parameters<IRegisterApi['register']>[0]

const PENDING: Account = {
  id: '0192f0c4-7b1a-7cc0-9a51-3f6f8e2d4b10',
  username: 'valentin.dupont',
  displayName: 'Valentin Dupont',
  email: 'valentin.dupont@uqac.ca',
  status: 'PENDING_EMAIL',
  verification: { codeExpiresAt: null, resendsRemaining: 3 },
}

const TAKEN = new Set(['valentin.dupont'])
const PASSWORD = 'fjord-tuque-raquette'

function CurrentAccountProbe() {
  const account = useCurrentAccount()
  return <p>{account.data?.status ?? 'anonyme'}</p>
}

async function renderForm(register: (registration: Registration) => Promise<void>) {
  const sent: Registration[] = []
  const checked: string[] = []
  let registered = false
  const registerApi: IRegisterApi = {
    register: async (registration) => {
      sent.push(registration)
      await register(registration)
      registered = true
    },
    usernameAvailability: (username) => {
      checked.push(username)
      return Promise.resolve(
        TAKEN.has(username)
          ? { available: false, suggestions: ['valentin.dupont2', 'valentin_dupont'] }
          : { available: true, suggestions: [] },
      )
    },
  }
  const sessionApi: ISessionApi = {
    me: () => Promise.resolve(registered ? PENDING : null),
    reloadCsrf: () => Promise.resolve(),
  }
  const router = createMemoryRouter(
    [
      {
        path: '/inscription',
        element: (
          <>
            <CurrentAccountProbe />
            <RegisterForm />
          </>
        ),
      },
    ],
    { initialEntries: ['/inscription'] },
  )
  const screen = await render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <SessionApiProvider api={sessionApi}>
        <PasswordBlocklistProvider api={{ load: () => Promise.resolve(['Mailcreated5240']) }}>
          <RegisterApiProvider api={registerApi}>
            <RouterProvider router={router} />
          </RegisterApiProvider>
        </PasswordBlocklistProvider>
      </SessionApiProvider>
    </QueryClientProvider>,
  )
  return { screen, sent, checked }
}

type Screen = Awaited<ReturnType<typeof renderForm>>['screen']

const field = (screen: Screen, label: string) => screen.getByLabelText(label, { exact: true })

async function fillValid(screen: Screen, email = 'valentin@uqac.ca') {
  await field(screen, 'Courriel').fill(email)
  await field(screen, 'Nom affiché').fill('Valentin Dupont')
  await field(screen, 'Mot de passe').fill(PASSWORD)
  await field(screen, 'Confirmer le mot de passe').fill(PASSWORD)
}

describe('AUTH-03 à AUTH-05, AUTH-10, AUTH-11 : formulaire d’inscription', () => {
  it('AUTH-10 : champs de la maquette et leur autocomplete', async () => {
    const { screen } = await renderForm(() => Promise.resolve())

    await expect.element(field(screen, 'Courriel')).toHaveAttribute('autocomplete', 'email')
    await expect.element(field(screen, 'Identifiant')).toHaveAttribute('autocomplete', 'username')
    await expect.element(field(screen, 'Nom affiché')).toHaveAttribute('autocomplete', 'name')
    await expect
      .element(field(screen, 'Mot de passe'))
      .toHaveAttribute('autocomplete', 'new-password')
    await expect
      .element(field(screen, 'Confirmer le mot de passe'))
      .toHaveAttribute('autocomplete', 'new-password')
    await expect
      .element(screen.getByText('Visible par vos amis. Modifiable à tout moment.'))
      .toBeVisible()
  })

  it('AUTH-03 : l’identifiant est annoncé définitif avant la création', async () => {
    const { screen } = await renderForm(() => Promise.resolve())

    await expect.element(screen.getByText('Identifiant définitif.')).toBeVisible()
    await expect
      .element(screen.getByText('Il ne pourra plus être modifié après la création du compte.'))
      .toBeVisible()
  })

  it('AUTH-04, AUTH-05 : identifiant proposé à partir du courriel, puis disponible', async () => {
    const { screen, checked } = await renderForm(() => Promise.resolve())

    await field(screen, 'Courriel').fill('Valentin.Dupont+seal@uqac.ca')

    await expect.element(field(screen, 'Identifiant')).toHaveValue('valentin.dupont')
    await expect.element(screen.getByText('Déjà pris.')).toBeVisible()
    await field(screen, 'Identifiant').fill('valentin.d')
    await expect.element(screen.getByText('Disponible', { exact: true })).toBeVisible()
    // Une requête par pause de la saisie, pas une par touche.
    expect(checked).not.toContain('valentin.')
  })

  it('AUTH-04 : « Proposé à partir de votre courriel » tant que l’identifiant suit le courriel', async () => {
    const { screen } = await renderForm(() => Promise.resolve())

    await field(screen, 'Courriel').fill('noah@uqac.ca')

    await expect.element(screen.getByText('Disponible', { exact: true })).toBeVisible()
    await expect.element(screen.getByText('· Proposé à partir de votre courriel.')).toBeVisible()
    await expect.element(screen.getByRole('img', { name: 'Signature vérifiée' })).toBeVisible()
  })

  it('AUTH-05 : identifiant pris, « Déjà pris. » et suggestions à choisir', async () => {
    const { screen } = await renderForm(() => Promise.resolve())
    await field(screen, 'Identifiant').fill('valentin.dupont')

    await expect.element(screen.getByText('Déjà pris.')).toBeVisible()
    await expect.element(screen.getByText('Suggestions :')).toBeVisible()
    await screen.getByRole('button', { name: 'valentin_dupont' }).click()

    await expect.element(field(screen, 'Identifiant')).toHaveValue('valentin_dupont')
    await expect.element(screen.getByText('Disponible', { exact: true })).toBeVisible()
  })

  it.each([
    ['Va', '3 à 20 caractères : minuscules, chiffres, et « . » ou « _ » entre deux.'],
    ['admin', 'Réservé.'],
  ])(
    'AUTH-01, AUTH-02 : « %s » affiche « %s » sans interroger le serveur',
    async (username, text) => {
      const { screen, checked } = await renderForm(() => Promise.resolve())
      await field(screen, 'Identifiant').fill(username)

      await expect.element(screen.getByText(text)).toBeVisible()
      expect(checked).not.toContain(username)
    },
  )

  it('AUTH-11 : règles et jauge en direct', async () => {
    const { screen } = await renderForm(() => Promise.resolve())

    await expect
      .element(screen.getByRole('meter', { name: 'Robustesse' }))
      .toHaveAttribute('aria-valuetext', 'Insuffisant')
    await field(screen, 'Mot de passe').fill(PASSWORD)

    await expect
      .element(screen.getByRole('meter', { name: 'Robustesse' }))
      .toHaveAttribute('aria-valuetext', 'Bon')
    await expect
      .element(
        screen
          .getByRole('listitem')
          .filter({ hasText: 'Absent des mots de passe les plus courants' }),
      )
      .toHaveAttribute('data-state', 'met')
  })

  it('ADR 0024 : confirmation différente signalée, envoi impossible', async () => {
    const { screen } = await renderForm(() => Promise.resolve())
    await fillValid(screen)
    await field(screen, 'Confirmer le mot de passe').fill(`${PASSWORD}!`)

    await expect.element(screen.getByText('Les mots de passe ne correspondent pas.')).toBeVisible()
    await expect.element(screen.getByRole('button', { name: 'Créer mon compte' })).toBeDisabled()
  })

  it('ADR 0024 : courriel mal formé signalé en quittant le champ', async () => {
    const { screen } = await renderForm(() => Promise.resolve())
    await field(screen, 'Courriel').fill('valentin@')
    await field(screen, 'Nom affiché').click()

    await expect.element(screen.getByText('Adresse invalide.')).toBeVisible()
  })

  it('AUTH-10, AUTH-16 : création, puis le compte en attente est relu', async () => {
    const { screen, sent } = await renderForm(() => Promise.resolve())
    await fillValid(screen)

    const submit = screen.getByRole('button', { name: 'Créer mon compte' })
    await expect.element(submit).toBeEnabled()
    await submit.click()

    await expect.element(screen.getByText('PENDING_EMAIL')).toBeVisible()
    expect(sent).toEqual([
      {
        email: 'valentin@uqac.ca',
        username: 'valentin',
        displayName: 'Valentin Dupont',
        password: PASSWORD,
      },
    ])
  })

  it('AUTH-05 : identifiant pris entre-temps, « Déjà pris. » au retour du serveur', async () => {
    const { screen } = await renderForm(() => {
      TAKEN.add('valentin')
      return Promise.reject(new ApiError(409, 'USERNAME_TAKEN'))
    })
    await fillValid(screen)
    await screen.getByRole('button', { name: 'Créer mon compte' }).click()

    await expect.element(screen.getByText('Déjà pris.')).toBeVisible()
    TAKEN.delete('valentin')
  })

  it('SEC-10 : 429, « Trop de tentatives » et le délai', async () => {
    const { screen } = await renderForm(() =>
      Promise.reject(new ApiError(429, 'RATE_LIMITED', { retryAfterSeconds: 61 })),
    )
    await fillValid(screen)
    await screen.getByRole('button', { name: 'Créer mon compte' }).click()

    await expect.element(screen.getByRole('alert')).toMatchTextContent('Réessayez dans 2 minutes.')
    await expect.element(screen.getByRole('button', { name: 'Créer mon compte' })).toBeDisabled()
  })

  it('SEC-01 : courriel refusé par le serveur, « Adresse invalide. » sous le champ', async () => {
    const { screen } = await renderForm(() =>
      Promise.reject(new ApiError(400, 'VALIDATION_FAILED', { fields: ['email'] })),
    )
    await fillValid(screen)
    await screen.getByRole('button', { name: 'Créer mon compte' }).click()

    await expect.element(screen.getByText('Adresse invalide.')).toBeVisible()
  })
})
