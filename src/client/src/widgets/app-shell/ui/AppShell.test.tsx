import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { page } from 'vitest/browser'
import { render } from 'vitest-browser-react'
import { FriendsApiProvider, type ReceivedRequest } from '@/entities/friend'
import { type Account, SessionApiProvider } from '@/entities/session'
import { AppShell } from './AppShell'

const ACTIVE: Account = {
  id: '0192f0c4-7b1a-7cc0-9a51-3f6f8e2d4b10',
  username: 'valentin.dupont',
  displayName: 'Valentin Dupont',
  email: 'valentin@uqac.ca',
  status: 'ACTIVE',
  verification: null,
}

function received(count: number): ReceivedRequest[] {
  return Array.from({ length: count }, (_, i) => ({
    id: `0193a1b2-0000-7000-8000-${String(i).padStart(12, '0')}`,
    userId: `0192f7a1-0000-7000-8000-${String(i).padStart(12, '0')}`,
    username: `ami.${String(i)}`,
    displayName: 'Ami',
    createdAt: new Date(0),
  }))
}

const never = () => Promise.reject(new Error('non attendu'))

async function renderAt(
  path: string,
  width: number,
  height: number,
  invitations = 0,
  onRead: () => void = () => undefined,
) {
  await page.viewport(width, height)
  const router = createMemoryRouter(
    [
      {
        element: <AppShell />,
        children: [
          { path: '/', element: <p>Accueil</p> },
          { path: '/parametres', element: <p>Écran des paramètres</p> },
        ],
      },
    ],
    { initialEntries: [path] },
  )
  const screen = await render(
    <QueryClientProvider client={new QueryClient()}>
      <SessionApiProvider
        api={{ me: () => Promise.resolve(ACTIVE), reloadCsrf: () => Promise.resolve() }}
      >
        <FriendsApiProvider
          api={{
            friends: never,
            sentRequests: never,
            receivedRequests: () => {
              onRead()
              return Promise.resolve({ items: received(invitations), next: null })
            },
          }}
        >
          <RouterProvider router={router} />
        </FriendsApiProvider>
      </SessionApiProvider>
    </QueryClientProvider>,
  )
  return { router, screen }
}

