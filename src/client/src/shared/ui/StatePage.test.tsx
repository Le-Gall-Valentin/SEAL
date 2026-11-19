import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { StatePage } from './StatePage'

describe('ADR 0012, ADR 0027 : page pleine d’un état (écran Etats)', () => {
  it('ADR 0016 : la page centre son état avec des marges de 16 px, sur toute la hauteur', async () => {
    const screen = await render(
      <StatePage>
        <p>État</p>
      </StatePage>,
    )

    expect(getComputedStyle(screen.getByRole('main').element())).toMatchObject({
      display: 'grid',
      alignItems: 'center',
      justifyItems: 'center',
      boxSizing: 'border-box',
      padding: '16px',
      minHeight: `${String(window.innerHeight)}px`,
    })
    await expect.element(screen.getByText('État')).toBeVisible()
  })
})
