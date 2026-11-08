import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { ErrorPage } from './ErrorPage'

describe('ADR 0012 : page de repli des frontières d’erreur', () => {
  it('TRN-21 : la page centre l’état d’erreur avec des marges de 16 px, sur le fond et l’encre de la maquette', async () => {
    const screen = await render(<ErrorPage onRetry={() => undefined} />)
    const page = screen.getByRole('main').element()

    expect(getComputedStyle(page)).toMatchObject({
      display: 'grid',
      alignItems: 'center',
      justifyItems: 'center',
      boxSizing: 'border-box',
      padding: '16px',
      minHeight: `${String(window.innerHeight)}px`,
    })
    expect(getComputedStyle(document.body)).toMatchObject({
      margin: '0px',
      backgroundColor: 'rgb(247, 248, 243)',
      color: 'rgb(27, 31, 20)',
      lineHeight: 'normal',
    })
    expect(getComputedStyle(document.body).fontFamily).toContain('Schibsted Grotesk')
  })
})
