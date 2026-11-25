import { type Browser, expect, type Page, test } from '@playwright/test'
import { mailMatch, registerAccount } from './support/account'

// Écran Paramètres contre SEAL lancé par docker compose (ACC-01 à ACC-03, ADR 0035). La ré-authentification exige
// qu'un facteur ait plus de 10 minutes : elle est éprouvée par les tests du client et du serveur, pas ici.
const RUN = Date.now().toString(36)
const EMAIL = `parametres.${RUN}@exemple.ca`
const NEW_EMAIL = `parametres.neuf.${RUN}@exemple.ca`
const PASSWORD = 'fjord-tuque-raquette'
const NEW_PASSWORD = 'raquette-sous-la-neige'
const PHONE_USERNAME = `telephone.${RUN}`

// ADR 0038 : la suite crée déjà 5 comptes, la limite d'inscription est de 5 par heure et par IP. Le parcours des amis
// réutilise donc les deux comptes de ce fichier : les tests partagent leurs pages, dans l'ordre.
test.describe.configure({ mode: 'serial' })

let desktop: Page
let phone: Page

async function newPage(browser: Browser, baseURL: string | undefined): Promise<Page> {
  const context = await browser.newContext(baseURL === undefined ? {} : { baseURL })
  return context.newPage()
}

test.beforeAll(async ({ browser }, testInfo) => {
  desktop = await newPage(browser, testInfo.project.use.baseURL)
  phone = await newPage(browser, testInfo.project.use.baseURL)
})

test.afterAll(async () => {
  await desktop.context().close()
  await phone.context().close()
})

test('ACC-03, ACC-02, ACC-01, AUTH-53 : nom affiché, courriel par code, mot de passe, puis reconnexion avec les nouveaux', async ({
  request,
}) => {
  const page = desktop
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
  await expect(page.getByRole('heading', { level: 1, name: 'Conversations' })).toBeVisible()
})

