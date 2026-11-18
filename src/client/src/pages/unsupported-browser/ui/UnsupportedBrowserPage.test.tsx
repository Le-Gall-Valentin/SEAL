import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { UnsupportedBrowserPage } from './UnsupportedBrowserPage'

describe('KEY-01 : page « Navigateur non pris en charge » (ADR 0027, point 1)', () => {
  it('KEY-01 : annonce le titre du CDC et le texte validé, sans aucun bouton', async () => {
    const screen = await render(<UnsupportedBrowserPage />)

    await expect.element(screen.getByRole('alert')).toBeVisible()
    await expect
      .element(screen.getByRole('heading', { level: 1, name: 'Navigateur non pris en charge' }))
      .toBeVisible()
    await expect
      .element(
        screen.getByText(
          'SEAL chiffre vos messages dans votre navigateur, et celui-ci ne propose pas les algorithmes nécessaires (Ed25519 et X25519). Mettez-le à jour ou ouvrez SEAL dans un navigateur récent.',
        ),
      )
      .toBeVisible()
    expect(screen.getByRole('button').elements()).toHaveLength(0)
  })

  it('ADR 0016 : la carte est centrée dans la page, avec des marges de 16 px, comme la page de repli', async () => {
    const screen = await render(<UnsupportedBrowserPage />)

    expect(getComputedStyle(screen.getByRole('main').element())).toMatchObject({
      display: 'grid',
      alignItems: 'center',
      justifyItems: 'center',
      padding: '16px',
      minHeight: `${String(window.innerHeight)}px`,
    })
  })
})
