import { QueryClientProvider } from '@tanstack/react-query'
import { createMemoryRouter, type InitialEntry, type RouteObject } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import type { Account } from '@/entities/session'
import { ApiProviders, type Apis, createQueryClient } from '../providers'
import { routes, withRootErrorBoundary } from './routes'

const ACTIVE: Account = {
  id: '0192f0c4-7b1a-7cc0-9a51-3f6f8e2d4b10',
  username: 'valentin',
  displayName: 'Valentin',
  email: 'valentin@uqac.ca',
  status: 'ACTIVE',
  verification: null,
}
const PENDING: Account = {
  ...ACTIVE,
  status: 'PENDING_EMAIL',
  verification: { codeExpiresAt: null, resendsRemaining: 3 },
}

// Doublures de tous les ports, comme la racine de composition les assemble ; seul /me varie.
function fakeApis(account: Account | null): Apis {
  const never = () => Promise.reject(new Error('non attendu'))
  return {
    session: { me: () => Promise.resolve(account), reloadCsrf: () => Promise.resolve() },
    passwordBlocklist: { load: () => Promise.resolve([]) },
    login: { login: never },
    register: { register: never, usernameAvailability: never },
    verifyEmail: { verify: never, resend: never, changeEmail: never },
    passwordReset: { request: never, inspect: never, confirm: never },
    logout: { logout: () => Promise.resolve() },
  }
}

async function renderAt(
  entry: InitialEntry,
  tree: RouteObject[] = routes,
  account: Account | null = ACTIVE,
) {
  const router = createMemoryRouter(tree, { initialEntries: [entry] })
  const screen = await render(
    <QueryClientProvider client={createQueryClient()}>
      <ApiProviders apis={fakeApis(account)}>
        <RouterProvider router={router} />
      </ApiProviders>
    </QueryClientProvider>,
  )
  return { router, screen }
}

// Une route dont le rendu échoue tant que `broken` est vrai, avec une pile qui ne doit jamais s'afficher.
let broken = true
function FlakyPage() {
  if (broken) {
    throw new Error('boom at secretFunction (secret.ts:1:1)')
  }
  return <p>Rétabli</p>
}

describe('ADR 0012 : routeur de la couche app', () => {
  afterEach(() => {
    broken = true
    vi.restoreAllMocks()
  })

  it('ADR 0024, point 2 : la racine, réservée au compte actif, affiche l’accueil provisoire', async () => {
    const { screen } = await renderAt('/')

    await expect.element(screen.getByRole('heading', { level: 1 })).toHaveTextContent('seal')
    await expect.element(screen.getByRole('button', { name: 'Se déconnecter' })).toBeVisible()
  })

  it('ADR 0012 : une adresse inconnue ramène à la racine, sans écran nouveau', async () => {
    const { router, screen } = await renderAt('/adresse/inconnue')

    await expect.element(screen.getByRole('button', { name: 'Se déconnecter' })).toBeVisible()
    expect(router.state.location.pathname).toBe('/')
  })

  it.each([
    ['/', null, '/connexion', 'Connexion'],
    ['/connexion', null, '/connexion', 'Connexion'],
    ['/inscription', null, '/inscription', 'Créer un compte'],
    ['/mot-de-passe/oublie', null, '/mot-de-passe/oublie', 'Mot de passe oublié'],
    ['/mot-de-passe/nouveau', null, '/mot-de-passe/nouveau', 'Nouveau mot de passe'],
    ['/', PENDING, '/verification-courriel', 'Vérifiez votre courriel'],
    ['/inscription', ACTIVE, '/', null],
    ['/verification-courriel', ACTIVE, '/', null],
  ] as const)(
    'ADR 0024, point 5 : %s, compte %o → %s',
    async (path, account, expected, heading) => {
      const { router, screen } = await renderAt(path, routes, account)

      await (heading === null
        ? expect.element(screen.getByRole('button', { name: 'Se déconnecter' })).toBeVisible()
        : expect.element(screen.getByRole('heading', { level: 1, name: heading })).toBeVisible())
      expect(router.state.location.pathname).toBe(expected)
    },
  )

  it("A10 : une erreur de rendu d'une route affiche l'état « Erreur de chargement », sans pile ni texte anglais", async () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined)

    const { screen } = await renderAt(
      '/',
      withRootErrorBoundary([{ path: '/', Component: FlakyPage }]),
    )

    await expect
      .element(screen.getByRole('heading', { level: 1, name: 'Impossible de charger' }))
      .toBeVisible()
    await expect.element(screen.getByRole('button', { name: 'Réessayer' })).toBeVisible()
    const text = document.body.textContent
    expect(text).not.toMatch(/boom|secret|Unexpected|Error|developer/)
  })

  it('A10 : « Réessayer » refait le rendu de la route', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined)
    const { screen } = await renderAt(
      '/',
      withRootErrorBoundary([{ path: '/', Component: FlakyPage }]),
    )
    await expect.element(screen.getByRole('button', { name: 'Réessayer' })).toBeVisible()

    broken = false
    await screen.getByRole('button', { name: 'Réessayer' }).click()

    await expect.element(screen.getByText('Rétabli')).toBeVisible()
  })

  it("A10 : « Réessayer » remplace l'entrée d'historique et garde la requête, l'ancre et l'état", async () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined)
    const { router, screen } = await renderAt(
      { pathname: '/', search: '?q=1', hash: '#h', state: { s: 1 } },
      withRootErrorBoundary([{ path: '/', Component: FlakyPage }]),
    )
    await expect.element(screen.getByRole('button', { name: 'Réessayer' })).toBeVisible()

    broken = false
    await screen.getByRole('button', { name: 'Réessayer' }).click()

    await expect.element(screen.getByText('Rétabli')).toBeVisible()
    expect(router.state.historyAction).toBe('REPLACE')
    expect(router.state.location).toMatchObject({
      pathname: '/',
      search: '?q=1',
      hash: '#h',
      state: { s: 1 },
    })
  })

  it("A10 : si l'adresse courante est refusée par le routeur, « Réessayer » ramène à l'accueil", async () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined)
    const { router, screen } = await renderAt(
      '//evil.example/x',
      withRootErrorBoundary([{ path: '*', Component: FlakyPage }]),
    )
    await expect.element(screen.getByRole('button', { name: 'Réessayer' })).toBeVisible()

    broken = false
    await screen.getByRole('button', { name: 'Réessayer' }).click()

    await expect.element(screen.getByText('Rétabli')).toBeVisible()
    expect(router.state.location.pathname).toBe('/')
  })
})
