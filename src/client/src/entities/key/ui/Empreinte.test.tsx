import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { Empreinte } from './Empreinte'

const PANEL = '29E9C1694564624662CCEA21BAE28389594171A501E7BE963EE4D56DFE10C210'

describe('KEY-52 : composant Empreinte de la maquette', () => {
  it('KEY-52 : 16 cases, et un libellé qui cite le début et la fin de l’empreinte', async () => {
    const screen = await render(<Empreinte fingerprint={PANEL} size={20} />)

    const image = screen.getByRole('img', { name: 'Motif de la clé 29E9…C210' })
    const { width, height } = image.element().getBoundingClientRect()
    expect({ width, height }).toEqual({ width: 20, height: 20 })
    expect(image.element().children).toHaveLength(16)
  })
})
