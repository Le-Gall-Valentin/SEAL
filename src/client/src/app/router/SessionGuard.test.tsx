import { QueryClientProvider, useQuery } from '@tanstack/react-query'
import { createMemoryRouter, type RouteObject } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { type Account, type ISessionApi, SessionApiProvider } from '@/entities/session'
import { ApiError, NetworkError } from '@/shared/lib'
import { createQueryClient } from '../providers'
import { SessionGuard } from './SessionGuard'

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

// Une page qui découvre en cours de route que la session n'existe plus (expirée, révoquée).
function ExpiringPage() {
  const query = useQuery({
    queryKey: ['donnees'],
    queryFn: () => Promise.reject(new ApiError(401, 'UNAUTHENTICATED')),
  })
  return <p>{query.isError ? 'Session perdue' : 'Données'}</p>
}

const TREE: RouteObject[] = [
  {
    element: <SessionGuard allow="anonymous" />,
    children: [{ path: '/connexion', element: <p>Écran de connexion</p> }],
  },
  {
    element: <SessionGuard allow="pending" />,
    children: [{ path: '/verification-courriel', element: <p>Écran de vérification</p> }],
  },
  {
    element: <SessionGuard allow="active" />,
    children: [
      { path: '/', element: <p>Accueil</p> },
      { path: '/expire', element: <ExpiringPage /> },
    ],
  },
]

async function renderAt(path: string, me: () => Promise<Account | null>) {
  const api: ISessionApi = { me, reloadCsrf: () => Promise.resolve() }
  const router = createMemoryRouter(TREE, { initialEntries: [path] })
  const screen = await render(
    <QueryClientProvider client={createQueryClient()}>
      <SessionApiProvider api={api}>
        <RouterProvider router={router} />
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
    const { router, screen } = await renderAt('/expire', () => Promise.resolve(ACTIVE))

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
})
