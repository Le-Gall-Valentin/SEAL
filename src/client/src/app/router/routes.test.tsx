import { renderToString } from 'react-dom/server'
import { createMemoryRouter } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { describe, expect, it } from 'vitest'
import { routes } from './routes'

describe('ADR 0012 : routeur de la couche app', () => {
  it("ADR 0012 : la racine affiche la page d'accueil provisoire, en attendant l'écran de connexion du lot 1", () => {
    const router = createMemoryRouter(routes, { initialEntries: ['/'] })

    const html = renderToString(<RouterProvider router={router} />)

    expect(html).toContain('<h1')
    expect(html).toContain('SEAL')
  })
})
