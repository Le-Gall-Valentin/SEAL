import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { Empreinte } from './Empreinte'
import { Phoque } from './Phoque'
import { Sceau } from './Sceau'
import { Wordmark } from './Wordmark'

const PANEL = '29E9C1694564624662CCEA21BAE28389594171A501E7BE963EE4D56DFE10C210'

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

  it('KEY-52 : Empreinte dessine 16 cases et annonce le début et la fin de l’empreinte', async () => {
    const screen = await render(<Empreinte fingerprint={PANEL} size={20} />)

    const image = screen.getByRole('img', { name: 'Motif de la clé 29E9…C210' })
    expect(sizeOf(image.element())).toEqual({ width: 20, height: 20 })
    expect(image.element().children).toHaveLength(16)
  })

  it('Wordmark : « seal » suivi du sceau vérifié', async () => {
    const screen = await render(<Wordmark variant="panel" />)

    await expect.element(screen.getByText('seal')).toBeVisible()
    await expect.element(screen.getByRole('img', { name: 'Signature vérifiée' })).toBeVisible()
  })
})
