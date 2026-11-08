import { afterEach, describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { ErrorBoundary } from './ErrorBoundary'

let broken = true
function FlakyTree() {
  if (broken) {
    throw new Error('boom at secretFunction (secret.ts:1:1)')
  }
  return <p>Rétabli</p>
}

describe('ADR 0012 : ErrorBoundary racine, hors du routeur', () => {
  afterEach(() => {
    broken = true
    vi.restoreAllMocks()
  })

  it('A10 : sans erreur, les enfants s’affichent tels quels', async () => {
    broken = false

    const screen = await render(
      <ErrorBoundary>
        <FlakyTree />
      </ErrorBoundary>,
    )

    await expect.element(screen.getByText('Rétabli')).toBeVisible()
  })

  it("A10 : une erreur de rendu affiche l'état « Erreur de chargement », sans pile ni texte anglais", async () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined)

    const screen = await render(
      <ErrorBoundary>
        <FlakyTree />
      </ErrorBoundary>,
    )

    await expect
      .element(screen.getByRole('heading', { level: 1, name: 'Impossible de charger' }))
      .toBeVisible()
    expect(document.body.textContent).not.toMatch(/boom|secret|Error/)
  })

  it('A10 : « Réessayer » refait le rendu des enfants', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined)
    const screen = await render(
      <ErrorBoundary>
        <FlakyTree />
      </ErrorBoundary>,
    )
    await expect.element(screen.getByRole('button', { name: 'Réessayer' })).toBeVisible()

    broken = false
    await screen.getByRole('button', { name: 'Réessayer' }).click()

    await expect.element(screen.getByText('Rétabli')).toBeVisible()
  })
})
