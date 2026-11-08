import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { LoadErrorState } from './LoadErrorState'

describe('ADR 0012 : état « Erreur de chargement » de la maquette (écran Etats)', () => {
  it('A10 : annonce le titre de la maquette et propose « Réessayer », sans la phrase sur le réseau', async () => {
    const screen = await render(<LoadErrorState onRetry={() => undefined} />)

    await expect.element(screen.getByRole('alert')).toBeVisible()
    await expect
      .element(screen.getByRole('heading', { level: 1, name: 'Impossible de charger' }))
      .toBeVisible()
    expect(screen.container.textContent).toBe('Impossible de chargerRéessayer')
  })

  it('A10 : « Réessayer » appelle onRetry une fois par clic', async () => {
    const onRetry = vi.fn()
    const screen = await render(<LoadErrorState onRetry={onRetry} />)

    await screen.getByRole('button', { name: 'Réessayer' }).click()

    expect(onRetry).toHaveBeenCalledOnce()
  })
})
