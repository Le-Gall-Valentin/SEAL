import { renderToString } from 'react-dom/server'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { describe, expect, it } from 'vitest'
import { routes } from './routes'

describe('routes', () => {
  it("affiche la page provisoire à la racine, en attendant l'écran de connexion du lot 1", () => {
    const router = createMemoryRouter(routes, { initialEntries: ['/'] })

    const html = renderToString(<RouterProvider router={router} />)

    expect(html).toContain('<h1')
    expect(html).toContain('SEAL')
  })
})
