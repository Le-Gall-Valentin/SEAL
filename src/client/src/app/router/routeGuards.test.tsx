import { QueryClientProvider, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { createMemoryRouter, type RouteObject } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import {
  AuthorityApiProvider,
  type AuthorityView,
  type IAuthorityApi,
  KeyStoreProvider,
} from '@/entities/authority'
import {
  type Account,
  type ISessionApi,
  SESSION_QUERY_KEY,
  SessionApiProvider,
} from '@/entities/session'
import { LogoutApiProvider } from '@/features/logout'
import type { KeyStore } from '@/shared/crypto'
import { ApiError, NetworkError } from '@/shared/lib'
import { createQueryClient } from '../providers'
import {
  freshKeyStore,
  OTHER_AUTHORITY,
  servingAuthority,
  VECTOR_AUTHORITY,
} from '../testing/authority'
import { ActiveRoute, AnonymousRoute, PendingRoute } from './routeGuards'

const ACTIVE: Account = {
  id: '0192f0c4-7b1a-7cc0-9a51-3f6f8e2d4b10',
  username: 'valentin',
  displayName: 'Valentin',
  email: 'valentin@exemple.ca',
  status: 'ACTIVE',
  verification: null,
}
const PENDING: Account = {
  ...ACTIVE,
  status: 'PENDING_EMAIL',
  verification: { codeExpiresAt: null, resendsRemaining: 3 },
}

// Une page qui découvre en cours de route que la session n'existe plus (expirée, révoquée) : /me répond alors 401.
let expired = false
function ExpiringPage() {
  const query = useQuery({
    queryKey: ['donnees'],
    queryFn: () => {
      expired = true
      return Promise.reject(new ApiError(401, 'UNAUTHENTICATED'))
    },
  })
  return <p>{query.isError ? 'Session perdue' : 'Données'}</p>
}

// Un formulaire en cours de saisie, et une relecture de /me (comme au retour du focus) qu'on déclenche à la demande.
function TypingPage() {
  const client = useQueryClient()
  const [value, setValue] = useState('')
  return (
    <>
      <input
        aria-label="saisie"
        value={value}
        onChange={(event) => {
          setValue(event.target.value)
        }}
      />
      <button
        type="button"
        onClick={() => {
          void client.refetchQueries({ queryKey: SESSION_QUERY_KEY })
        }}
      >
        relire
      </button>
    </>
  )
}

const TREE: RouteObject[] = [
  {
    element: <AnonymousRoute />,
    children: [{ path: '/connexion', element: <p>Écran de connexion</p> }],
  },
  {
    element: <PendingRoute />,
    children: [{ path: '/verification-courriel', element: <p>Écran de vérification</p> }],
  },
  // Hors de toute garde, comme /reinitialisation : on peut sortir de la zone du compte actif et y revenir.
  { path: '/libre', element: <p>Libre</p> },
  {
    element: <ActiveRoute />,
    children: [
      { path: '/', element: <p>Accueil</p> },
      { path: '/expire', element: <ExpiringPage /> },
      { path: '/saisie', element: <TypingPage /> },
    ],
  },
]

interface AuthorityDoubles {
  readonly authority?: IAuthorityApi
  readonly store?: KeyStore
}

async function renderAt(
  path: string,
  me: () => Promise<Account | null>,
  { authority = servingAuthority(), store }: AuthorityDoubles = {},
) {
  const api: ISessionApi = { me, reloadCsrf: () => Promise.resolve() }
  const keyStore = store ?? (await freshKeyStore())
  const router = createMemoryRouter(TREE, { initialEntries: [path] })
  const screen = await render(
    <QueryClientProvider client={createQueryClient()}>
      <SessionApiProvider api={api}>
        <AuthorityApiProvider api={authority}>
          <KeyStoreProvider api={keyStore}>
            <LogoutApiProvider api={{ logout: () => Promise.resolve() }}>
              <RouterProvider router={router} />
            </LogoutApiProvider>
          </KeyStoreProvider>
        </AuthorityApiProvider>
      </SessionApiProvider>
    </QueryClientProvider>,
  )
  return { router, screen }
}

describe('AUTH-16 : gardes de route selon le compte courant', () => {
  it.each([
    ['/', null, '/connexion', 'Écran de connexion'],
    ['/', PENDING, '/verification-courriel', 'Écran de vérification'],
    ['/', ACTIVE, '/', 'Accueil'],
    ['/connexion', null, '/connexion', 'Écran de connexion'],
    ['/connexion', PENDING, '/verification-courriel', 'Écran de vérification'],
    ['/connexion', ACTIVE, '/', 'Accueil'],
    ['/verification-courriel', null, '/connexion', 'Écran de connexion'],
    ['/verification-courriel', PENDING, '/verification-courriel', 'Écran de vérification'],
    ['/verification-courriel', ACTIVE, '/', 'Accueil'],
  ])('AUTH-16 : %s, compte %o → %s', async (path, account, expected, text) => {
    const { router, screen } = await renderAt(path, () => Promise.resolve(account))

    await expect.element(screen.getByText(text)).toBeVisible()
    expect(router.state.location.pathname).toBe(expected)
  })

  it('A10 : tant que /me n’a pas répondu, aucun écran ne s’affiche', async () => {
    const { screen } = await renderAt('/', () => new Promise<Account | null>(() => undefined))

    await expect.element(screen.getByText('Accueil')).not.toBeInTheDocument()
    await expect.element(screen.getByText('Écran de connexion')).not.toBeInTheDocument()
  })

  it('AUTH-53, ADR 0023 : un 401 UNAUTHENTICATED en cours de route ramène à la connexion', async () => {
    expired = false
    const { router, screen } = await renderAt('/expire', () =>
      Promise.resolve(expired ? null : ACTIVE),
    )

    await expect.element(screen.getByText('Écran de connexion')).toBeVisible()
    expect(router.state.location.pathname).toBe('/connexion')
  })

  it('A10 : /me injoignable affiche « Impossible de charger », et « Réessayer » relit le compte', async () => {
    let calls = 0
    const { screen } = await renderAt('/', () => {
      calls += 1
      return calls === 1 ? Promise.reject(new NetworkError()) : Promise.resolve(ACTIVE)
    })

    await expect
      .element(screen.getByRole('heading', { name: 'Impossible de charger' }))
      .toBeVisible()
    await screen.getByRole('button', { name: 'Réessayer' }).click()

    await expect.element(screen.getByText('Accueil')).toBeVisible()
    expect(calls).toBe(2)
  })

  it('A10 : une relecture de /me qui échoue garde l’écran et la saisie en cours, le compte étant connu', async () => {
    let calls = 0
    const { screen } = await renderAt('/saisie', () => {
      calls += 1
      return calls === 1 ? Promise.resolve(ACTIVE) : Promise.reject(new NetworkError())
    })
    await screen.getByLabelText('saisie').fill('503271')
    await screen.getByRole('button', { name: 'relire' }).click()

    await expect.poll(() => calls).toBe(2)
    await expect.element(screen.getByLabelText('saisie')).toHaveValue('503271')
    await expect
      .element(screen.getByRole('heading', { name: 'Impossible de charger' }))
      .not.toBeInTheDocument()
  })
})

describe('KEY-11 : la zone du compte actif attend le contrôle de l’Autorité (ADR 0027, point 5)', () => {
  it('KEY-11 : au premier usage, la clé servie est épinglée et l’accueil s’affiche', async () => {
    const store = await freshKeyStore()
    const { screen } = await renderAt('/', () => Promise.resolve(ACTIVE), { store })

    await expect.element(screen.getByText('Accueil')).toBeVisible()
    await expect(store.loadAuthority()).resolves.toHaveLength(32)
  })

  it('KEY-11 : une Autorité changée bloque l’application, sans aucun écran du compte', async () => {
    const { screen } = await renderAt('/', () => Promise.resolve(ACTIVE), {
      store: await freshKeyStore(OTHER_AUTHORITY),
    })

    await expect
      .element(screen.getByRole('heading', { level: 1, name: 'Autorité SEAL modifiée' }))
      .toBeVisible()
    await expect.element(screen.getByRole('button', { name: 'Se déconnecter' })).toBeVisible()
    await expect.element(screen.getByText('Accueil')).not.toBeInTheDocument()
  })

  it('ADR 0027, point 5 : une réponse incohérente bloque aussi, et rien n’est épinglé', async () => {
    const store = await freshKeyStore()
    const { screen } = await renderAt('/', () => Promise.resolve(ACTIVE), {
      store,
      authority: servingAuthority({ ...VECTOR_AUTHORITY, fingerprint: '0'.repeat(64) }),
    })

    await expect
      .element(screen.getByRole('heading', { level: 1, name: 'Autorité SEAL modifiée' }))
      .toBeVisible()
    await expect(store.loadAuthority()).resolves.toBeUndefined()
  })

  it('KEY-11 : tant que le contrôle n’a pas répondu, aucun écran du compte ne s’affiche', async () => {
    const { screen } = await renderAt('/', () => Promise.resolve(ACTIVE), {
      authority: { fetchAuthority: () => new Promise(() => undefined) },
    })

    await new Promise((resolve) => setTimeout(resolve, 50))
    await expect.element(screen.getByText('Accueil')).not.toBeInTheDocument()
    await expect.element(screen.getByRole('alert')).not.toBeInTheDocument()
  })

  it('A10 : /pki/ca injoignable affiche « Impossible de charger », et « Réessayer » relance le contrôle', async () => {
    let calls = 0
    const { screen } = await renderAt('/', () => Promise.resolve(ACTIVE), {
      authority: {
        fetchAuthority: () => {
          calls += 1
          return calls === 1
            ? Promise.reject(new NetworkError())
            : Promise.resolve(VECTOR_AUTHORITY)
        },
      },
    })

    await screen.getByRole('button', { name: 'Réessayer' }).click()

    await expect.element(screen.getByText('Accueil')).toBeVisible()
    expect(calls).toBe(2)
  })

  it('ADR 0025, point 9 : un anonyme et un compte en attente n’interrogent pas /pki/ca', async () => {
    let calls = 0
    const authority: IAuthorityApi = {
      fetchAuthority: () => {
        calls += 1
        return Promise.resolve(VECTOR_AUTHORITY)
      },
    }
    const anonymous = await renderAt('/connexion', () => Promise.resolve(null), { authority })
    await expect.element(anonymous.screen.getByText('Écran de connexion')).toBeVisible()
    const pending = await renderAt('/verification-courriel', () => Promise.resolve(PENDING), {
      authority,
    })
    await expect.element(pending.screen.getByText('Écran de vérification')).toBeVisible()

    expect(calls).toBe(0)
  })

  describe('ADR 0027, point 7 : un contrôle frais à chaque entrée dans la zone', () => {
    /** Entre dans la zone, en sort par /libre, puis y revient ; le second contrôle répond selon `second`. */
    async function reenter(second: () => Promise<AuthorityView>) {
      let calls = 0
      const authority: IAuthorityApi = {
        fetchAuthority: () => {
          calls += 1
          return calls === 1 ? Promise.resolve(VECTOR_AUTHORITY) : second()
        },
      }
      const { router, screen } = await renderAt('/', () => Promise.resolve(ACTIVE), { authority })
      await expect.element(screen.getByText('Accueil')).toBeVisible()
      await router.navigate('/libre')
      await expect.element(screen.getByText('Libre')).toBeVisible()
      await router.navigate('/')
      return { screen, calls: () => calls }
    }

    it('ADR 0027 : au retour, rien ne s’affiche avant la réponse du nouveau contrôle', async () => {
      const { screen, calls } = await reenter(() => new Promise(() => undefined))

      await vi.waitFor(() => {
        expect(calls()).toBe(2)
      })
      await expect.element(screen.getByText('Accueil')).not.toBeInTheDocument()
    })

    it('ADR 0027 : au retour, un contrôle en échec mène à « Impossible de charger »', async () => {
      const { screen } = await reenter(() => Promise.reject(new NetworkError()))

      await expect
        .element(screen.getByRole('heading', { name: 'Impossible de charger' }))
        .toBeVisible()
      await expect.element(screen.getByText('Accueil')).not.toBeInTheDocument()
    })

    it('KEY-11 : au retour, une Autorité changée bloque l’application', async () => {
      const { screen } = await reenter(() =>
        Promise.resolve({ ...VECTOR_AUTHORITY, fingerprint: '0'.repeat(64) }),
      )

      await expect
        .element(screen.getByRole('heading', { level: 1, name: 'Autorité SEAL modifiée' }))
        .toBeVisible()
    })
  })
})
