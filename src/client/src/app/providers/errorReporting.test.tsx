import { createMemoryRouter } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { withRootErrorBoundary } from '../router/routes'
import { ErrorBoundary } from './ErrorBoundary'
import { rootErrorOptions, routerErrorHandler } from './errorReporting'

function BrokenTree(): never {
  throw new Error('boom at secretFunction (secret.ts:1:1)')
}

describe('SEC-21 : journaux de React et de React Router en production', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it("SEC-21 : en production, une erreur de rendu captée par l'ErrorBoundary n'écrit rien dans la console", async () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined)

    const screen = await render(
      <ErrorBoundary>
        <BrokenTree />
      </ErrorBoundary>,
      { createRootOptions: rootErrorOptions(true) },
    )

    await expect
      .element(screen.getByRole('heading', { name: 'Impossible de charger' }))
      .toBeVisible()
    expect(consoleError).not.toHaveBeenCalled()
  })

  it("SEC-21 : en production, une erreur de rendu d'une route n'écrit rien dans la console", async () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined)
    const router = createMemoryRouter(withRootErrorBoundary([{ path: '/', Component: BrokenTree }]))

    const screen = await render(
      <RouterProvider router={router} onError={routerErrorHandler(true)} />,
      {
        createRootOptions: rootErrorOptions(true),
      },
    )

    await expect
      .element(screen.getByRole('heading', { name: 'Impossible de charger' }))
      .toBeVisible()
    expect(consoleError).not.toHaveBeenCalled()
  })

  it('SEC-21 : en développement, React et React Router gardent leurs traces', () => {
    expect(rootErrorOptions(false)).toEqual({})
    expect(routerErrorHandler(false)).toBeUndefined()
  })
})
