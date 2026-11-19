import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { StateCard } from './StateCard'

const LONG_TEXT =
  'La clé de l’Autorité SEAL a changé depuis votre première connexion sur cet appareil. Par sécurité, SEAL est bloqué : prévenez l’administrateur du serveur.'

describe('ADR 0027 : carte d’état bloquant, d’après l’état « Erreur de chargement » (écran Etats)', () => {
  it('ADR 0027 : annonce le titre au niveau demandé et le texte, sans action', async () => {
    const screen = await render(<StateCard heading="h1" title="Titre" text="Texte" />)

    await expect.element(screen.getByRole('alert')).toBeVisible()
    await expect.element(screen.getByRole('heading', { level: 1, name: 'Titre' })).toBeVisible()
    expect(screen.getByRole('alert').element().textContent).toBe('TitreTexte')
    await expect.element(screen.getByRole('region', { name: 'Titre' })).toBeVisible()
    expect(screen.getByRole('button').elements()).toHaveLength(0)
  })

  // Valeurs de l'écran Etats de la maquette : paragraphe de la carte « Erreur de chargement ».
  it('ADR 0016 : le texte reprend la marge, la taille, l’interligne et la couleur de la maquette', async () => {
    const screen = await render(<StateCard heading="h1" title="Titre" text="Texte" />)
    const text = screen.getByText('Texte').element()

    expect(getComputedStyle(text)).toMatchObject({
      marginTop: '6px',
      marginBottom: '0px',
      fontSize: '14px',
      lineHeight: '20.3px',
      color: 'rgb(74, 80, 64)',
    })
  })

  it('ADR 0016 : la carte garde 236 px de haut, et l’action est posée à 22 px du bas', async () => {
    const screen = await render(
      <StateCard heading="h1" title="Titre" actions={<button type="button">Action</button>} />,
    )
    const card = screen.getByRole('region').element()
    const action = screen.getByRole('button').element()

    expect(card.getBoundingClientRect().height).toBe(236)
    expect(card.getBoundingClientRect().bottom - action.getBoundingClientRect().bottom).toBe(22)
  })

  it('ADR 0027 : un texte long et une action tiennent dans la carte, qui grandit sans rien couper', async () => {
    const screen = await render(
      <div className="w-80">
        <StateCard
          heading="h1"
          title="Autorité SEAL modifiée"
          text={LONG_TEXT}
          actions={<button type="button">Se déconnecter</button>}
        />
      </div>,
    )
    const card = screen.getByRole('region').element()
    const text = screen.getByText(LONG_TEXT).element()
    const action = screen.getByRole('button').element()

    expect(card.scrollHeight).toBe(card.clientHeight)
    expect(
      action.getBoundingClientRect().top - text.getBoundingClientRect().bottom,
    ).toBeGreaterThanOrEqual(16)
    expect(card.getBoundingClientRect().bottom - action.getBoundingClientRect().bottom).toBe(22)
  })

  it('ADR 0027, point 13 : les actions restent hors de l’alerte, qui n’annonce que le titre et le texte', async () => {
    const screen = await render(
      <StateCard heading="h1" title="Titre" text="Texte" actions={<p role="alert">Échec</p>} />,
    )

    expect(screen.getByRole('alert').elements()).toHaveLength(2)
    for (const alert of screen.getByRole('alert').elements()) {
      expect(alert.querySelector('[role="alert"]')).toBeNull()
    }
  })
})
