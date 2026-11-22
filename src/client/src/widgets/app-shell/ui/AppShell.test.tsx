import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, describe, expect, it } from 'vitest'
import { page } from 'vitest/browser'
import { render } from 'vitest-browser-react'
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

async function renderAt(path: string, width: number, height: number) {
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
        <RouterProvider router={router} />
      </SessionApiProvider>
    </QueryClientProvider>,
  )
  return { router, screen }
}

describe('ADR 0035, point 3 : navigation principale', () => {
  afterEach(async () => {
    await page.viewport(1280, 800)
  })

  it('ADR 0035 : ordinateur, rail de 88 px avec libellés ; Paramètres est la page courante ; ni Amis ni pastille', async () => {
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
    expect(nav.getByRole('link', { name: /Amis/ }).elements()).toHaveLength(0)
    await expect.element(nav.getByRole('link', { name: 'SEAL' })).toHaveAttribute('href', '/')
    await expect.element(screen.getByText('Écran des paramètres')).toBeVisible()
  })

  it('ADR 0035 : « Mon profil » montre les initiales du nom affiché et mène aux paramètres', async () => {
    const { router, screen } = await renderAt('/', 1440, 900)

    const profile = screen.getByRole('link', { name: 'Mon profil' })
    await expect.element(profile).toHaveTextContent('VD')
    await profile.click()
    expect(router.state.location.pathname).toBe('/parametres')
  })

  it('ADR 0035 : tablette, rail de 72 px sans libellés visibles, mais nommés', async () => {
    const { screen } = await renderAt('/', 820, 1180)

    const nav = screen.getByRole('navigation', { name: 'Navigation principale' })
    expect(nav.element().getBoundingClientRect().width).toBe(72)
    await expect
      .element(nav.getByRole('link', { name: 'Conversations' }))
      .toHaveAttribute('aria-current', 'page')
    await expect.element(nav.getByText('Conversations', { exact: true })).not.toBeVisible()
  })

  it('ADR 0035 : téléphone, barre du bas en deux colonnes ; aller aux paramètres', async () => {
    const { router, screen } = await renderAt('/', 390, 844)

    const nav = screen.getByRole('navigation', { name: 'Navigation principale' })
    const bar = nav.element().getBoundingClientRect()
    expect(bar.height).toBe(64)
    expect(Math.round(bar.bottom)).toBe(844)
    expect(nav.getByRole('link').elements()).toHaveLength(2)
    await nav.getByRole('link', { name: 'Paramètres' }).click()
    expect(router.state.location.pathname).toBe('/parametres')
    await expect
      .element(nav.getByRole('link', { name: 'Paramètres' }))
      .toHaveAttribute('aria-current', 'page')
  })

  it('ADR 0035 : téléphone, une sous-page des paramètres retire la barre du bas', async () => {
    const { screen } = await renderAt('/parametres?section=compte', 390, 844)

    await expect.element(screen.getByText('Écran des paramètres')).toBeVisible()
    expect(
      screen.getByRole('navigation', { name: 'Navigation principale' }).elements(),
    ).toHaveLength(0)
  })
})