test('ACC-03, ADR 0035 : sur téléphone, la liste des sections puis la sous-page Compte, sans barre du bas', async ({
  request,
}) => {
  const page = phone
  await page.setViewportSize({ width: 390, height: 844 })
  await registerAccount(page, request, {
    email: `${PHONE_USERNAME}@exemple.ca`,
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

test('FRD-01, FRD-04, FRD-11, ADR 0038 : inviter, voir le compteur, accepter, retirer ; feuille du téléphone', async () => {
  // Le téléphone est déjà sur Amis, et son utilisateur vient d'agir (AUTH-52, ADR 0043 point 4).
  await phone.goto('/amis')
  await expect(phone.getByRole('heading', { name: 'Pas encore d’amis' })).toBeVisible()
  await phone.mouse.move(10, 10)

  // L'ordinateur invite le téléphone depuis l'aside.
  await desktop.goto('/amis')
  await expect(desktop.getByRole('heading', { name: 'Pas encore d’amis' })).toBeVisible()
  await desktop.getByLabel('Identifiant').fill(`@${PHONE_USERNAME.toUpperCase()}`)
  await desktop.getByRole('button', { name: 'Envoyer l’invitation' }).click()
  await expect(desktop.getByRole('status')).toContainText(
    `Invitation envoyée à @${PHONE_USERNAME}.`,
  )

  // FRD-04, TRN-10 : la pastille du téléphone se met à jour sans recharger la page, puis il accepte.
  await expect(phone.getByRole('link', { name: 'Amis, 1 invitation' })).toBeVisible()
  await phone.goto('/amis?onglet=invitations')
  await phone.getByRole('button', { name: 'Accepter — Léa Tremblay-Roy' }).click()
  await expect(phone.getByText('Aucune invitation reçue.')).toBeVisible()

  // L'ordinateur voit son ami, puis le retire après confirmation.
  await desktop.goto('/amis')
  await desktop.getByRole('button', { name: 'Plus d’actions — Essai Téléphone' }).click()
  await desktop.getByRole('menuitem', { name: 'Retirer des amis' }).click()
  await desktop.getByRole('button', { name: 'Retirer', exact: true }).click()
  await expect(desktop.getByRole('heading', { name: 'Pas encore d’amis' })).toBeVisible()

  // Feuille du téléphone : identifiant inexistant, sans défilement horizontal.
  await phone.goto('/amis')
  await phone.getByRole('button', { name: 'Ajouter un ami' }).first().click()
  const sheet = phone.getByRole('dialog', { name: 'Ajouter un ami' })
  await sheet.getByLabel('Identifiant').fill('personne.inconnue')
  await sheet.getByRole('button', { name: 'Envoyer l’invitation' }).click()
  await expect(sheet.getByRole('alert')).toContainText(
    'Aucun utilisateur ne porte l’identifiant @personne.inconnue.',
  )
  expect(await phone.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390)
})

test('CNV-01, CNV-03, GRP-03, ADR 0041 : directe depuis Amis, groupe, renommage, rechargement, départ', async () => {
  // Le parcours précédent a retiré l'amitié : les deux comptes redeviennent amis.
  await desktop.goto('/amis')
  await desktop.getByLabel('Identifiant').fill(PHONE_USERNAME)
  await desktop.getByRole('button', { name: 'Envoyer l’invitation' }).click()
  await expect(desktop.getByRole('status')).toContainText(
    `Invitation envoyée à @${PHONE_USERNAME}.`,
  )
  await phone.goto('/amis?onglet=invitations')
  await phone.getByRole('button', { name: 'Accepter — Léa Tremblay-Roy' }).click()
  await expect(phone.getByText('Aucune invitation reçue.')).toBeVisible()

  // CNV-01 : la directe s'ouvre depuis Amis, à l'adresse de la conversation.
  await desktop.goto('/amis')
  // ADR 0017 : le nom de l'ami est isolé (U+2068, U+2069) dans le libellé.
  await desktop.getByRole('button', { name: /^Envoyer un message — .Essai Téléphone.$/u }).click()
  await expect(desktop).toHaveURL(/\/\?conversation=[0-9a-f-]{36}$/u)
  await expect(desktop.getByRole('heading', { level: 2, name: 'Essai Téléphone' })).toBeVisible()

  // CNV-03, GRP-01 : un groupe à deux, depuis « Nouvelle conversation ».
  await desktop.getByRole('button', { name: 'Nouvelle conversation' }).first().click()
  const dialog = desktop.getByRole('dialog', { name: 'Nouvelle conversation' })
  await dialog.getByRole('radio', { name: 'Groupe' }).click()
  await dialog.getByLabel('Nom du groupe').fill('Raquette du dimanche')
  await dialog.getByRole('combobox', { name: 'Membres' }).fill('essai')
  await dialog.getByRole('option', { name: /Essai Téléphone/u }).click()
  await dialog.getByRole('button', { name: 'Créer le groupe (2)' }).click()
  await expect(
    desktop.getByRole('heading', { level: 2, name: 'Raquette du dimanche' }),
  ).toBeVisible()
  await expect(
    desktop.getByText(
      'Vous ne voyez que les messages envoyés depuis votre arrivée dans le groupe.',
    ),
  ).toBeVisible()

  // GRP-03 : l'administrateur renomme, puis la conversation se rouvre au rechargement (ADR 0041, point 1).
  const info = desktop.getByRole('complementary', { name: 'Infos du groupe' })
  await info.getByRole('button', { name: 'Renommer le groupe' }).click()
  const rename = desktop.getByRole('dialog', { name: 'Renommer le groupe' })
  await rename.getByLabel('Nom du groupe').fill('Raquette au fjord')
  await rename.getByRole('button', { name: 'Enregistrer' }).click()
  await expect(desktop.getByRole('heading', { level: 2, name: 'Raquette au fjord' })).toBeVisible()
  await desktop.reload()
  await expect(desktop.getByRole('heading', { level: 2, name: 'Raquette au fjord' })).toBeVisible()

  // GRP-03 : le membre ouvre le groupe sur téléphone, puis le quitte depuis le tiroir.
  await phone.goto('/')
  await phone.getByRole('link', { name: 'Raquette au fjord' }).click()
  await phone.getByRole('button', { name: 'Infos du groupe' }).click()
  await phone
    .getByRole('dialog', { name: 'Infos du groupe' })
    .getByRole('button', { name: 'Quitter le groupe' })
    .click()
  await phone.getByRole('alertdialog').getByRole('button', { name: 'Quitter le groupe' }).click()
  await expect(phone.getByRole('heading', { level: 1, name: 'Conversations' })).toBeVisible()
  await expect(phone.getByRole('link', { name: 'Raquette au fjord' })).toHaveCount(0)
  expect(await phone.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390)
})

test('CNV-04, ADR 0041 : en thème sombre, l’écran Conversations prend les jetons sombres de la maquette', async () => {
  await desktop.emulateMedia({ colorScheme: 'dark' })
  try {
    await desktop.goto('/')
    const title = desktop.getByRole('heading', { level: 1, name: 'Conversations' })
    await expect(title).toBeVisible()
    // tokens.css, thème sombre : --ink #e9eddf, --surface #151a10.
    await expect(title).toHaveCSS('color', 'rgb(233, 237, 223)')
    const surface = await title.evaluate((element) => {
      let node: Element | null = element
      while (node !== null && getComputedStyle(node).backgroundColor === 'rgba(0, 0, 0, 0)') {
        node = node.parentElement
      }
      return node === null ? null : getComputedStyle(node).backgroundColor
    })
    expect(surface).toBe('rgb(21, 26, 16)')
    await desktop.getByRole('link', { name: 'Raquette au fjord' }).click()
    await expect(desktop.getByRole('heading', { level: 2, name: 'Raquette au fjord' })).toHaveCSS(
      'color',
      'rgb(233, 237, 223)',
    )
  } finally {
    await desktop.emulateMedia({ colorScheme: 'light' })
  }
})
