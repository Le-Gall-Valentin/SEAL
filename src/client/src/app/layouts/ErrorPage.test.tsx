import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { ErrorPage } from './ErrorPage'

describe('ADR 0012 : page de repli des frontières d’erreur', () => {
  it('ADR 0016 : l’état d’erreur occupe la page pleine, sur le fond et l’encre de la maquette', async () => {
    const screen = await render(<ErrorPage onRetry={() => undefined} />)

    await expect
      .element(
        screen.getByRole('main').getByRole('heading', { level: 1, name: 'Impossible de charger' }),
      )
      .toBeVisible()
    expect(getComputedStyle(document.body)).toMatchObject({
      margin: '0px',
      backgroundColor: 'rgb(247, 248, 243)',
      color: 'rgb(27, 31, 20)',
      lineHeight: 'normal',
    })
    expect(getComputedStyle(document.body).fontFamily).toContain('Schibsted Grotesk')
  })
})
