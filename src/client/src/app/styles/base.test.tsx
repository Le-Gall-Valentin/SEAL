import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'

describe('ADR 0016 : styles de base de la feuille d’entrée', () => {
  it('ADR 0016 : un lien garde le soulignement par défaut et prend la couleur de la maquette', async () => {
    const screen = await render(<a href="/parametres">Comparer les empreintes</a>)
    const link = screen.getByRole('link').element()

    expect(getComputedStyle(link)).toMatchObject({
      textDecorationLine: 'underline',
      color: 'rgb(82, 94, 21)',
    })
  })
})
