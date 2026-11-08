import { createMemoryRouter, type RouteObject } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { routes, withRootErrorBoundary } from './routes'

async function renderAt(path: string, tree: RouteObject[] = routes) {
  const router = createMemoryRouter(tree, { initialEntries: [path] })
  const screen = await render(<RouterProvider router={router} />)
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

  it("ADR 0012 : la racine affiche la page d'accueil provisoire, en attendant l'écran de connexion du lot 1", async () => {
    const { screen } = await renderAt('/')

    await expect.element(screen.getByRole('heading', { level: 1, name: 'SEAL' })).toBeVisible()
  })

  it('ADR 0012 : une adresse inconnue ramène à la racine, sans écran nouveau', async () => {
    const { router, screen } = await renderAt('/adresse/inconnue')

    await expect.element(screen.getByRole('heading', { level: 1, name: 'SEAL' })).toBeVisible()
    expect(router.state.location.pathname).toBe('/')
  })

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
})
