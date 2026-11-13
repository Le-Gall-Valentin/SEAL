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

describe('ADR 0007 : polices servies par SEAL', () => {
  it.each([
    ['650 16px "Schibsted Grotesk Variable"'],
    ['400 16px "DM Mono"'],
    ['500 16px "DM Mono"'],
  ])('ADR 0007, point 8 : %s se charge depuis le client, sans ressource tierce', async (font) => {
    const faces = await document.fonts.load(font, 'SEAL')

    expect(faces.length).toBeGreaterThan(0)
    expect(document.fonts.check(font, 'SEAL')).toBe(true)
  })

  it('ADR 0024 : le texte prend Schibsted Grotesk, en police variable', async () => {
    const screen = await render(<p>Connexion</p>)

    expect(
      getComputedStyle(screen.container.querySelector('p') ?? document.body).fontFamily,
    ).toMatch(/^"Schibsted Grotesk Variable"/)
  })
})
