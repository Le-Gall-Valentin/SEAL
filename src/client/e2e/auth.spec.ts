import { expect, type Page, test } from '@playwright/test'
import { mailMatch, registerAccount } from './support/account'

// Parcours du lot 1 contre SEAL lancé par docker compose ; les courriels sont lus dans Mailpit (compose.yaml).
const EMAIL = `bout-en-bout.${Date.now().toString(36)}@exemple.ca`
const PASSWORD = 'fjord-tuque-raquette'
const NEW_PASSWORD = 'raquette-sous-la-neige'
/** Clé de récupération notée à l'inscription (KEY-30), ressaisie sur chaque nouvel appareil (KEY-33). */
let recoveryKey = ''

/** KEY-33 : Restauration-Appareil ; la clé collée dans la première case se répartit dans les huit. */
async function restore(page: Page, key: string) {
  await expect(page.getByRole('heading', { name: 'Restaurer vos clés' })).toBeVisible()
  await page.getByLabel('Groupe 1 sur 8').fill(key)
  await page.getByRole('button', { name: 'Restaurer mes clés' }).click()
}

/**
 * KEY-33 : la restauration a abouti. « Se déconnecter » ne le prouve pas, Restauration-Appareil l'affiche aussi à la
 * même adresse : son titre doit disparaître, et l'écran Conversations s'afficher (ADR 0041).
 */
async function expectRestored(page: Page) {
  await expect(page.getByRole('heading', { name: 'Restaurer vos clés' })).toBeHidden()
  await expect(page.getByRole('heading', { level: 1, name: 'Conversations' })).toBeVisible()
  expect(new URL(page.url()).pathname).toBe('/')
}

async function login(page: Page, password: string) {
  await page.goto('/connexion')
  await page.getByLabel('Identifiant ou courriel').fill(EMAIL)
  await page.getByLabel('Mot de passe', { exact: true }).fill(password)
  await page.getByRole('button', { name: 'Se connecter' }).click()
}

test.describe.serial('AUTH-10 à AUTH-70 : parcours d’authentification du lot 1', () => {
  test('AUTH-10, AUTH-14, AUTH-53, KEY-02 : inscription, code reçu, clé de récupération, accueil, déconnexion', async ({
    page,
    request,
  }) => {
    recoveryKey = await registerAccount(page, request, {
      email: EMAIL,
      displayName: 'Bout en bout',
      password: PASSWORD,
    })
    // ADR 0041 : l'écran Conversations remplace l'accueil provisoire ; la déconnexion est dans Paramètres.
    await page.goto('/parametres')
    await page.getByRole('button', { name: 'Se déconnecter' }).click()
    await expect(page.getByRole('heading', { name: 'Connexion' })).toBeVisible()
  })

  test('AUTH-20, AUTH-21, KEY-33 : connexion refusée puis acceptée, restauration sur un nouvel appareil', async ({
    page,
  }) => {
    await login(page, `${PASSWORD}!`)
    await expect(page.getByRole('alert')).toHaveText('Identifiant ou mot de passe incorrect.')

    await page.getByLabel('Mot de passe', { exact: true }).fill(PASSWORD)
    await page.getByRole('button', { name: 'Se connecter' }).click()

    // KEY-33 : nouveau contexte, donc nouvel appareil ; une clé fausse est refusée, la bonne restaure le trousseau.
    await restore(page, '0000 1111 2222 3333 4444 5555 6666 7777')
    await expect(page.getByRole('alert')).toHaveText(
      'Cette clé de récupération ne correspond pas à votre coffre.',
    )
    await restore(page, recoveryKey)
    await expectRestored(page)
  })

  test('AUTH-70 : mot de passe oublié, lien reçu, nouveau mot de passe, connexion avec lui', async ({
    page,
    request,
  }) => {
    const since = Date.now() - 2_000
    await page.goto('/mot-de-passe/oublie')
    await page.getByLabel('Courriel', { exact: true }).fill(EMAIL)
    await page.getByRole('button', { name: 'Envoyer le lien' }).click()
    await expect(page.getByRole('status')).toContainText('Vérifiez votre boîte de réception')

    const link = await mailMatch(
      request,
      EMAIL,
      /(https:\/\/[^\s]+\/mot-de-passe\/nouveau#jeton=[A-Za-z0-9_-]{43})/,
      since,
    )
    await page.goto(link)
    // ADR 0022 : le jeton quitte l'adresse dès l'ouverture.
    // AUTH-04 : l'identifiant suggéré pour « bout-en-bout.… » est « bout.en.bout.… ».
    await expect(
      page.getByText(/^Pour @bout\.en\.bout\..*\. Ce lien expire dans \d+ minutes\.$/),
    ).toBeVisible()
    expect(page.url()).not.toContain('jeton')
    await page.getByLabel('Nouveau mot de passe', { exact: true }).fill(NEW_PASSWORD)
    await page.getByLabel('Confirmer le mot de passe', { exact: true }).fill(NEW_PASSWORD)
    await page.getByRole('button', { name: 'Enregistrer le mot de passe' }).click()
    await expect(page.getByRole('status')).toHaveText(
      'Mot de passe enregistré. Connectez-vous avec le nouveau.',
    )

    await login(page, NEW_PASSWORD)
    await restore(page, recoveryKey)
    await expectRestored(page)
  })
})
