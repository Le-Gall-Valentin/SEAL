import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { LoadErrorState } from './LoadErrorState'

// Tailwind compose box-shadow avec des ombres transparentes et nulles, sans effet à l'écran : seules les autres comptent.
function visibleShadows(element: Element): string[] {
  return getComputedStyle(element)
    .boxShadow.split(/,(?![^(]*\))/)
    .map((shadow) => shadow.trim())
    .filter((shadow) => shadow !== 'rgba(0, 0, 0, 0) 0px 0px 0px 0px')
}

describe('ADR 0012 : état « Erreur de chargement » de la maquette (écran Etats)', () => {
  it('A10 : annonce le titre de la maquette et propose « Réessayer », sans la phrase sur le réseau', async () => {
    const screen = await render(<LoadErrorState heading="h1" onRetry={() => undefined} />)

    await expect.element(screen.getByRole('alert')).toBeVisible()
    await expect
      .element(screen.getByRole('heading', { level: 1, name: 'Impossible de charger' }))
      .toBeVisible()
    expect(screen.container.textContent).toBe('Impossible de chargerRéessayer')
  })

  it('A10 : « Réessayer » appelle onRetry une fois par clic', async () => {
    const onRetry = vi.fn()
    const screen = await render(<LoadErrorState heading="h1" onRetry={onRetry} />)

    await screen.getByRole('button', { name: 'Réessayer' }).click()

    expect(onRetry).toHaveBeenCalledOnce()
  })

  it("ADR 0012 : le niveau du titre est celui que demande l'appelant", async () => {
    const screen = await render(<LoadErrorState heading="h2" onRetry={() => undefined} />)

    await expect
      .element(screen.getByRole('heading', { level: 2, name: 'Impossible de charger' }))
      .toBeVisible()
  })

  // Valeurs de l'écran Etats de la maquette (bloc <helmet> et styles de l'état « Erreur de chargement »).
  it('ADR 0016 : la carte, l’icône, le titre et le bouton reprennent les dimensions et les jetons de la maquette', async () => {
    const screen = await render(<LoadErrorState heading="h1" onRetry={() => undefined} />)
    const card = screen.getByRole('alert').element()
    const icon = card.querySelector('[aria-hidden="true"]')
    const title = screen.getByRole('heading').element()
    const button = screen.getByRole('button').element()
    if (icon === null) throw new Error('icône absente')

    expect(getComputedStyle(card)).toMatchObject({
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'flex-start',
      boxSizing: 'border-box',
      height: '236px',
      padding: '22px',
      borderRadius: '18px',
      backgroundColor: 'rgb(255, 255, 255)',
    })
    expect(getComputedStyle(icon)).toMatchObject({
      display: 'grid',
      alignItems: 'center',
      justifyItems: 'center',
      flexShrink: '0',
      width: '44px',
      height: '44px',
      borderRadius: '12px',
      backgroundColor: 'rgb(251, 237, 234)',
      color: 'rgb(179, 38, 30)',
    })
    expect(getComputedStyle(title)).toMatchObject({
      marginTop: '14px',
      marginBottom: '0px',
      fontSize: '18px',
      fontWeight: '700',
      letterSpacing: '-0.18px',
      lineHeight: 'normal',
    })
    // Élément d'un conteneur flex : son inline-flex est calculé en flex, et margin-top: auto le pose en bas de la carte.
    expect(card.getBoundingClientRect().bottom - button.getBoundingClientRect().bottom).toBe(22)
    expect(getComputedStyle(button)).toMatchObject({
      display: 'flex',
      alignItems: 'center',
      gap: '8px',
      height: '44px',
      padding: '0px 16px 0px 12px',
      borderTopWidth: '0px',
      borderRadius: '10px',
      backgroundColor: 'rgb(255, 255, 255)',
      color: 'rgb(27, 31, 20)',
      fontSize: '14.5px',
      fontWeight: '600',
      lineHeight: 'normal',
    })
    expect(getComputedStyle(button).fontFamily).toContain('Schibsted Grotesk')
    expect(visibleShadows(card)).toEqual(['rgb(227, 230, 218) 0px 0px 0px 1px inset'])
    expect(visibleShadows(icon)).toEqual(['rgb(239, 197, 190) 0px 0px 0px 1px inset'])
    expect(visibleShadows(button)).toEqual(['rgb(205, 210, 192) 0px 0px 0px 1px inset'])
  })

  it("ADR 0012 : la carte fait au plus 380 px de large, et toute la largeur d'un conteneur plus étroit", async () => {
    for (const [width, expected] of [
      ['600px', 380],
      ['200px', 200],
    ] as const) {
      const container = document.body.appendChild(document.createElement('div'))
      container.style.width = width
      const screen = await render(<LoadErrorState heading="h1" onRetry={() => undefined} />, {
        container,
      })

      expect(screen.getByRole('alert').element().getBoundingClientRect().width).toBe(expected)
      await screen.unmount()
      container.remove()
    }
  })

  it('ADR 0016 : les dimensions restent celles de la maquette quand la police du navigateur grandit', async () => {
    document.documentElement.style.fontSize = '24px'
    try {
      const screen = await render(<LoadErrorState heading="h1" onRetry={() => undefined} />)
      const card = screen.getByRole('alert').element()
      const icon = card.querySelector('[aria-hidden="true"]')
      if (icon === null) throw new Error('icône absente')

      expect(getComputedStyle(card)).toMatchObject({ height: '236px', padding: '22px' })
      expect(getComputedStyle(icon)).toMatchObject({ width: '44px', borderRadius: '12px' })
      expect(getComputedStyle(screen.getByRole('heading').element()).marginTop).toBe('14px')
      expect(getComputedStyle(screen.getByRole('button').element())).toMatchObject({
        height: '44px',
        paddingRight: '16px',
        gap: '8px',
      })
    } finally {
      document.documentElement.style.fontSize = ''
    }
  })
})
