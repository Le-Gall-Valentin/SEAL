import { expect, type Page, test } from '@playwright/test'

// ADR 0024, point 11 : mise en page des écrans de l'authentification, mesurée dans un vrai navigateur.

/** Du Galaxy Fold fermé (280 px) au grand écran, en passant par le Galaxy Z Fold 5 fermé (344 px). */
const WIDTHS = [280, 320, 344, 360, 375, 390, 412, 640, 820, 1024, 1280, 1440, 1920] as const
const DESKTOP_WIDTHS = [1024, 1440, 1920] as const

/** Largeur de la page au-delà de la fenêtre : 0 si rien ne défile horizontalement. */
async function horizontalOverflow(page: Page): Promise<number> {
  return page.evaluate(
    () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
  )
}

async function expectNoOverflowAtEveryWidth(page: Page) {
  for (const width of WIDTHS) {
    await page.setViewportSize({ width, height: 904 })
    await expect.poll(() => horizontalOverflow(page), { message: `${String(width)} px` }).toBe(0)
  }
}

test.describe('ADR 0024 : écrans étroits, aucun défilement horizontal de 280 à 1920 px', () => {
  for (const path of ['/connexion', '/inscription', '/mot-de-passe/oublie']) {
    test(`AUTH-10, AUTH-20, AUTH-70 : ${path}`, async ({ page }) => {
      await page.goto(path)
      await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
      await expectNoOverflowAtEveryWidth(page)
    })
  }

  test('AUTH-14 : les six cases du code tiennent sur un téléphone étroit', async ({ page }) => {
    const email = `mise-en-page.${Date.now().toString(36)}@departement.exemple.ca`
    await page.goto('/inscription')
    await page.getByLabel('Courriel', { exact: true }).fill(email)
    await expect(page.getByText('Disponible', { exact: true })).toBeVisible()
    await page.getByLabel('Nom affiché', { exact: true }).fill('Mise en page')
    await page.getByLabel('Mot de passe', { exact: true }).fill('fjord-tuque-raquette')
    await page.getByLabel('Confirmer le mot de passe', { exact: true }).fill('fjord-tuque-raquette')
    await page.getByRole('button', { name: 'Créer mon compte' }).click()
    await expect(page.getByRole('heading', { name: 'Vérifiez votre courriel' })).toBeVisible()

    await expectNoOverflowAtEveryWidth(page)
    // Aux largeurs de la maquette, les cases gardent leurs 48 px (Verification-Courriel).
    await page.setViewportSize({ width: 390, height: 844 })
    await expect(page.getByLabel('Code de vérification — 1/6')).toHaveCSS('width', '48px')
  })
})

test.describe('ADR 0024 : partage de l’écran sur ordinateur', () => {
  for (const width of DESKTOP_WIDTHS) {
    test(`AUTH-20 : à ${String(width)} px, panneau et formulaire prennent chacun la moitié`, async ({
      page,
    }) => {
      await page.setViewportSize({ width, height: 900 })
      await page.goto('/connexion')
      const panel = page.getByRole('complementary')
      const main = page.getByRole('main')
      const form = page.locator('form')
      await expect(form).toBeVisible()

      const panelBox = await panel.boundingBox()
      const mainBox = await main.boundingBox()
      const formBox = await form.boundingBox()
      // Bloc central du panneau : phoque, accroche et atouts ; le logo et le pied restent à gauche.
      const pitchBox = await panel.locator(':scope > div').boundingBox()
      if (panelBox === null || mainBox === null || formBox === null || pitchBox === null) {
        throw new Error('mise en page introuvable')
      }
      expect(Math.abs(panelBox.width - width / 2)).toBeLessThanOrEqual(1)
      expect(Math.abs(mainBox.width - width / 2)).toBeLessThanOrEqual(1)
      // Formulaire de 400 px, centré dans sa moitié.
      expect(formBox.width).toBe(400)
      const formCenter = formBox.x + formBox.width / 2
      expect(Math.abs(formCenter - (mainBox.x + mainBox.width / 2))).toBeLessThanOrEqual(1)
      // Bloc central du panneau, de 448 px au plus, centré dans sa moitié.
      expect(pitchBox.width).toBeLessThanOrEqual(448)
      const pitchCenter = pitchBox.x + pitchBox.width / 2
      expect(Math.abs(pitchCenter - (panelBox.x + panelBox.width / 2))).toBeLessThanOrEqual(1)
    })
  }
})
