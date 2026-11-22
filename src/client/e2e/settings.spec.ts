import { expect, test } from '@playwright/test'
import { mailMatch, registerAccount } from './support/account'

// Écran Paramètres contre SEAL lancé par docker compose (ACC-01 à ACC-03, ADR 0035). La ré-authentification exige
// qu'un facteur ait plus de 10 minutes : elle est éprouvée par les tests du client et du serveur, pas ici.
const RUN = Date.now().toString(36)
const EMAIL = `parametres.${RUN}@exemple.ca`
const NEW_EMAIL = `parametres.neuf.${RUN}@exemple.ca`
const PASSWORD = 'fjord-tuque-raquette'
const NEW_PASSWORD = 'raquette-sous-la-neige'

test('ACC-03, ACC-02, ACC-01, AUTH-53 : nom affiché, courriel par code, mot de passe, puis reconnexion avec les nouveaux', async ({
  page,
  request,
}) => {
  await registerAccount(page, request, {
    email: EMAIL,
    displayName: 'Paramètres Essai',
    password: PASSWORD,
  })

  await page
    .getByRole('navigation', { name: 'Navigation principale' })
    .getByRole('link', { name: 'Paramètres' })
    .click()
  await expect(page).toHaveURL(/\/parametres$/)
  await expect(page.getByRole('heading', { level: 2, name: 'Compte' })).toBeVisible()

  // ACC-03
  await page.getByLabel('Nom affiché', { exact: true }).fill('Léa Tremblay')
  await page.getByRole('button', { name: 'Enregistrer' }).click()
  await expect(page.getByText('Nom affiché enregistré.')).toBeVisible()
  await expect(page.getByRole('link', { name: 'Mon profil' })).toHaveText('LT')

  // ACC-02 : la session vient de s'ouvrir, son facteur a moins de 10 minutes.
  const since = Date.now() - 2_000
  await page.getByRole('button', { name: 'Changer de courriel' }).click()
  await page.getByLabel('Nouveau courriel', { exact: true }).fill(NEW_EMAIL)
  await page.getByRole('button', { name: 'Envoyer le code' }).click()
  await expect(page.getByRole('dialog', { name: 'Saisissez le code' })).toBeVisible()
  const code = await mailMatch(
    request,
    NEW_EMAIL,
    /code de vérification SEAL : ([0-9]{6})\./,
    since,
  )
  await page.getByLabel('Chiffre 1 sur 6', { exact: true }).fill(code)
  await page.getByRole('button', { name: 'Confirmer' }).click()
  await expect(page.getByRole('dialog')).toBeHidden()
  await expect(page.getByText(NEW_EMAIL, { exact: true })).toBeVisible()
  await expect(page.getByText('Courriel changé.')).toBeVisible()
  await mailMatch(request, EMAIL, /(Votre courriel SEAL a été changé|est désormais)/, since)

  // ACC-01
  await page.getByRole('button', { name: 'Changer le mot de passe' }).click()
  const dialog = page.getByRole('dialog', { name: 'Changer le mot de passe' })
  await dialog.getByLabel('Mot de passe actuel', { exact: true }).fill(PASSWORD)
  await dialog.getByLabel('Nouveau mot de passe', { exact: true }).fill(NEW_PASSWORD)
  await dialog.getByLabel('Confirmer le mot de passe', { exact: true }).fill(NEW_PASSWORD)
  await dialog.getByRole('button', { name: 'Changer le mot de passe' }).click()
  await expect(dialog).toBeHidden()
  await expect(page.getByText('Mot de passe changé.')).toBeVisible()

  // La session gardée sous un nouvel identifiant sert encore : une requête non sûre passe.
  await page.getByLabel('Nom affiché', { exact: true }).fill('Léa Tremblay-Roy')
  await page.getByRole('button', { name: 'Enregistrer' }).click()
  await expect(page.getByText('Nom affiché enregistré.')).toBeVisible()

  // AUTH-53, puis connexion par la nouvelle adresse et le nouveau mot de passe, sur le même appareil.
  await page
    .getByRole('navigation', { name: 'Sections des paramètres' })
    .getByRole('button', { name: 'Se déconnecter' })
    .click()
  await expect(page.getByRole('heading', { name: 'Connexion' })).toBeVisible()
  await page.getByLabel('Identifiant ou courriel').fill(NEW_EMAIL)
  await page.getByLabel('Mot de passe', { exact: true }).fill(NEW_PASSWORD)
  await page.getByRole('button', { name: 'Se connecter' }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'seal' })).toBeVisible()
})

test('ADR 0035 : sur téléphone, la liste des sections puis la sous-page Compte, sans barre du bas', async ({
  page,
  request,
}) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await registerAccount(page, request, {
    email: `telephone.${RUN}@exemple.ca`,
    displayName: 'Essai Téléphone',
    password: PASSWORD,
  })

  await page
    .getByRole('navigation', { name: 'Navigation principale' })
    .getByRole('link', { name: 'Paramètres' })
    .click()
  await expect(page.getByRole('heading', { level: 1, name: 'Paramètres' })).toBeVisible()
  await page
    .getByRole('navigation', { name: 'Sections des paramètres' })
    .getByRole('link', { name: /Compte/ })
    .click()
  await expect(page.getByRole('heading', { level: 1, name: 'Compte' })).toBeVisible()
  await expect(page.getByRole('navigation', { name: 'Navigation principale' })).toBeHidden()
  // Aucun défilement horizontal (ADR 0024, point 11).
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390)
  await page.getByRole('link', { name: 'Paramètres' }).click()
  await expect(page.getByRole('navigation', { name: 'Navigation principale' })).toBeVisible()
})
