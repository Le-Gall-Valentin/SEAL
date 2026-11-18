import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { CryptoGate } from './CryptoGate'

const NEVER = new Promise<boolean>(() => undefined)

describe('KEY-01 : rien ne fonctionne sans Ed25519 ni X25519', () => {
  it('KEY-01 : un navigateur sans les algorithmes ne voit que « Navigateur non pris en charge »', async () => {
    const screen = await render(
      <CryptoGate support={Promise.resolve(false)}>
        <p>Application</p>
      </CryptoGate>,
    )

    await expect
      .element(screen.getByRole('heading', { level: 1, name: 'Navigateur non pris en charge' }))
      .toBeVisible()
    expect(screen.getByText('Application').elements()).toHaveLength(0)
  })

  it('KEY-01 : un navigateur qui les propose voit l’application', async () => {
    const screen = await render(
      <CryptoGate support={Promise.resolve(true)}>
        <p>Application</p>
      </CryptoGate>,
    )

    await expect.element(screen.getByText('Application')).toBeVisible()
    expect(screen.getByRole('alert').elements()).toHaveLength(0)
  })

  it('KEY-01 : tant que le contrôle n’a pas répondu, rien ne s’affiche', async () => {
    const screen = await render(
      <CryptoGate support={NEVER}>
        <p>Application</p>
      </CryptoGate>,
    )

    await new Promise((resolve) => setTimeout(resolve, 50))
    expect(screen.container.textContent).toBe('')
  })
})
