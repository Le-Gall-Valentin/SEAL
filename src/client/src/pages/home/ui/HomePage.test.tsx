import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { HomePage } from './HomePage'

describe("ADR 0012 : page d'accueil provisoire", () => {
  it('ADR 0016 : le nom est centré dans la page, avec les styles du mot-symbole de la maquette', async () => {
    const screen = await render(<HomePage />)
    const page = screen.getByRole('main').element()
    const wordmark = screen.getByRole('heading', { level: 1, name: 'SEAL' }).element()

    expect(getComputedStyle(page)).toMatchObject({
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      minHeight: `${String(window.innerHeight)}px`,
    })
    expect(getComputedStyle(wordmark)).toMatchObject({
      margin: '0px',
      fontSize: '34px',
      fontWeight: '800',
      letterSpacing: '-1.7px',
      lineHeight: '34px',
    })
  })
})
