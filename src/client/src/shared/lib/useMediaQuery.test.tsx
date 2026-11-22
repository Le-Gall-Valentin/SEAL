import { afterEach, describe, expect, it } from 'vitest'
import { page } from 'vitest/browser'
import { render } from 'vitest-browser-react'
import { useMediaQuery, WIDE_SCREEN } from './useMediaQuery'

function Probe() {
  return <p>{useMediaQuery(WIDE_SCREEN) ? 'large' : 'étroit'}</p>
}

describe('ADR 0035 : largeur de l’écran', () => {
  afterEach(async () => {
    await page.viewport(1280, 800)
  })

  it('ADR 0035 : suit la largeur en direct, téléphone sous 640 px', async () => {
    await page.viewport(390, 844)
    const screen = await render(<Probe />)
    await expect.element(screen.getByText('étroit')).toBeVisible()

    await page.viewport(820, 1180)
    await expect.element(screen.getByText('large')).toBeVisible()
  })
})
