import { QueryClientProvider } from '@tanstack/react-query'
import { createMemoryRouter, type InitialEntry, type RouteObject } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { KeyStoreProvider } from '@/entities/keyring'
import type { Account } from '@/entities/session'
import { ApiProviders, type Apis, createQueryClient } from '../providers'
import { freshKeyStore, servingAuthority } from '../testing/authority'
import { routes, withRootErrorBoundary } from './routes'

// KEY-02, KEY-33 : ce test porte sur le routage ; le trousseau de l'appareil est complet (routeGuards.test.tsx éprouve
// les autres états des clés).
vi.mock('@/entities/keyring', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/entities/keyring')>()),
  useKeyStatus: () => ({ status: 'ready', failed: false, waiting: false, reread: () => undefined }),
}))

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

// Appels de GET /api/v1/auth/oauth/pending : une session ouverte n'en fait aucun (AUTH-16).
const pendingCalls: string[] = []

// Doublures de tous les ports, comme la racine de composition les assemble ; seul /me varie.
function fakeApis(account: Account | null): Apis {
  const never = () => Promise.reject(new Error('non attendu'))
  return {
    session: { me: () => Promise.resolve(account), reloadCsrf: () => Promise.resolve() },
    authority: servingAuthority(),
    keys: {
      createChallenge: never,
      publishIdentity: never,
      resetIdentity: never,
      publishSubkey: never,
      readVault: never,
      saveVault: never,
      readOwnKeys: never,
    },
    passwordBlocklist: { load: () => Promise.resolve([]) },
    login: { login: never },
    usernameAvailability: { usernameAvailability: never },
    oauthProviders: { providers: () => Promise.resolve([]) },
    register: { register: never },
    oauthRegistration: {
      pending: () => {
        pendingCalls.push(account?.status ?? 'anonyme')
        return Promise.resolve(null)
      },
      complete: never,
    },
    verifyEmail: { verify: never, resend: never, changeEmail: never },
    passwordReset: { request: never, inspect: never, confirm: never },
    logout: { logout: () => Promise.resolve() },
    loginMethods: {
      loginMethods: () => Promise.resolve({ passwordSet: true, providers: [] }),
    },
    reauth: { reauthenticate: never },
    profile: { changeDisplayName: never },
    passwordChange: { changePassword: never },
    emailChange: { request: never, confirm: never },
    friends: {
      friends: () => Promise.resolve({ items: [], next: null }),
      receivedRequests: () => Promise.resolve({ items: [], next: null }),
      sentRequests: () => Promise.resolve({ items: [], next: null }),
    },
    sendFriendRequest: { send: never },
    answerFriendRequest: { accept: never, decline: never, revoke: never },
    removeFriend: { unfriend: never },
    conversations: { list: never, get: never },
    startDirect: { openDirect: never },
    createGroup: { createGroup: never },
    addMembers: { admit: never },
    renameGroup: { rename: never },
    removeMember: { exclude: never },
    leaveGroup: { leave: never },
  }
}

async function renderAt(
  entry: InitialEntry,
  tree: RouteObject[] = routes,
  account: Account | null = ACTIVE,
  apis: Apis = fakeApis(account),
) {
  const router = createMemoryRouter(tree, { initialEntries: [entry] })
  const store = await freshKeyStore()
  const screen = await render(
    <QueryClientProvider client={createQueryClient()}>
      <ApiProviders apis={apis}>
        <KeyStoreProvider api={store}>
          <RouterProvider router={router} />
        </KeyStoreProvider>
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
    pendingCalls.length = 0
    vi.restoreAllMocks()
  })

  it('CNV-04, ADR 0041 : la racine, réservée au compte actif, affiche l’écran Conversations', async () => {
    const { screen } = await renderAt('/')

    await expect
      .element(screen.getByRole('heading', { level: 1, name: 'Conversations' }))
      .toBeVisible()
  })

  it('ADR 0035 : /parametres, réservée au compte actif, affiche Paramètres dans la navigation principale', async () => {
    const { screen } = await renderAt('/parametres')

    await expect
      .element(screen.getByRole('heading', { level: 1, name: 'Paramètres' }))
      .toBeVisible()
    await expect
      .element(screen.getByRole('navigation', { name: 'Navigation principale' }))
      .toBeVisible()
  })

  it('ADR 0012 : une adresse inconnue ramène à la racine, sans écran nouveau', async () => {
    const { router, screen } = await renderAt('/adresse/inconnue')

    await expect
      .element(screen.getByRole('heading', { level: 1, name: 'Conversations' }))
      .toBeVisible()
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
    // AUTH-32, ADR 0033 (point 2) : sans inscription en attente, Identifiant-OAuth ramène à la connexion.
    ['/inscription/identifiant', null, '/connexion', 'Connexion'],
    ['/inscription/identifiant', ACTIVE, '/', null],
    ['/inscription/identifiant', PENDING, '/verification-courriel', 'Vérifiez votre courriel'],
    // ADR 0035 : Paramètres, réservé au compte actif.
    ['/parametres', null, '/connexion', 'Connexion'],
    ['/parametres', PENDING, '/verification-courriel', 'Vérifiez votre courriel'],
    // ADR 0038 (point 5) : Amis, réservé au compte actif.
    ['/amis', null, '/connexion', 'Connexion'],
    ['/amis', PENDING, '/verification-courriel', 'Vérifiez votre courriel'],
  ] as const)(
    'ADR 0024, point 5 : %s, compte %o → %s',
    async (path, account, expected, heading) => {
      const { router, screen } = await renderAt(path, routes, account)

      // ADR 0041 : l'écran du compte actif est Conversations.
      await expect
        .element(screen.getByRole('heading', { level: 1, name: heading ?? 'Conversations' }))
        .toBeVisible()
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

  it.each([ACTIVE, PENDING])(
    'AUTH-16, AUTH-32 : une session ouverte (%o) ne lit jamais l’inscription OAuth en attente',
    async (account) => {
      const { router } = await renderAt('/inscription/identifiant', routes, account)

      await expect.poll(() => router.state.location.pathname).not.toBe('/inscription/identifiant')
      expect(pendingCalls).toEqual([])
    },
  )

  it('AUTH-32, ADR 0032 (point 1) : « Créer mon compte » pour un courriel non garanti mène à la vérification', async () => {
    let completed = false
    const apis: Apis = {
      ...fakeApis(null),
      session: {
        me: () => Promise.resolve(completed ? PENDING : null),
        reloadCsrf: () => Promise.resolve(),
      },
      usernameAvailability: {
        usernameAvailability: () => Promise.resolve({ available: true, suggestions: [] }),
      },
      oauthRegistration: {
        pending: () =>
          Promise.resolve({
            provider: 'github',
            email: 'valentin@uqac.ca',
            displayName: 'Valentin',
          }),
        complete: () => {
          completed = true
          return Promise.resolve()
        },
      },
    }
    const { router, screen } = await renderAt('/inscription/identifiant', routes, null, apis)

    const submit = screen.getByRole('button', { name: 'Créer mon compte' })
    await expect.element(submit).toBeEnabled()
    await submit.click()

    await expect
      .element(screen.getByRole('heading', { level: 1, name: 'Vérifiez votre courriel' }))
      .toBeVisible()
    expect(router.state.location.pathname).toBe('/verification-courriel')
  })
})