describe('ACC-03, ADR 0035 (point 3) : navigation principale', () => {
  afterEach(async () => {
    await page.viewport(1280, 800)
  })

  it('ACC-03, ADR 0035 : ordinateur, rail de 88 px avec libellés ; Paramètres est la page courante', async () => {
    const { screen } = await renderAt('/parametres', 1440, 900)

    const nav = screen.getByRole('navigation', { name: 'Navigation principale' })
    await expect.element(nav).toBeVisible()
    expect(nav.element().getBoundingClientRect().width).toBe(88)
    await expect
      .element(nav.getByRole('link', { name: 'Paramètres' }))
      .toHaveAttribute('aria-current', 'page')
    await expect
      .element(nav.getByRole('link', { name: 'Conversations' }))
      .not.toHaveAttribute('aria-current')
    await expect.element(nav.getByText('Paramètres', { exact: true })).toBeVisible()
    await expect.element(nav.getByRole('link', { name: 'Amis' })).toHaveAttribute('href', '/amis')
    await expect.element(nav.getByRole('link', { name: 'SEAL' })).toHaveAttribute('href', '/')
    await expect.element(screen.getByText('Écran des paramètres')).toBeVisible()
  })

  it('ACC-03, ADR 0035 : « Mon profil » montre les initiales du nom affiché et mène aux paramètres', async () => {
    const { router, screen } = await renderAt('/', 1440, 900)

    const profile = screen.getByRole('link', { name: 'Mon profil' })
    await expect.element(profile).toHaveTextContent('VD')
    await profile.click()
    expect(router.state.location.pathname).toBe('/parametres')
  })

  it('ACC-03, ADR 0035 : tablette, rail de 72 px sans libellés visibles, mais nommés', async () => {
    const { screen } = await renderAt('/', 820, 1180)

    const nav = screen.getByRole('navigation', { name: 'Navigation principale' })
    expect(nav.element().getBoundingClientRect().width).toBe(72)
    await expect
      .element(nav.getByRole('link', { name: 'Conversations' }))
      .toHaveAttribute('aria-current', 'page')
    await expect.element(nav.getByText('Conversations', { exact: true })).not.toBeVisible()
  })

  it('FRD-04, ADR 0038 : Amis entre Conversations et Paramètres, avec la pastille des invitations reçues', async () => {
    const { screen } = await renderAt('/', 1440, 900, 2)

    const nav = screen.getByRole('navigation', { name: 'Navigation principale' })
    const friends = nav.getByRole('link', { name: 'Amis, 2 invitations' })
    await expect.element(friends).toBeVisible()
    expect(friends.element().textContent).toBe('Amis2')
    const names = nav
      .getByRole('link')
      .elements()
      .map((link) => link.getAttribute('aria-label'))
    expect(names.slice(1, 4)).toEqual(['Conversations', 'Amis, 2 invitations', 'Paramètres'])
  })

  it('FRD-04, ADR 0038 : une invitation au singulier ; au-delà de 99, « 99+ »', async () => {
    const one = await renderAt('/', 820, 1180, 1)
    await expect.element(one.screen.getByRole('link', { name: 'Amis, 1 invitation' })).toBeVisible()
    await one.screen.unmount()

    const many = await renderAt('/', 820, 1180, 120)
    const link = many.screen.getByRole('link', { name: 'Amis, 120 invitations' })
    await expect.element(link).toBeVisible()
    expect(link.element().textContent).toContain('99+')
  })

  it('FRD-04, AUTH-52, ADR 0037 (point 11) : la coque relit les invitations toutes les 60 s, seulement après une action', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] })
    try {
      let reads = 0
      const { screen } = await renderAt('/', 1440, 900, 1, () => {
        reads += 1
      })
      await expect.element(screen.getByRole('link', { name: 'Amis, 1 invitation' })).toBeVisible()
      const first = reads

      vi.advanceTimersByTime(2 * 60_000)
      expect(reads).toBe(first)

      window.dispatchEvent(new KeyboardEvent('keydown'))
      vi.advanceTimersByTime(60_000)
      await expect.poll(() => reads).toBe(first + 1)
    } finally {
      vi.useRealTimers()
    }
  })

  it('ACC-03, ADR 0038 : téléphone, barre du bas en trois colonnes ; aller aux paramètres', async () => {
    const { router, screen } = await renderAt('/', 390, 844)

    const nav = screen.getByRole('navigation', { name: 'Navigation principale' })
    const bar = nav.element().getBoundingClientRect()
    expect(bar.height).toBe(64)
    expect(Math.round(bar.bottom)).toBe(844)
    expect(nav.getByRole('link').elements()).toHaveLength(3)
    const widths = nav
      .getByRole('link')
      .elements()
      .map((link) => Math.round(link.getBoundingClientRect().width))
    expect(widths).toEqual([130, 130, 130])
    await nav.getByRole('link', { name: 'Paramètres' }).click()
    expect(router.state.location.pathname).toBe('/parametres')
    await expect
      .element(nav.getByRole('link', { name: 'Paramètres' }))
      .toHaveAttribute('aria-current', 'page')
  })

  it('ACC-03, ADR 0035 : téléphone, une sous-page des paramètres retire la barre du bas', async () => {
    const { screen } = await renderAt('/parametres?section=compte', 390, 844)

    await expect.element(screen.getByText('Écran des paramètres')).toBeVisible()
    expect(
      screen.getByRole('navigation', { name: 'Navigation principale' }).elements(),
    ).toHaveLength(0)
  })

  it('ACC-03, ADR 0035 : une valeur de section inconnue n’est pas une sous-page ; la barre du bas reste', async () => {
    const { screen } = await renderAt('/parametres?section=inconnue', 390, 844)

    await expect.element(screen.getByText('Écran des paramètres')).toBeVisible()
    await expect
      .element(screen.getByRole('navigation', { name: 'Navigation principale' }))
      .toBeVisible()
  })
})
