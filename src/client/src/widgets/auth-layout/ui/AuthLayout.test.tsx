import { afterEach, describe, expect, it } from 'vitest'
import { page } from 'vitest/browser'
import { render } from 'vitest-browser-react'
import { AuthLayout } from './AuthLayout'

function box(element: Element) {
  return element.getBoundingClientRect()
}

async function renderAt(width: number, height: number) {
  await page.viewport(width, height)
  return render(
    <AuthLayout variant="searching">
      <h1>Formulaire</h1>
    </AuthLayout>,
  )
}

describe('ADR 0007, point 7 : mise en page des écrans d’authentification', () => {
  afterEach(async () => {
    await page.viewport(1280, 800)
  })

  it('Connexion-Ordinateur : panneau de marque à gauche, formulaire à droite, sur 400 px au plus', async () => {
    const screen = await renderAt(1440, 900)

    const panel = screen.getByRole('complementary')
    await expect.element(panel).toBeVisible()
    await expect.element(panel.getByText('Messagerie chiffrée de bout en bout.')).toBeVisible()
    await expect
      .element(panel.getByText('Vos messages sont chiffrés sur votre appareil.'))
      .toBeVisible()
    await expect
      .element(panel.getByText('Chaque message porte le sceau de son auteur.'))
      .toBeVisible()
    await expect
      .element(panel.getByText('Vous comparez vos clés en personne, d’un coup d’œil.'))
      .toBeVisible()
    await expect.element(panel.getByText('Secure · Encrypted · Authenticated · Link')).toBeVisible()
    await expect
      .element(panel.getByRole('img', { name: 'Le phoque SEAL vous salue' }))
      .toBeVisible()
    await expect
      .element(panel.getByRole('img', { name: 'Motif de la clé 29E9…C210' }))
      .toBeVisible()

    const form = screen.getByRole('heading', { name: 'Formulaire' }).element()
    expect(box(form).left).toBeGreaterThanOrEqual(box(panel.element()).right)
    expect(box(form.parentElement ?? form).width).toBeLessThanOrEqual(400)
    // La pose de l'écran n'apparaît que sur tablette et téléphone.
    await expect
      .element(screen.getByRole('img', { name: 'Le phoque SEAL cherche' }))
      .not.toBeInTheDocument()
  })

  it('Connexion-Tablette : logo centré au-dessus d’une carte de 480 px, sans panneau', async () => {
    const screen = await renderAt(820, 1180)

    await expect.element(screen.getByRole('complementary')).not.toBeInTheDocument()
    const mascot = screen.getByRole('img', { name: 'Le phoque SEAL cherche' })
    await expect.element(mascot).toBeVisible()
    expect(box(mascot.element()).width).toBe(88)
    const card = screen.getByRole('heading', { name: 'Formulaire' }).element().parentElement
    expect(card === null ? 0 : box(card).width).toBe(480)
    expect(box(mascot.element()).bottom).toBeLessThanOrEqual(card === null ? 0 : box(card).top)
  })

  it('Connexion-Telephone : mascotte de 64 px à côté du logo, formulaire pleine largeur', async () => {
    const screen = await renderAt(390, 844)

    await expect.element(screen.getByRole('complementary')).not.toBeInTheDocument()
    const mascot = screen.getByRole('img', { name: 'Le phoque SEAL cherche' })
    expect(box(mascot.element()).width).toBe(64)
    // Le logo du panneau, masqué sur téléphone, reste dans le document : seul celui de l'en-tête se voit.
    const visible = screen
      .getByText('seal')
      .elements()
      .filter((element) => box(element).width > 0)
    expect(visible).toHaveLength(1)
    const form = screen.getByRole('heading', { name: 'Formulaire' }).element()
    expect(box(form.parentElement ?? form).width).toBe(350)
  })
})
