import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { Phoque } from './Phoque'
import { Sceau } from './Sceau'
import { Wordmark } from './Wordmark'

function sizeOf(element: Element) {
  const { width, height } = element.getBoundingClientRect()
  return { width, height }
}

describe('Composants de marque de la maquette (Phoque, Sceau, Empreinte)', () => {
  it.each([
    ['waving', 'Le phoque SEAL vous salue'],
    ['neutral', 'Le phoque SEAL'],
    ['searching', 'Le phoque SEAL cherche'],
  ] as const)('Phoque : la pose %s porte le libellé de la maquette', async (variant, label) => {
    const screen = await render(<Phoque variant={variant} size={64} />)

    const image = screen.getByRole('img', { name: label })
    await expect.element(image).toBeVisible()
    expect(sizeOf(image.element())).toEqual({ width: 64, height: 64 })
  })

  it.each([64, 88, 200] as const)('Phoque : taille %i px', async (size) => {
    const screen = await render(<Phoque variant="waving" size={size} />)

    expect(sizeOf(screen.getByRole('img').element())).toEqual({ width: size, height: size })
  })

  it.each([8, 10, 18] as const)('Sceau : « Signature vérifiée », %i px', async (size) => {
    const screen = await render(<Sceau variant="verified" size={size} />)

    const image = screen.getByRole('img', { name: 'Signature vérifiée' })
    expect(sizeOf(image.element())).toEqual({ width: size, height: size })
  })

  it('ADR 0024, point 10 : Sceau décoratif, masqué aux lecteurs d’écran', async () => {
    const screen = await render(<Sceau variant="verified" size={18} decorative />)

    await expect.element(screen.getByRole('img')).not.toBeInTheDocument()
    expect(screen.container.querySelector('svg')?.getAttribute('aria-hidden')).toBe('true')
  })

  it('ADR 0024, point 10 : Wordmark, « seal » suivi d’un sceau décoratif', async () => {
    const screen = await render(<Wordmark variant="panel" />)

    await expect.element(screen.getByText('seal')).toBeVisible()
    await expect
      .element(screen.getByRole('img', { name: 'Signature vérifiée' }))
      .not.toBeInTheDocument()
  })
})
